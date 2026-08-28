package com.nitronbox.app.domain.chat

import com.nitronbox.app.data.attachments.AttachmentPipeline
import com.nitronbox.app.data.attachments.PdfTextExtractor
import com.nitronbox.app.data.local.ChatRepository
import com.nitronbox.app.data.model.AttachmentKind
import com.nitronbox.app.data.model.AttachmentReference
import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.ConversationSummary
import com.nitronbox.app.data.model.MessageRole
import com.nitronbox.app.data.model.MessageStatus
import com.nitronbox.app.data.model.ModelRoute
import com.nitronbox.app.data.model.ModelTarget
import com.nitronbox.app.data.model.Workspace
import com.nitronbox.app.data.remote.AiProviderBridge
import com.nitronbox.app.data.remote.ChatCompletionRequest
import com.nitronbox.app.data.remote.FailoverRouter
import com.nitronbox.app.data.remote.InlineImage
import com.nitronbox.app.data.remote.ProviderRegistry
import com.nitronbox.app.domain.context.ContextWindowEngine
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/** Orchestrates a full turn: persist user input, assemble context, stream, persist the answer. */
class ChatService(
    private val repository: ChatRepository,
    private val contextWindowEngine: ContextWindowEngine,
    private val attachmentPipeline: AttachmentPipeline,
    private val pdfTextExtractor: PdfTextExtractor,
) {
    sealed interface TurnOutcome {
        /** The assistant reply was persisted with status COMPLETE. */
        data object Completed : TurnOutcome

        /** The caller cancelled; the partial reply is persisted as CANCELLED. */
        data object Cancelled : TurnOutcome

        /** Generation failed; the failure is persisted on the assistant message. */
        data class Failed(val errorText: String) : TurnOutcome
    }

    data class PreparedUserMessage(val conversationId: String, val message: ChatMessage)

    /**
     * Persists the user turn. Creates the conversation lazily so an empty chat screen does not
     * create rows until the user actually sends something. Text-like attachments are inlined into
     * the outgoing content; images stay as references and are resolved as inline image payloads.
     */
    suspend fun prepareUserMessage(
        workspace: Workspace,
        conversationId: String?,
        text: String,
        attachments: List<AttachmentReference>,
    ): PreparedUserMessage {
        val resolvedConversationId = conversationId
            ?: repository.createConversation(workspace.id, deriveTitle(text)).id
        val documentText = withContext(Dispatchers.IO) { inlineDocumentText(attachments) }
        val content = if (documentText.isBlank()) {
            text
        } else {
            text + "\n\n" + documentText
        }
        val message = ChatMessage(
            conversationId = resolvedConversationId,
            role = MessageRole.USER,
            content = content,
            attachments = attachments,
        )
        repository.saveMessage(message)
        repository.touchConversation(resolvedConversationId)
        return PreparedUserMessage(resolvedConversationId, message)
    }

    /**
     * Streams one assistant reply for [conversationId] and persists it, throttling intermediate
     * writes. Cancellation keeps the partial text; provider failures are stored on the message.
     */
    suspend fun generateReply(
        conversationId: String,
        workspace: Workspace,
        registry: ProviderRegistry,
        target: ModelTarget,
        route: ModelRoute = workspace.route.copy(primary = target),
    ): TurnOutcome {
        val history = repository.contextMessages(conversationId)
            .filter { it.status != MessageStatus.FAILED && it.status != MessageStatus.QUEUED }
        val conversation = repository.conversation(conversationId)
        val existingSummary = conversation?.let {
            ConversationSummary(
                conversationId = it.id,
                content = it.summary ?: return@let null,
                throughMessageEpochMillis = it.summaryThroughEpochMillis ?: 0L,
                estimatedTokens = 0,
            )
        }
        val assembly = contextWindowEngine.assemble(
            conversationId = conversationId,
            systemPrompt = workspace.systemPrompt,
            history = history,
            policy = workspace.contextPolicy,
            existingSummary = existingSummary,
        )
        assembly.generatedSummary?.let {
            repository.persistSummary(conversationId, it.content, it.throughMessageEpochMillis)
        }

        val assistant = ChatMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
        )
        repository.saveMessage(assistant)
        repository.touchConversation(conversationId)

        val images = resolveInlineImages(history.lastOrNull { it.role == MessageRole.USER })
        val request = ChatCompletionRequest(
            modelId = target.modelId,
            messages = assembly.messages,
            generation = workspace.generation,
            requestId = UUID.randomUUID().toString(),
            imagesByMessageId = images,
        )

        val content = StringBuilder()
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        var providerId: String? = null
        var modelId: String? = null
        var lastPersistAt = 0L

        suspend fun persist(status: MessageStatus, errorText: String? = null) {
            repository.updateMessage(
                assistant.copy(
                    content = content.toString(),
                    status = status,
                    providerId = providerId,
                    modelId = modelId,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    errorText = errorText,
                ),
            )
        }

        return try {
            FailoverRouter(registry).stream(route, request).collect { event ->
                when (event) {
                    is com.nitronbox.app.data.remote.StreamEvent.Started -> {
                        if (providerId == null) {
                            providerId = event.providerId
                            modelId = event.modelId
                        }
                    }
                    is com.nitronbox.app.data.remote.StreamEvent.TextDelta -> {
                        content.append(event.text)
                        val now = System.currentTimeMillis()
                        if (now - lastPersistAt > PERSIST_INTERVAL_MILLIS) {
                            lastPersistAt = now
                            persist(MessageStatus.STREAMING)
                        }
                    }
                    is com.nitronbox.app.data.remote.StreamEvent.ReasoningDelta -> Unit
                    is com.nitronbox.app.data.remote.StreamEvent.ToolCallDelta -> Unit
                    is com.nitronbox.app.data.remote.StreamEvent.Usage -> {
                        inputTokens = event.inputTokens ?: inputTokens
                        outputTokens = event.outputTokens ?: outputTokens
                    }
                    is com.nitronbox.app.data.remote.StreamEvent.Completed -> Unit
                }
            }
            if (content.isBlank()) {
                persist(MessageStatus.FAILED, "The model returned an empty response.")
                TurnOutcome.Failed("The model returned an empty response.")
            } else {
                persist(MessageStatus.COMPLETE)
                TurnOutcome.Completed
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                persist(MessageStatus.CANCELLED)
            }
            TurnOutcome.Cancelled
        } catch (failure: Throwable) {
            val detail = failure.message?.take(MAX_ERROR_CHARS) ?: failure::class.java.simpleName
            withContext(NonCancellable + Dispatchers.IO) {
                persist(MessageStatus.FAILED, detail)
            }
            TurnOutcome.Failed(detail)
        }
    }

    /**
     * Deletes a message. Returns its conversation id when an assistant reply was removed and can
     * be regenerated, or null for user messages (deletion only).
     */
    suspend fun deleteForRetry(messageId: String): String? {
        val message = repository.message(messageId) ?: return null
        repository.deleteMessage(messageId)
        return message.conversationId.takeIf { message.role == MessageRole.ASSISTANT }
    }

    private suspend fun resolveInlineImages(
        message: ChatMessage?,
    ): Map<String, List<InlineImage>> = withContext(Dispatchers.IO) {
        if (message == null) return@withContext emptyMap()
        val images = message.attachments
            .filter { it.kind == AttachmentKind.IMAGE }
            .take(MAX_INLINE_IMAGES)
            .mapNotNull { attachment ->
                runCatching {
                    InlineImage(
                        mediaType = attachment.mimeType,
                        base64Data = attachmentPipeline.base64(attachment),
                    )
                }.getOrNull()
            }
        if (images.isEmpty()) emptyMap() else mapOf(message.id to images)
    }

    private suspend fun inlineDocumentText(attachments: List<AttachmentReference>): String {
        if (attachments.isEmpty()) return ""
        val sections = attachments.mapNotNull { attachment ->
            val text = when (attachment.kind) {
                AttachmentKind.TEXT, AttachmentKind.CSV, AttachmentKind.JSON ->
                    attachmentPipeline.textContent(attachment)
                AttachmentKind.PDF -> pdfTextExtractor.extract(attachment)
                AttachmentKind.IMAGE -> null
            }?.take(ATTACHMENT_TEXT_LIMIT)?.trim()
            if (text.isNullOrEmpty()) return@mapNotNull null
            "[Attached file: ${attachment.displayName}]\n$text"
        }
        return sections.joinToString("\n\n")
    }

    private fun deriveTitle(text: String): String = text
        .lineSequence().firstOrNull().orEmpty()
        .trim()
        .take(ChatRepository.TITLE_MAX_LENGTH)
        .ifBlank { "New conversation" }

    private companion object {
        const val PERSIST_INTERVAL_MILLIS = 300L
        const val MAX_ERROR_CHARS = 2_000
        const val ATTACHMENT_TEXT_LIMIT = 60_000
        const val MAX_INLINE_IMAGES = 4
    }
}

/**
 * Streams a compact summary through the currently selected provider so the SUMMARIZE context
 * policy works end to end. The bridge is resolved lazily per call because providers are dynamic.
 */
class LlmConversationSummarizer(
    private val resolveBridge: suspend () -> AiProviderBridge,
    private val resolveTarget: suspend () -> ModelTarget,
) : com.nitronbox.app.domain.context.ConversationSummarizer {
    override suspend fun summarize(
        messages: List<ChatMessage>,
        instruction: String,
    ): ConversationSummary {
        val bridge = resolveBridge()
        val target = resolveTarget()
        val transcript = messages.joinToString("\n\n") { "${it.role.name.lowercase()}: ${it.content}" }
        val request = ChatCompletionRequest(
            modelId = target.modelId,
            messages = listOf(
                ChatMessage(
                    conversationId = "summary",
                    role = MessageRole.SYSTEM,
                    content = instruction,
                ),
                ChatMessage(
                    conversationId = "summary",
                    role = MessageRole.USER,
                    content = transcript.take(TRANSCRIPT_LIMIT),
                ),
            ),
            generation = com.nitronbox.app.data.model.GenerationSettings(
                temperature = 0.2f,
                maxOutputTokens = 512,
            ),
            requestId = UUID.randomUUID().toString(),
        )
        val summary = StringBuilder()
        bridge.streamChat(request).collect { event ->
            if (event is com.nitronbox.app.data.remote.StreamEvent.TextDelta) summary.append(event.text)
        }
        return ConversationSummary(
            conversationId = messages.lastOrNull()?.conversationId ?: "summary",
            content = summary.toString().ifBlank { throw IllegalStateException("Summarizer returned no content") },
            throughMessageEpochMillis = messages.lastOrNull()?.createdAtEpochMillis ?: System.currentTimeMillis(),
            estimatedTokens = 0,
        )
    }

    private companion object { const val TRANSCRIPT_LIMIT = 40_000 }
}

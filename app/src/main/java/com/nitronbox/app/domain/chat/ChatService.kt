package com.nitronbox.app.domain.chat

import com.nitronbox.app.data.attachments.AttachmentPipeline
import com.nitronbox.app.data.attachments.PdfTextExtractor
import com.nitronbox.app.data.filesystem.CreatorFileTools
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
import com.nitronbox.app.domain.context.ContextAssembly
import com.nitronbox.app.domain.context.ContextWindowEngine
import androidx.documentfile.provider.DocumentFile
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
        // A freshly created empty conversation gets a real title from its first message.
        if (text.isNotBlank()) {
            repository.conversation(resolvedConversationId)?.let { conversation ->
                if (conversation.title == ChatRepository.DEFAULT_TITLE) {
                    repository.renameConversation(resolvedConversationId, deriveTitle(text))
                }
            }
        }
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
     *
     * When [creatorTools] is provided the model may act on the project folder by emitting
     * fenced `tool {...}` blocks; each block is executed, its result appended to the context as
     * a system note, and a follow-up request is made — up to [MAX_TOOL_ITERATIONS] rounds.
     */
    suspend fun generateReply(
        conversationId: String,
        workspace: Workspace,
        registry: ProviderRegistry,
        target: ModelTarget,
        route: ModelRoute = workspace.route.copy(primary = target),
        creatorTools: CreatorFileTools? = null,
        creatorRootUri: String? = null,
    ): TurnOutcome {
        val root = creatorRootUri?.let { creatorTools?.rootFor(it) }
        var iteration = 0
        var outcome: TurnOutcome = TurnOutcome.Completed
        while (true) {
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
                systemPrompt = creatorTools?.let {
                    workspace.systemPrompt + "\n\n" + creatorSystemPrompt(it, root)
                } ?: workspace.systemPrompt,
                history = history,
                policy = workspace.contextPolicy,
                existingSummary = existingSummary,
            )
            assembly.generatedSummary?.let {
                repository.persistSummary(conversationId, it.content, it.throughMessageEpochMillis)
            }

            outcome = streamOnce(
                conversationId = conversationId,
                assembly = assembly,
                workspace = workspace,
                registry = registry,
                target = target,
                route = route,
                images = resolveInlineImages(history.lastOrNull { it.role == MessageRole.USER }),
            )

            val blocks = if (outcome is TurnOutcome.Completed && root != null) {
                TOOL_BLOCK.findAll(assistantContent ?: "").toList()
            } else emptyList()
            if (blocks.isEmpty() || root == null || iteration >= MAX_TOOL_ITERATIONS) break

            // Persist tool execution results and let the model continue.
            val results = blocks.mapIndexedNotNull { index, match ->
                val result = creatorTools?.executeTool(root, match.groupValues[1])
                if (result == null) null else "[$index] $result"
            }
            if (results.isEmpty()) break
            repository.saveMessage(
                ChatMessage(
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    content = "[tool results]\n" + results.joinToString("\n\n"),
                ),
            )
            repository.touchConversation(conversationId)
            iteration++
        }
        return outcome
    }

    private var assistantContent: String? = null

    private suspend fun streamOnce(
        conversationId: String,
        assembly: ContextAssembly,
        workspace: Workspace,
        registry: ProviderRegistry,
        target: ModelTarget,
        route: ModelRoute,
        images: Map<String, List<InlineImage>>,
    ): TurnOutcome {
        val assistant = ChatMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
        )
        repository.saveMessage(assistant)
        repository.touchConversation(conversationId)

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
        var generationMillis: Long? = null
        var lastPersistAt = 0L
        val startedAt = System.currentTimeMillis()

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
                    generationDurationMillis = generationMillis ?: (System.currentTimeMillis() - startedAt),
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
                generationMillis = System.currentTimeMillis() - startedAt
                assistantContent = content.toString()
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
                AttachmentKind.TEXT, AttachmentKind.CSV, AttachmentKind.JSON, AttachmentKind.CODE ->
                    attachmentPipeline.textContent(attachment)
                AttachmentKind.PDF -> pdfTextExtractor.extract(attachment)
                AttachmentKind.IMAGE, AttachmentKind.VIDEO, AttachmentKind.AUDIO,
                AttachmentKind.ARCHIVE, AttachmentKind.FILE,
                -> null
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
        const val MAX_TOOL_ITERATIONS = 4

        /** Fenced tool block: ```tool {"action":"...","path":"..."} ``` (nitron:tool also accepted). */
        val TOOL_BLOCK = Regex("```(?:nitron:)?tool\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
    }
}

/**
 * System prompt appended for Creator chats. The folder tree is injected live so the model sees
 * the real project structure; nothing about the environment is hardcoded.
 */
private suspend fun creatorSystemPrompt(tools: CreatorFileTools, root: DocumentFile?): String {
    val tree = root?.let { tools.list(it) } ?: "(folder unavailable)"
    return """
You are in Creator mode with direct access to the user's project folder.

Current folder tree:
$tree

You can run file operations by emitting fenced tool blocks in your reply:
```tool {"action":"list","path":"."}```

Actions: list (path optional — omit for the whole tree), read, write (creates or overwrites; content required), create_folder, delete, rename (new_name required).
Rules: paths are relative to the project root and use forward slashes. Emit one block per operation, several blocks are allowed. After your reply the environment appends a [tool results] message — continue your work from there. When the task is finished, answer normally without tool blocks.
""".trim()
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

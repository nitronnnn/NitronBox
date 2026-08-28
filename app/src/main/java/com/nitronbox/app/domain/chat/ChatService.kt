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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        // The bubble shows only the user text + attachment chips; document contents are
        // injected into the request transcript at generation time, never displayed here.
        val message = ChatMessage(
            conversationId = resolvedConversationId,
            role = MessageRole.USER,
            content = text,
            attachments = attachments,
        )
        repository.saveMessage(message)
        repository.touchConversation(resolvedConversationId)
        return PreparedUserMessage(resolvedConversationId, message)
    }

    /**
     * Streams one assistant reply for [conversationId] and persists it. The whole turn —
     * including Creator tool rounds — lives in a single assistant message: tool blocks stay
     * attached to the final answer and tool results are fed back to the model invisibly
     * (they exist only in the in-memory transcript, never as separate chat bubbles).
     */
    suspend fun generateReply(
        conversationId: String,
        workspace: Workspace,
        registry: ProviderRegistry,
        target: ModelTarget,
        route: ModelRoute = workspace.route.copy(primary = target),
        creatorTools: CreatorFileTools? = null,
        creatorRootUri: String? = null,
        skills: List<com.nitronbox.app.data.settings.Skill> = emptyList(),
    ): TurnOutcome {
        val root = creatorRootUri?.let { creatorTools?.rootFor(it) }
        val systemPrompt = buildString {
            append(workspace.systemPrompt)
            skills.filter { it.enabled }.ifEmpty { null }?.let { enabled ->
                append("\n\n").append(enabled.joinToString("\n\n") { "# Skill: ${it.name}\n${it.prompt}" })
            }
            if (root != null && creatorTools != null) {
                append("\n\n").append(creatorSystemPrompt(creatorTools, root))
            }
        }

        val history = repository.contextMessages(conversationId)
            .filter { it.status != MessageStatus.FAILED && it.status != MessageStatus.QUEUED }
            .map { enrichWithAttachments(it) }
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
            systemPrompt = systemPrompt,
            history = history,
            policy = workspace.contextPolicy,
            existingSummary = existingSummary,
        )
        assembly.generatedSummary?.let {
            repository.persistSummary(conversationId, it.content, it.throughMessageEpochMillis)
        }

        // One assistant row for the entire turn.
        val assistant = ChatMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
        )
        repository.saveMessage(assistant)
        repository.touchConversation(conversationId)

        val transcript = assembly.messages.toMutableList()
        val images = resolveInlineImages(history.lastOrNull { it.role == MessageRole.USER })

        val content = StringBuilder()
        val reasoning = StringBuilder()
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        var providerId: String? = null
        var modelId: String? = null
        val startedAt = System.currentTimeMillis()
        var generationMillis: Long? = null
        var lastPersistAt = 0L

        suspend fun persist(status: MessageStatus, errorText: String? = null) {
            repository.updateMessage(
                assistant.copy(
                    content = content.toString(),
                    reasoning = reasoning.toString().takeIf(String::isNotBlank),
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

        try {
            var iteration = 0
            while (true) {
                val roundContent = StringBuilder()
                val request = ChatCompletionRequest(
                    modelId = target.modelId,
                    messages = transcript.toList(),
                    generation = workspace.generation,
                    requestId = UUID.randomUUID().toString(),
                    imagesByMessageId = images,
                )
                FailoverRouter(registry).stream(route, request).collect { event ->
                    when (event) {
                        is com.nitronbox.app.data.remote.StreamEvent.Started -> {
                            if (providerId == null) {
                                providerId = event.providerId
                                modelId = event.modelId
                            }
                        }
                        is com.nitronbox.app.data.remote.StreamEvent.TextDelta -> {
                            roundContent.append(event.text)
                            val now = System.currentTimeMillis()
                            if (now - lastPersistAt > PERSIST_INTERVAL_MILLIS) {
                                lastPersistAt = now
                                persist(MessageStatus.STREAMING)
                            }
                        }
                        is com.nitronbox.app.data.remote.StreamEvent.ReasoningDelta -> {
                            reasoning.append(event.text)
                            val now = System.currentTimeMillis()
                            if (now - lastPersistAt > PERSIST_INTERVAL_MILLIS) {
                                lastPersistAt = now
                                persist(MessageStatus.STREAMING)
                            }
                        }
                        is com.nitronbox.app.data.remote.StreamEvent.ToolCallDelta -> Unit
                        is com.nitronbox.app.data.remote.StreamEvent.Usage -> {
                            inputTokens = event.inputTokens ?: inputTokens
                            outputTokens = event.outputTokens ?: outputTokens
                        }
                        is com.nitronbox.app.data.remote.StreamEvent.Completed -> Unit
                    }
                }

                if (roundContent.isBlank()) {
                    persist(MessageStatus.FAILED, "The model returned an empty response.")
                    return TurnOutcome.Failed("The model returned an empty response.")
                }
                content.append(withToolMarkers(roundContent.toString()))

                val blocks = if (root != null) {
                    extractToolBlocks(roundContent.toString())
                } else emptyList()
                if (blocks.isEmpty() || iteration >= MAX_TOOL_ITERATIONS) {
                    generationMillis = System.currentTimeMillis() - startedAt
                    persist(MessageStatus.COMPLETE)
                    return TurnOutcome.Completed
                }

                // Execute tools; results are visible to the model through the transcript only.
                // Parse failures are fed back so the model can correct its format.
                val results = blocks.mapIndexed { index, match ->
                    val executed = root?.let { r ->
                        creatorTools?.executeTool(r, match)
                    } ?: "[tool error] no project folder is available"
                    "[$index] " + (executed ?: "[tool error] could not parse this block; emit one-line JSON with action/path/content")
                }
                transcript.add(
                    ChatMessage(
                        conversationId = conversationId,
                        role = MessageRole.ASSISTANT,
                        content = roundContent.toString(),
                    ),
                )
                if (results.isEmpty()) {
                    generationMillis = System.currentTimeMillis() - startedAt
                    persist(MessageStatus.COMPLETE)
                    return TurnOutcome.Completed
                }
                transcript.add(
                    ChatMessage(
                        conversationId = conversationId,
                        role = MessageRole.USER,
                        content = "[tool results]\n" + results.joinToString("\n\n"),
                    ),
                )
                repository.touchConversation(conversationId)
                iteration++
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                persist(MessageStatus.CANCELLED)
            }
            return TurnOutcome.Cancelled
        } catch (failure: Throwable) {
            val detail = failure.message?.take(MAX_ERROR_CHARS) ?: failure::class.java.simpleName
            withContext(NonCancellable + Dispatchers.IO) {
                persist(MessageStatus.FAILED, detail)
            }
            return TurnOutcome.Failed(detail)
        }
    }

    /**
     * Chat display version of a round: every completed fenced tool block collapses into a
     * compact marker line (rendered as a chip by the UI); raw JSON never reaches the chat.
     * An unterminated trailing block (still streaming) is hidden until its fence closes.
     */
    private fun withToolMarkers(text: String): String {
        val hasTools = text.contains("```tool") || text.contains("```nitron:tool")
        if (!hasTools) return text
        val sb = StringBuilder()
        var cursor = 0
        var lastComplete = 0
        while (true) {
            val toolIdx = text.indexOf("```tool", cursor).let { t ->
                val nitron = text.indexOf("```nitron:tool", cursor)
                if (nitron != -1 && (t == -1 || nitron < t)) nitron else t
            }
            if (toolIdx == -1) {
                sb.append(text.substring(cursor))
                lastComplete = text.length
                break
            }
            val jsonStart = text.indexOf('{', toolIdx)
            if (jsonStart == -1) {
                sb.append(text.substring(cursor, toolIdx))
                break
            }
            var depth = 0
            var inString = false
            var escaped = false
            var jsonEnd = -1
            var scan = jsonStart
            while (scan < text.length) {
                val ch = text[scan]
                if (escaped) {
                    escaped = false
                } else {
                    when {
                        ch == '\\' && inString -> escaped = true
                        ch == '"' -> inString = !inString
                        !inString && ch == '{' -> depth++
                        !inString && ch == '}' -> {
                            depth--
                            if (depth == 0) {
                                jsonEnd = scan
                                break
                            }
                        }
                    }
                }
                scan++
            }
            if (jsonEnd == -1) {
                // Half-streamed block: cut the tail so raw JSON never flashes in the chat.
                sb.append(text.substring(cursor, toolIdx))
                lastComplete = toolIdx
                break
            }
            sb.append(text.substring(cursor, toolIdx))
            val json = text.substring(jsonStart, jsonEnd + 1)
            val parsed = runCatching {
                val obj = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .parseToJsonElement(json).jsonObject
                val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: "tool"
                val path = obj["path"]?.jsonPrimitive?.contentOrNull
                action + (path?.let { " \u00b7 $it" } ?: "")
            }.getOrDefault("tool")
            sb.append("\u27e6tool\u27e7 ").append(parsed).append('\n')
            cursor = closingFence(text, jsonEnd + 1).let { if (it == -1) text.length else it + 3 }
            lastComplete = cursor
        }
        var result = sb.toString()
        if (lastComplete < text.length) result = result.take(lastComplete.coerceAtMost(result.length))
        return result.trimEnd('\n')
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


    /**
     * Model-side enrichment: text-like and PDF attachments get their contents appended to the
     * request transcript. Displayed messages stay clean — only chips, never the raw file text.
     */
    private suspend fun enrichWithAttachments(message: ChatMessage): ChatMessage {
        if (message.role != MessageRole.USER || message.attachments.isEmpty()) return message
        if (message.content.contains("[Attached file:")) return message
        val documentText = withContext(Dispatchers.IO) { inlineDocumentText(message.attachments) }
        if (documentText.isBlank()) return message
        return message.copy(content = message.content + "\n\n" + documentText)
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


    /**
     * Extracts fenced `tool {...}` blocks with a brace-balanced scanner. The JSON may span
     * lines and contain nested objects/strings with braces — a lazy regex breaks on those.
     */
    private fun extractToolBlocks(text: String): List<String> {
        val calls = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val toolIdx = text.indexOf("```tool", searchFrom).let { t ->
                val nitron = text.indexOf("```nitron:tool", searchFrom)
                if (nitron != -1 && (t == -1 || nitron < t)) nitron else t
            }
            if (toolIdx == -1) break
            val jsonStart = text.indexOf('{', toolIdx)
            if (jsonStart == -1) break
            var depth = 0
            var inString = false
            var escaped = false
            var jsonEnd = -1
            var cursor = jsonStart
            while (cursor < text.length) {
                val ch = text[cursor]
                if (escaped) {
                    escaped = false
                } else {
                    when {
                        ch == '\\' && inString -> escaped = true
                        ch == '"' -> inString = !inString
                        !inString && ch == '{' -> depth++
                        !inString && ch == '}' -> {
                            depth--
                            if (depth == 0) {
                                jsonEnd = cursor
                                break
                            }
                        }
                    }
                }
                cursor++
            }
            if (jsonEnd == -1) break
            calls.add(text.substring(jsonStart, jsonEnd + 1))
            searchFrom = closingFence(text, jsonEnd + 1).let { if (it == -1) jsonEnd + 1 else it + 3 }
        }
        return calls
    }

    private companion object {
        const val PERSIST_INTERVAL_MILLIS = 300L
        const val MAX_ERROR_CHARS = 2_000
        const val ATTACHMENT_TEXT_LIMIT = 60_000
        const val MAX_INLINE_IMAGES = 4
        const val MAX_TOOL_ITERATIONS = 4

        /** Finds the end index of the closing fence after [from], or -1. */
        fun closingFence(text: String, from: Int): Int = text.indexOf("```", from)
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

package com.nitronbox.app.domain.context

import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.ContextOverflowStrategy
import com.nitronbox.app.data.model.ContextPolicy
import com.nitronbox.app.data.model.ConversationSummary
import com.nitronbox.app.data.model.MessageRole
import java.util.UUID
import kotlin.math.ceil

fun interface TokenEstimator {
    fun estimate(text: String): Int
}

/** Conservative multilingual fallback. Replace with a model tokenizer through this interface. */
class CharacterTokenEstimator : TokenEstimator {
    override fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        val ascii = text.count { it.code < 128 }
        val nonAscii = text.length - ascii
        return ceil(ascii / 3.8 + nonAscii / 1.8).toInt() + MESSAGE_OVERHEAD
    }

    private companion object { const val MESSAGE_OVERHEAD = 4 }
}

fun interface ConversationSummarizer {
    suspend fun summarize(messages: List<ChatMessage>, instruction: String): ConversationSummary
}

data class ContextAssembly(
    val messages: List<ChatMessage>,
    val estimatedInputTokens: Int,
    val omittedMessageCount: Int,
    val generatedSummary: ConversationSummary? = null,
)

class ContextWindowExceededException(val estimatedTokens: Int, val allowedTokens: Int) :
    IllegalStateException("Context needs $estimatedTokens tokens but only $allowedTokens are available")

class ContextWindowEngine(
    private val tokenEstimator: TokenEstimator,
    private val summarizer: ConversationSummarizer? = null,
) {
    suspend fun assemble(
        conversationId: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        policy: ContextPolicy,
        existingSummary: ConversationSummary? = null,
    ): ContextAssembly {
        val budget = policy.maxInputTokens - policy.reservedOutputTokens
        require(budget > 0) { "Context budget must be positive" }
        val systemMessage = ChatMessage(
            id = "system-$conversationId",
            conversationId = conversationId,
            role = MessageRole.SYSTEM,
            content = systemPrompt,
        )
        val systemTokens = estimate(systemMessage)
        if (systemTokens > budget) {
            throw ContextWindowExceededException(systemTokens, budget)
        }
        val fullMessages = listOf(systemMessage) + history
        val fullTokens = fullMessages.sumOf(::estimate)
        if (fullTokens <= budget) return ContextAssembly(fullMessages, fullTokens, 0)

        if (policy.strategy == ContextOverflowStrategy.REJECT) {
            throw ContextWindowExceededException(fullTokens, budget)
        }

        val preservedCount = policy.preserveRecentMessages.coerceAtLeast(1)
        val recent = history.takeLast(preservedCount)
        val old = history.dropLast(recent.size)
        val generatedSummary = if (policy.strategy == ContextOverflowStrategy.SUMMARIZE && old.isNotEmpty()) {
            summarizer?.summarize(old, policy.summaryPrompt)
        } else null
        val summary = generatedSummary ?: existingSummary
        val candidateSummaryMessage = summary?.let {
            ChatMessage(
                id = "summary-${UUID.randomUUID()}",
                conversationId = conversationId,
                role = MessageRole.SYSTEM,
                content = "Conversation summary:\n${it.content}",
                createdAtEpochMillis = it.throughMessageEpochMillis,
            )
        }
        val summaryMessage = candidateSummaryMessage?.takeIf {
            estimate(systemMessage) + estimate(it) <= budget
        }

        val selected = ArrayDeque<ChatMessage>()
        var used = estimate(systemMessage) + (summaryMessage?.let(::estimate) ?: 0)
        for (message in recent.asReversed()) {
            val tokens = estimate(message)
            if (used + tokens > budget) continue
            selected.addFirst(message)
            used += tokens
        }

        // Fill remaining space with the newest messages that preceded the protected tail.
        for (message in old.asReversed()) {
            val tokens = estimate(message)
            if (used + tokens > budget) break
            selected.addFirst(message)
            used += tokens
        }
        val result = buildList {
            add(systemMessage)
            summaryMessage?.let(::add)
            addAll(selected)
        }
        return ContextAssembly(
            messages = result,
            estimatedInputTokens = result.sumOf(::estimate),
            omittedMessageCount = (history.size - selected.size).coerceAtLeast(0),
            generatedSummary = generatedSummary,
        )
    }

    private fun estimate(message: ChatMessage): Int = tokenEstimator.estimate(message.content) +
        message.attachments.sumOf { ceil(it.byteSize / 3_072.0).toInt() }
}
package com.nitronbox.app.domain.context

import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.ContextOverflowStrategy
import com.nitronbox.app.data.model.ContextPolicy
import com.nitronbox.app.data.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowEngineTest {
    private val fixedEstimator = TokenEstimator { text -> text.length }

    @Test
    fun `pruning preserves newest messages within budget`() = runBlocking {
        val history = (1..8).map { index ->
            ChatMessage(
                id = "$index",
                conversationId = "thread",
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "message-$index-" + "x".repeat(20),
            )
        }
        val policy = ContextPolicy(
            maxInputTokens = 120,
            reservedOutputTokens = 20,
            strategy = ContextOverflowStrategy.PRUNE_OLDEST,
            preserveRecentMessages = 2,
        )

        val result = ContextWindowEngine(fixedEstimator).assemble("thread", "system", history, policy)

        assertTrue(result.estimatedInputTokens <= 100)
        assertTrue(result.messages.any { it.id == "8" })
        assertTrue(result.omittedMessageCount > 0)
        assertEquals(MessageRole.SYSTEM, result.messages.first().role)
    }
}
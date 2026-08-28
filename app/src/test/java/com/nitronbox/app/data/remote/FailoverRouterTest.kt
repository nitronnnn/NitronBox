package com.nitronbox.app.data.remote

import com.nitronbox.app.data.model.GenerationSettings
import com.nitronbox.app.data.model.ModelRoute
import com.nitronbox.app.data.model.ModelTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FailoverRouterTest {
    @Test
    fun `route falls back before content is emitted`() = runBlocking {
        val failed = FakeBridge("primary") {
            throw ProviderException("busy", "primary", 503, retryable = true)
        }
        val healthy = FakeBridge("secondary") {
            emit(StreamEvent.TextDelta("ok"))
            emit(StreamEvent.Completed("stop"))
        }
        val route = ModelRoute(
            primary = ModelTarget("primary", "a"),
            fallbacks = listOf(ModelTarget("secondary", "b")),
        )

        val events = FailoverRouter(ProviderRegistry(listOf(failed, healthy)))
            .stream(route, ChatCompletionRequest("a", emptyList(), GenerationSettings(), "request"))
            .toList()

        assertEquals("ok", (events.first() as StreamEvent.TextDelta).text)
    }
}

private class FakeBridge(
    override val providerId: String,
    private val stream: suspend kotlinx.coroutines.flow.FlowCollector<StreamEvent>.() -> Unit,
) : AiProviderBridge {
    override suspend fun discoverModels(forceRefresh: Boolean) = emptyList<DiscoveredModel>()
    override suspend fun healthCheck() = ProviderHealth(true, 1)
    override fun streamChat(request: ChatCompletionRequest): Flow<StreamEvent> = flow(stream)
}
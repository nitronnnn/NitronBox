package com.nitronbox.app.data.remote

import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.GenerationSettings
import com.nitronbox.app.data.model.MessageRole
import com.nitronbox.app.data.model.ModelRoute
import com.nitronbox.app.data.model.ModelTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/** Provider-neutral contract. UI and persistence code must depend on this boundary only. */
interface AiProviderBridge {
    val providerId: String

    suspend fun discoverModels(forceRefresh: Boolean = false): List<DiscoveredModel>
    suspend fun healthCheck(): ProviderHealth
    fun streamChat(request: ChatCompletionRequest): Flow<StreamEvent>
}

@Serializable
data class DiscoveredModel(
    val id: String,
    val displayName: String = id,
    val providerId: String,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val contextWindowTokens: Int? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class ModelCapabilities(
    val chat: Boolean = true,
    val streaming: Boolean = true,
    val vision: Boolean = false,
    val tools: Boolean = false,
    val jsonMode: Boolean = false,
    val audio: Boolean = false,
)

/** An image resolved by the attachment pipeline, keyed to the message it belongs to. */
data class InlineImage(
    /** IANA media type, e.g. image/jpeg. */
    val mediaType: String,
    val base64Data: String,
)

data class ChatCompletionRequest(
    val modelId: String,
    val messages: List<ChatMessage>,
    val generation: GenerationSettings,
    val requestId: String,
    /** Images per message id; only messages present here get image parts in the wire format. */
    val imagesByMessageId: Map<String, List<InlineImage>> = emptyMap(),
)

sealed interface StreamEvent {
    data class Started(val providerId: String, val modelId: String) : StreamEvent
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ToolCallDelta(val id: String?, val name: String?, val argumentsDelta: String) : StreamEvent
    data class Usage(val inputTokens: Int?, val outputTokens: Int?) : StreamEvent
    data class Completed(val finishReason: String?) : StreamEvent
}

data class ProviderHealth(
    val reachable: Boolean,
    val latencyMillis: Long,
    val detail: String? = null,
)

class ProviderException(
    message: String,
    val providerId: String,
    val httpCode: Int? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

class ProviderRegistry(bridges: Collection<AiProviderBridge>) {
    private val bridgesById = bridges.associateBy(AiProviderBridge::providerId)

    fun require(providerId: String): AiProviderBridge = bridgesById[providerId]
        ?: throw ProviderException("Provider '$providerId' is not configured", providerId)

    fun all(): List<AiProviderBridge> = bridgesById.values.toList()
}

/**
 * Sequential route failover. A second provider is never started after the first visible delta,
 * preventing duplicate/contradictory assistant text from appearing in the conversation.
 */
class FailoverRouter(private val registry: ProviderRegistry) {
    fun stream(route: ModelRoute, request: ChatCompletionRequest): Flow<StreamEvent> = flow {
        val targets = buildList {
            add(route.primary)
            if (route.policy.enabled) addAll(route.fallbacks)
        }
        var terminalFailure: Throwable? = null

        targets.forEachIndexed { targetIndex, target ->
            repeat(route.policy.maxAttemptsPerTarget.coerceAtLeast(1)) { attempt ->
                var emittedContent = false
                try {
                    registry.require(target.providerId)
                        .streamChat(request.copy(modelId = target.modelId))
                        .collect { event ->
                            if (event is StreamEvent.TextDelta || event is StreamEvent.ReasoningDelta ||
                                event is StreamEvent.ToolCallDelta
                            ) {
                                emittedContent = true
                            }
                            emit(event)
                        }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    terminalFailure = failure
                    if (emittedContent || !failure.canRetry(route, targetIndex, targets.lastIndex)) {
                        throw failure
                    }
                    if (attempt < route.policy.maxAttemptsPerTarget - 1) {
                        delay(route.policy.retryBaseDelayMillis * (attempt + 1))
                    }
                }
            }
        }
        throw terminalFailure ?: ProviderException("No model target is configured", "route")
    }

    private fun Throwable.canRetry(route: ModelRoute, targetIndex: Int, lastIndex: Int): Boolean {
        val providerFailure = this as? ProviderException ?: return targetIndex < lastIndex
        val codeAllowsRetry = providerFailure.httpCode?.let(route.policy.retryableHttpCodes::contains)
        return (providerFailure.retryable || codeAllowsRetry == true) &&
            (targetIndex < lastIndex || route.policy.maxAttemptsPerTarget > 1)
    }
}

internal fun MessageRole.wireName(): String = name.lowercase()
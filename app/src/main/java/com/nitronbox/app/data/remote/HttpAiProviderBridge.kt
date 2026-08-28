package com.nitronbox.app.data.remote

import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.MessageRole
import com.nitronbox.app.data.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class HttpAiProviderBridge(
    private val profile: ProviderProfile,
    private val credentialStore: CredentialStore,
    baseClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : AiProviderBridge {
    override val providerId: String = profile.id
    private val client = baseClient.newBuilder()
        .connectTimeout(profile.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(profile.readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
    private val cache = ConcurrentHashMap<String, ModelCacheEntry>()

    init {
        validateProfile(profile)
    }

    override suspend fun discoverModels(forceRefresh: Boolean): List<DiscoveredModel> {
        val cached = cache[CACHE_KEY]
        if (!forceRefresh && cached != null && System.currentTimeMillis() < cached.expiresAt) {
            return cached.models
        }
        return withContext(Dispatchers.IO) {
            val requestBuilder = authenticatedRequest(resolve(profile.discovery.path))
            val request = when (profile.discovery.method) {
                HttpMethod.GET -> requestBuilder.get().build()
                HttpMethod.POST -> requestBuilder.post(
                    (profile.discovery.body ?: "{}")
                        .toRequestBody(JSON_MEDIA_TYPE),
                ).build()
            }
            execute(request).use { response ->
                requireSuccess(response.code, response.body?.string(), "model discovery").let { body ->
                    val models = normalizeModels(json.parseToJsonElement(body))
                    cache[CACHE_KEY] = ModelCacheEntry(models, System.currentTimeMillis() + CACHE_TTL_MILLIS)
                    models
                }
            }
        }
    }

    override suspend fun healthCheck(): ProviderHealth {
        val startedAt = System.nanoTime()
        return runCatching { discoverModels(forceRefresh = true) }.fold(
            onSuccess = {
                ProviderHealth(true, (System.nanoTime() - startedAt) / 1_000_000)
            },
            onFailure = {
                ProviderHealth(false, (System.nanoTime() - startedAt) / 1_000_000, it.message)
            },
        )
    }

    override fun streamChat(request: ChatCompletionRequest): Flow<StreamEvent> = flow {
        emit(StreamEvent.Started(providerId, request.modelId))
        val wireRequest = when (profile.protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> openAiRequest(request)
            ProviderProtocol.ANTHROPIC -> anthropicRequest(request)
            ProviderProtocol.GEMINI -> geminiRequest(request)
            ProviderProtocol.OLLAMA -> ollamaRequest(request)
        }
        val httpRequest = authenticatedRequest(resolve(wireRequest.path))
            .post(wireRequest.body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", if (profile.protocol == ProviderProtocol.OLLAMA) "application/x-ndjson" else "text/event-stream")
            .header("X-Request-ID", request.requestId)
            .build()

        execute(httpRequest).use { response ->
            if (!response.isSuccessful) {
                requireSuccess(response.code, response.body?.string(), "streaming chat")
            }
            val source = response.body?.source()
                ?: throw ProviderException("Provider returned an empty stream", providerId, response.code)
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                val payload = when {
                    line.startsWith("data:") -> line.removePrefix("data:").trim()
                    profile.protocol == ProviderProtocol.OLLAMA && line.isNotBlank() -> line.trim()
                    else -> continue
                }
                if (payload == "[DONE]") break
                parseStreamPayload(payload).forEach { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseStreamPayload(payload: String): List<StreamEvent> = runCatching {
        val root = json.parseToJsonElement(payload).jsonObject
        when (profile.protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> parseOpenAiEvent(root)
            ProviderProtocol.ANTHROPIC -> parseAnthropicEvent(root)
            ProviderProtocol.GEMINI -> parseGeminiEvent(root)
            ProviderProtocol.OLLAMA -> parseOllamaEvent(root)
        }
    }.getOrElse { failure ->
        throw ProviderException("Malformed stream event", providerId, retryable = false, cause = failure)
    }

    private fun parseOpenAiEvent(root: JsonObject): List<StreamEvent> = buildList {
        root["choices"]?.asArray()?.firstOrNull()?.asObject()?.let { choice ->
            val delta = choice["delta"]?.asObject()
            delta?.string("content")?.takeIf(String::isNotEmpty)?.let { add(StreamEvent.TextDelta(it)) }
            (delta?.string("reasoning_content") ?: delta?.string("reasoning"))
                ?.takeIf(String::isNotEmpty)?.let { add(StreamEvent.ReasoningDelta(it)) }
            delta?.get("tool_calls")?.asArray()?.forEach { toolElement ->
                val tool = toolElement.asObject() ?: return@forEach
                val function = tool["function"]?.asObject()
                add(StreamEvent.ToolCallDelta(tool.string("id"), function?.string("name"), function?.string("arguments").orEmpty()))
            }
            choice.string("finish_reason")?.let { add(StreamEvent.Completed(it)) }
        }
        root["usage"]?.asObject()?.let { usage ->
            add(StreamEvent.Usage(usage.int("prompt_tokens"), usage.int("completion_tokens")))
        }
    }

    private fun parseAnthropicEvent(root: JsonObject): List<StreamEvent> = buildList {
        when (root.string("type")) {
            "content_block_delta" -> {
                val delta = root["delta"]?.asObject()
                when (delta?.string("type")) {
                    "text_delta" -> delta.string("text")?.let { add(StreamEvent.TextDelta(it)) }
                    "thinking_delta" -> delta.string("thinking")?.let { add(StreamEvent.ReasoningDelta(it)) }
                    "input_json_delta" -> add(StreamEvent.ToolCallDelta(null, null, delta.string("partial_json").orEmpty()))
                }
            }
            "message_delta" -> {
                root["usage"]?.asObject()?.let { add(StreamEvent.Usage(null, it.int("output_tokens"))) }
                add(StreamEvent.Completed(root["delta"]?.asObject()?.string("stop_reason")))
            }
            "message_start" -> root["message"]?.asObject()?.get("usage")?.asObject()?.let {
                add(StreamEvent.Usage(it.int("input_tokens"), null))
            }
            "error" -> throw ProviderException(
                root["error"]?.asObject()?.string("message") ?: "Anthropic stream error",
                providerId,
            )
        }
    }

    private fun parseGeminiEvent(root: JsonObject): List<StreamEvent> = buildList {
        val candidate = root["candidates"]?.asArray()?.firstOrNull()?.asObject()
        candidate?.get("content")?.asObject()?.get("parts")?.asArray()?.forEach { part ->
            part.asObject()?.string("text")?.let { add(StreamEvent.TextDelta(it)) }
        }
        candidate?.string("finishReason")?.let { add(StreamEvent.Completed(it)) }
        root["usageMetadata"]?.asObject()?.let {
            add(StreamEvent.Usage(it.int("promptTokenCount"), it.int("candidatesTokenCount")))
        }
    }

    private fun parseOllamaEvent(root: JsonObject): List<StreamEvent> = buildList {
        root["message"]?.asObject()?.string("content")?.takeIf(String::isNotEmpty)
            ?.let { add(StreamEvent.TextDelta(it)) }
        if (root.boolean("done") == true) {
            add(StreamEvent.Usage(root.int("prompt_eval_count"), root.int("eval_count")))
            add(StreamEvent.Completed(root.string("done_reason")))
        }
    }

    private fun openAiRequest(request: ChatCompletionRequest) = WireRequest(
        path = "v1/chat/completions",
        body = buildJsonObject {
            put("model", request.modelId)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            putGeneration(request)
            put("messages", openAiMessageArray(request.messages, request.imagesByMessageId))
        },
    )

    private fun anthropicRequest(request: ChatCompletionRequest): WireRequest {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n", transform = ChatMessage::content)
        return WireRequest(
            path = "v1/messages",
            body = buildJsonObject {
                put("model", request.modelId)
                put("stream", true)
                put("max_tokens", request.generation.maxOutputTokens)
                put("temperature", request.generation.temperature)
                put("top_p", request.generation.topP)
                if (system.isNotBlank()) put("system", system)
                put("messages", anthropicMessageArray(request.messages.filter { it.role != MessageRole.SYSTEM }, request.imagesByMessageId))
            },
        )
    }

    private fun geminiRequest(request: ChatCompletionRequest): WireRequest {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n", transform = ChatMessage::content)
        return WireRequest(
            path = "v1beta/models/${encodePathSegment(request.modelId.removePrefix("models/"))}:streamGenerateContent?alt=sse",
            body = buildJsonObject {
                if (system.isNotBlank()) put("systemInstruction", buildJsonObject {
                    putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                })
                putJsonArray("contents") {
                    request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                        add(buildJsonObject {
                            put("role", if (message.role == MessageRole.ASSISTANT) "model" else "user")
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", message.content) })
                                request.imagesByMessageId[message.id].orEmpty().forEach { image ->
                                    add(buildJsonObject {
                                        put("inlineData", buildJsonObject {
                                            put("mimeType", image.mediaType)
                                            put("data", image.base64Data)
                                        })
                                    })
                                }
                            }
                        })
                    }
                }
                put("generationConfig", buildJsonObject {
                    put("temperature", request.generation.temperature)
                    put("topP", request.generation.topP)
                    put("maxOutputTokens", request.generation.maxOutputTokens)
                })
            },
        )
    }

    private fun ollamaRequest(request: ChatCompletionRequest): WireRequest {
        val imagesByMessageId = request.imagesByMessageId
        return WireRequest(
            path = "api/chat",
            body = buildJsonObject {
                put("model", request.modelId)
                put("stream", true)
                putJsonArray("messages") {
                    request.messages.forEach { message ->
                        add(buildJsonObject {
                            put("role", message.role.wireName())
                            put("content", message.content)
                            val images = imagesByMessageId[message.id].orEmpty()
                            if (images.isNotEmpty()) {
                                putJsonArray("images") { images.forEach { add(JsonPrimitive(it.base64Data)) } }
                            }
                        })
                    }
                }
                put("options", buildJsonObject {
                    put("temperature", request.generation.temperature)
                    put("top_p", request.generation.topP)
                    put("num_predict", request.generation.maxOutputTokens)
                })
            },
        )
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putGeneration(request: ChatCompletionRequest) {
        put("temperature", request.generation.temperature)
        put("top_p", request.generation.topP)
        put("max_tokens", request.generation.maxOutputTokens)
        request.generation.frequencyPenalty?.let { put("frequency_penalty", it) }
        request.generation.presencePenalty?.let { put("presence_penalty", it) }
        request.generation.seed?.let { put("seed", it) }
        if (request.generation.stopSequences.isNotEmpty()) {
            put("stop", buildJsonArray {
                request.generation.stopSequences.forEach { add(JsonPrimitive(it)) }
            })
        }
    }

    /** OpenAI-compatible multimodal content: text parts plus data-URL image parts. */
    private fun openAiMessageArray(
        messages: List<ChatMessage>,
        imagesByMessageId: Map<String, List<InlineImage>>,
    ): JsonArray = buildJsonArray {
        messages.forEach { message ->
            add(buildJsonObject {
                put("role", message.role.wireName())
                val images = imagesByMessageId[message.id].orEmpty()
                if (images.isEmpty()) {
                    put("content", message.content)
                } else {
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", message.content)
                        })
                        images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:${image.mediaType};base64,${image.base64Data}")
                                })
                            })
                        }
                    }
                }
            })
        }
    }

    /** Anthropic content blocks: text plus base64 image sources. */
    private fun anthropicMessageArray(
        messages: List<ChatMessage>,
        imagesByMessageId: Map<String, List<InlineImage>>,
    ): JsonArray = buildJsonArray {
        messages.forEach { message ->
            add(buildJsonObject {
                put("role", message.role.wireName())
                val images = imagesByMessageId[message.id].orEmpty()
                if (images.isEmpty()) {
                    put("content", message.content)
                } else {
                    putJsonArray("content") {
                        images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", image.mediaType)
                                    put("data", image.base64Data)
                                })
                            })
                        }
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", message.content)
                        })
                    }
                }
            })
        }
    }

    private fun normalizeModels(root: JsonElement): List<DiscoveredModel> {
        val modelElements = when (root) {
            is JsonArray -> root
            is JsonObject -> sequenceOf("data", "models", "items")
                .mapNotNull { root[it] as? JsonArray }
                .firstOrNull()
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return modelElements.mapNotNull { element ->
            val model = element.asObject() ?: return@mapNotNull null
            val rawId = model.string("id") ?: model.string("name") ?: model.string("model") ?: return@mapNotNull null
            val id = if (profile.protocol == ProviderProtocol.GEMINI) rawId.removePrefix("models/") else rawId
            val methods = model["supportedGenerationMethods"]?.asArray()
                ?.mapNotNull { it.asPrimitive()?.contentOrNull }.orEmpty()
            DiscoveredModel(
                id = id,
                displayName = model.string("display_name") ?: model.string("displayName") ?: model.string("name") ?: id,
                providerId = providerId,
                contextWindowTokens = model.int("context_window") ?: model.int("inputTokenLimit"),
                capabilities = ModelCapabilities(
                    chat = methods.isEmpty() || methods.any { it.contains("generateContent", ignoreCase = true) },
                    streaming = true,
                    vision = model.boolean("vision") ?: id.contains("vision", true),
                    tools = model.boolean("tools") ?: model.boolean("supports_tools") ?: false,
                    jsonMode = model.boolean("json_mode") ?: false,
                ),
                metadata = model.mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
                }.toMap(),
            )
        }.distinctBy(DiscoveredModel::id).sortedBy(DiscoveredModel::displayName)
    }

    private fun authenticatedRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Content-Type", "application/json")
        profile.customHeaders.forEach(builder::header)
        val secret = profile.credentialAlias?.let(credentialStore::get)
        try {
            if (secret != null) {
                val value = secret.concatToString()
                when (profile.protocol) {
                    ProviderProtocol.ANTHROPIC -> builder
                        .header("x-api-key", value)
                        .header("anthropic-version", "2023-06-01")
                    ProviderProtocol.GEMINI -> builder.header("x-goog-api-key", value)
                    ProviderProtocol.OPENAI_COMPATIBLE -> builder.header("Authorization", "Bearer $value")
                    ProviderProtocol.OLLAMA -> Unit
                }
            }
        } finally {
            secret?.fill('\u0000')
        }
        return builder
    }

    private fun execute(request: Request): okhttp3.Response = try {
        client.newCall(request).execute()
    } catch (io: IOException) {
        throw ProviderException("Network request failed: ${io.message}", providerId, retryable = true, cause = io)
    }

    private fun requireSuccess(code: Int, responseBody: String?, operation: String): String {
        if (code in 200..299) return responseBody.orEmpty()
        val safeDetail = responseBody.orEmpty().take(MAX_ERROR_BODY_CHARS)
        throw ProviderException(
            message = "$operation failed with HTTP $code${if (safeDetail.isBlank()) "" else ": $safeDetail"}",
            providerId = providerId,
            httpCode = code,
            retryable = code in RETRYABLE_CODES,
        )
    }

    private fun resolve(path: String): String = profile.baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private data class WireRequest(val path: String, val body: JsonObject)
    private data class ModelCacheEntry(val models: List<DiscoveredModel>, val expiresAt: Long)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val RETRYABLE_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
        const val CACHE_KEY = "models"
        const val CACHE_TTL_MILLIS = 5 * 60 * 1_000L
        const val MAX_ERROR_BODY_CHARS = 2_048

        fun validateProfile(profile: ProviderProfile) {
            val base = profile.baseUrl.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Provider base URL is invalid")
            require(base.scheme == "https" || base.host in setOf("localhost", "127.0.0.1", "10.0.2.2")) {
                "Provider endpoint must use HTTPS; cleartext is only accepted for explicit loopback hosts"
            }
            require(profile.discovery.path.isNotBlank()) { "Discovery path cannot be blank" }
        }

        fun encodePathSegment(value: String): String = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("placeholder.invalid")
            .addPathSegment(value)
            .build()
            .encodedPathSegments[0]
    }
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
private fun JsonElement.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
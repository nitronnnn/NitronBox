package app.nitronbox.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
    @Volatile private var activeCall: Call? = null

    fun cancel() { activeCall?.cancel() }

    suspend fun models(provider: Provider, apiKey: String, baseUrl: String): List<AiModel> = withContext(Dispatchers.IO) {
        val endpoint = when {
            provider.custom -> modelsUrl(baseUrl)
            provider.protocol == Protocol.GEMINI -> "${provider.modelsUrl}?key=${apiKey.encode()}&pageSize=1000"
            else -> provider.modelsUrl
        }
        val request = requestBuilder(endpoint, provider, apiKey).get().build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val body = raw.jsonOrEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(body, response.code))
            val source = if (provider.protocol == Protocol.GEMINI) body.optJSONArray("models") else body.optJSONArray("data") ?: body.optJSONArray("models")
            val result = buildList {
                for (index in 0 until (source?.length() ?: 0)) {
                    val item = source!!.optJSONObject(index) ?: continue
                    if (provider.protocol == Protocol.GEMINI) {
                        val methods = item.optJSONArray("supportedGenerationMethods")
                        if (methods != null && !(0 until methods.length()).any { methods.optString(it) == "generateContent" }) continue
                    }
                    val rawId = item.optString("id").ifBlank { item.optString("name") }
                    val id = if (provider.protocol == Protocol.GEMINI) rawId.removePrefix("models/") else rawId
                    if (id.isNotBlank()) add(AiModel(id, item.optString("displayName").ifBlank { item.optString("name").ifBlank { id } }, item.optString("description").ifBlank { item.optString("owned_by") }))
                }
            }
            result.distinctBy { it.id }.sortedBy { it.title.lowercase() }
        }.also { activeCall = null }
    }

    suspend fun chat(
        provider: Provider,
        apiKey: String,
        baseUrl: String,
        model: String,
        system: String,
        temperature: Float,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val endpoint = when {
            provider.custom -> chatUrl(baseUrl)
            provider.protocol == Protocol.GEMINI -> "${provider.chatUrl}/models/${model.encode()}:streamGenerateContent?alt=sse&key=${apiKey.encode()}"
            else -> provider.chatUrl
        }
        val payload = when (provider.protocol) {
            Protocol.ANTHROPIC -> anthropicPayload(model, system, temperature, messages)
            Protocol.GEMINI -> geminiPayload(system, temperature, messages)
            Protocol.OPENAI -> openAiPayload(model, system, temperature, messages)
        }
        val request = requestBuilder(endpoint, provider, apiKey)
            .post(payload.toString().toRequestBody(jsonType)).build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty().jsonOrEmpty()
                throw ApiException(errorMessage(body, response.code))
            }
            val source = response.body?.source() ?: throw ApiException("Провайдер вернул пустой ответ")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val event = data.jsonOrEmpty()
                event.optJSONObject("error")?.let { throw ApiException(it.optString("message", "Ошибка потока")) }
                val delta = when (provider.protocol) {
                    Protocol.ANTHROPIC -> if (event.optString("type") == "content_block_delta") event.optJSONObject("delta")?.optString("text").orEmpty() else ""
                    Protocol.GEMINI -> event.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.strings("text").orEmpty()
                    Protocol.OPENAI -> event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                }
                if (delta.isNotEmpty()) onDelta(delta)
            }
        }
        activeCall = null
    }

    private fun requestBuilder(url: String, provider: Provider, key: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        when (provider.protocol) {
            Protocol.ANTHROPIC -> builder.header("x-api-key", key).header("anthropic-version", "2023-06-01")
            Protocol.GEMINI -> Unit
            Protocol.OPENAI -> builder.header("Authorization", "Bearer $key")
        }
        if (provider.id == "openrouter") builder.header("HTTP-Referer", "https://nitronbox.app").header("X-Title", "NitronBox")
        return builder
    }

    private fun openAiPayload(model: String, system: String, temperature: Float, messages: List<ChatMessage>) = JSONObject().apply {
        put("model", model); put("stream", true); put("temperature", temperature)
        put("messages", JSONArray().apply {
            if (system.isNotBlank()) put(JSONObject().put("role", "system").put("content", system))
            messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
        })
    }

    private fun anthropicPayload(model: String, system: String, temperature: Float, messages: List<ChatMessage>) = JSONObject().apply {
        put("model", model); put("stream", true); put("max_tokens", 4096); put("temperature", temperature)
        if (system.isNotBlank()) put("system", system)
        put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
    }

    private fun geminiPayload(system: String, temperature: Float, messages: List<ChatMessage>) = JSONObject().apply {
        if (system.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        put("contents", JSONArray().apply { messages.forEach { put(JSONObject().put("role", if (it.role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", it.content)))) } })
        put("generationConfig", JSONObject().put("temperature", temperature))
    }

    private fun modelsUrl(base: String): String {
        val clean = base.trim().trimEnd('/')
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "Некорректный Base URL" }
        return if (clean.endsWith("/chat/completions")) clean.removeSuffix("/chat/completions") + "/models" else clean + if (clean.endsWith("/v1")) "/models" else "/v1/models"
    }

    private fun chatUrl(base: String): String {
        val clean = base.trim().trimEnd('/')
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "Некорректный Base URL" }
        return if (clean.endsWith("/chat/completions")) clean else clean + if (clean.endsWith("/v1")) "/chat/completions" else "/v1/chat/completions"
    }

    private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")
    private fun String.jsonOrEmpty() = try { JSONObject(this) } catch (_: Exception) { JSONObject() }
    private fun errorMessage(body: JSONObject, code: Int) = body.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: body.optString("message").takeIf { it.isNotBlank() } ?: "Ошибка провайдера $code"
    private fun JSONArray.strings(key: String) = buildString { for (i in 0 until length()) append(optJSONObject(i)?.optString(key).orEmpty()) }
}

class ApiException(message: String) : Exception(message)

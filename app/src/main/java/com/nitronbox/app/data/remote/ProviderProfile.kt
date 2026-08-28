package com.nitronbox.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ProviderProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val credentialAlias: String? = null,
    val protocol: ProviderProtocol,
    val discovery: DiscoveryRequest = protocol.defaultDiscovery(),
    val customHeaders: Map<String, String> = emptyMap(),
    val connectTimeoutSeconds: Long = 20,
    val readTimeoutSeconds: Long = 120,
) {
    /**
     * Base URL with redundant API suffixes removed. Users habitually paste endpoints like
     * `https://host/v1/chat/completions`; the bridge appends protocol paths itself, so any
     * of the well-known suffixes are trimmed repeatedly from the end.
     */
    fun normalizedBaseUrl(): String {
        var url = baseUrl.trim().trimEnd('/')
        val suffixes = listOf(
            "chat/completions", "completions", "messages", "streamGenerateContent",
            "generateContent", "models", "embeddings", "api/chat", "api/generate",
            "api/tags", "api/embed", "v1", "v1beta", "v1alpha", "v2",
        ).sortedByDescending { it.length }
        var changed = true
        while (changed) {
            changed = false
            for (suffix in suffixes) {
                val candidate = "/" + suffix
                if (url.endsWith(candidate, ignoreCase = true)) {
                    url = url.removeSuffix(candidate).trimEnd('/')
                    changed = true
                }
            }
        }
        return url
    }
}

@Serializable
enum class ProviderProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
    OLLAMA;

    /** Human-readable label used across settings and pickers. */
    fun friendlyName(): String = when (this) {
        OPENAI_COMPATIBLE -> "OpenAI compatible"
        ANTHROPIC -> "Anthropic"
        GEMINI -> "Gemini"
        OLLAMA -> "Ollama"
    }

    fun defaultDiscovery(): DiscoveryRequest = when (this) {
        OPENAI_COMPATIBLE -> DiscoveryRequest(path = "v1/models")
        ANTHROPIC -> DiscoveryRequest(path = "v1/models")
        GEMINI -> DiscoveryRequest(path = "v1beta/models")
        OLLAMA -> DiscoveryRequest(path = "api/tags")
    }
}

@Serializable
data class DiscoveryRequest(
    val method: HttpMethod = HttpMethod.GET,
    val path: String,
    /** Optional JSON object for custom POST-based discovery APIs. */
    val body: String? = null,
)

@Serializable
enum class HttpMethod { GET, POST }

object OfficialProviderProfiles {
    fun openAi(credentialAlias: String) = ProviderProfile(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/",
        credentialAlias = credentialAlias,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
    )

    fun anthropic(credentialAlias: String) = ProviderProfile(
        id = "anthropic",
        displayName = "Anthropic",
        baseUrl = "https://api.anthropic.com/",
        credentialAlias = credentialAlias,
        protocol = ProviderProtocol.ANTHROPIC,
    )

    fun gemini(credentialAlias: String) = ProviderProfile(
        id = "gemini",
        displayName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/",
        credentialAlias = credentialAlias,
        protocol = ProviderProtocol.GEMINI,
    )

    fun deepSeek(credentialAlias: String) = ProviderProfile(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/",
        credentialAlias = credentialAlias,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
    )

    fun groq(credentialAlias: String) = ProviderProfile(
        id = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/",
        credentialAlias = credentialAlias,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
    )
}
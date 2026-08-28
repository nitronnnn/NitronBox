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
)

@Serializable
enum class ProviderProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
    OLLAMA;

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
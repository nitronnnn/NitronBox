package app.nitronbox.mobile

data class Provider(
    val id: String,
    val title: String,
    val badge: String,
    val color: Long,
    val keyHint: String,
    val chatUrl: String = "",
    val modelsUrl: String = "",
    val protocol: Protocol = Protocol.OPENAI,
    val custom: Boolean = false,
)

enum class Protocol { OPENAI, ANTHROPIC, GEMINI }

data class AiModel(val id: String, val title: String, val description: String = "")
data class ChatMessage(val id: Long, val role: String, val content: String)
data class Conversation(val id: Long, val title: String, val messages: List<ChatMessage>)

val Providers = listOf(
    Provider("openai", "OpenAI", "OA", 0xFF10A37F, "sk-...", "https://api.openai.com/v1/chat/completions", "https://api.openai.com/v1/models"),
    Provider("anthropic", "Anthropic", "AN", 0xFFD97757, "sk-ant-...", "https://api.anthropic.com/v1/messages", "https://api.anthropic.com/v1/models?limit=1000", Protocol.ANTHROPIC),
    Provider("gemini", "Google Gemini", "G", 0xFF4285F4, "AIza...", "https://generativelanguage.googleapis.com/v1beta", "https://generativelanguage.googleapis.com/v1beta/models", Protocol.GEMINI),
    Provider("openrouter", "OpenRouter", "OR", 0xFF7868FF, "sk-or-...", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/models"),
    Provider("groq", "Groq", "GQ", 0xFFF55036, "gsk_...", "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models"),
    Provider("mistral", "Mistral AI", "MI", 0xFFFF7000, "API key", "https://api.mistral.ai/v1/chat/completions", "https://api.mistral.ai/v1/models"),
    Provider("xai", "xAI", "x", 0xFF202020, "xai-...", "https://api.x.ai/v1/chat/completions", "https://api.x.ai/v1/models"),
    Provider("custom", "Свой провайдер", "+", 0xFF00A99D, "API key", custom = true),
)

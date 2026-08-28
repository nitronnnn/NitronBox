package com.nitronbox.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * An isolated AI environment. Secrets are deliberately represented by aliases and are stored
 * through SecureCredentialStore, never in this serializable/Room-backed graph.
 */
@Serializable
data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "sparkles",
    val systemPrompt: String = "You are a helpful assistant.",
    val route: ModelRoute,
    val generation: GenerationSettings = GenerationSettings(),
    val contextPolicy: ContextPolicy = ContextPolicy(),
    val knowledge: KnowledgeConfiguration = KnowledgeConfiguration(),
    val theme: WorkspaceTheme = WorkspaceTheme(),
    val providerConfigurations: List<WorkspaceProviderConfiguration> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun validate(): List<WorkspaceValidationError> = buildList {
        if (name.isBlank()) add(WorkspaceValidationError.EmptyName)
        if (name.length > MAX_WORKSPACE_NAME_LENGTH) add(WorkspaceValidationError.NameTooLong)
        if (route.primary.providerId.isBlank() || route.primary.modelId.isBlank()) {
            add(WorkspaceValidationError.InvalidPrimaryRoute)
        }
        if (route.fallbacks.any { it.providerId.isBlank() || it.modelId.isBlank() }) {
            add(WorkspaceValidationError.InvalidFallbackRoute)
        }
        if (generation.temperature !in 0f..2f) add(WorkspaceValidationError.InvalidTemperature)
        if (generation.topP !in 0f..1f) add(WorkspaceValidationError.InvalidTopP)
        if (contextPolicy.maxInputTokens < MIN_CONTEXT_TOKENS) {
            add(WorkspaceValidationError.ContextWindowTooSmall)
        }
        if (contextPolicy.reservedOutputTokens >= contextPolicy.maxInputTokens) {
            add(WorkspaceValidationError.OutputReserveExceedsContext)
        }
    }

    companion object {
        const val MAX_WORKSPACE_NAME_LENGTH = 80
        const val MIN_CONTEXT_TOKENS = 512
    }
}

@Serializable
data class WorkspaceProviderConfiguration(
    val providerId: String,
    val baseUrl: String,
    val credentialAlias: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class ModelRoute(
    val primary: ModelTarget,
    val fallbacks: List<ModelTarget> = emptyList(),
    val policy: FailoverPolicy = FailoverPolicy(),
)

@Serializable
data class ModelTarget(
    val providerId: String,
    /** Opaque model identifier returned by provider discovery; never inferred from displayName. */
    val modelId: String,
    val displayName: String = modelId,
)

@Serializable
data class FailoverPolicy(
    val enabled: Boolean = true,
    val maxAttemptsPerTarget: Int = 1,
    val retryBaseDelayMillis: Long = 350,
    val retryableHttpCodes: Set<Int> = setOf(408, 425, 429, 500, 502, 503, 504),
)

@Serializable
data class GenerationSettings(
    val temperature: Float = 0.7f,
    val topP: Float = 1f,
    /** Null means no cap is requested: the field is omitted from wire requests entirely. */
    val maxOutputTokens: Int? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val seed: Long? = null,
    val stopSequences: List<String> = emptyList(),
)

@Serializable
data class ContextPolicy(
    /** Effectively unlimited by default; lower it to enable context-window trimming. */
    val maxInputTokens: Int = UNLIMITED_CONTEXT_TOKENS,
    val reservedOutputTokens: Int = 0,
    val strategy: ContextOverflowStrategy = ContextOverflowStrategy.PRUNE_OLDEST,
    val preserveRecentMessages: Int = 8,
    val summaryPrompt: String = DEFAULT_SUMMARY_PROMPT,
) {
    companion object {
        const val DEFAULT_SUMMARY_PROMPT =
            "Summarize the conversation faithfully. Preserve decisions, constraints, names, and open tasks."
        const val UNLIMITED_CONTEXT_TOKENS = 1_000_000
    }
}

@Serializable
enum class ContextOverflowStrategy { PRUNE_OLDEST, SUMMARIZE, REJECT }

@Serializable
data class KnowledgeConfiguration(
    val indexedAttachmentIds: Set<String> = emptySet(),
    val vectorStores: List<VectorStoreReference> = emptyList(),
    val retrieval: RetrievalSettings = RetrievalSettings(),
)

@Serializable
data class VectorStoreReference(
    val id: String,
    val name: String,
    val providerId: String? = null,
    val remoteStoreId: String? = null,
)

@Serializable
data class RetrievalSettings(
    val enabled: Boolean = false,
    val topK: Int = 6,
    val minimumScore: Float = 0.55f,
)

@Serializable
data class WorkspaceTheme(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette(),
    val typography: TypographyConfiguration = TypographyConfiguration(),
    val surface: SurfaceConfiguration = SurfaceConfiguration(),
    val reduceMotion: Boolean = false,
)

/** Colors are ARGB longs to keep the domain module independent from Compose. */
@Serializable
data class ThemePalette(
    val lightBackground: Long = 0xFFFFFFFF,
    val darkBackground: Long = 0xFF0A0A0A,
    val lightSurface: Long = 0xFFFFFFFF,
    val darkSurface: Long = 0xFF0A0A0A,
    val lightMuted: Long = 0xFFF7F7F8,
    val darkMuted: Long = 0xFF141414,
    val lightBorder: Long = 0xFFEBEBEB,
    val darkBorder: Long = 0xFF262626,
    val lightText: Long = 0xFF171717,
    val darkText: Long = 0xFFEDEDED,
    val accent: Long = 0xFF0070F3,
    val destructive: Long = 0xFFE11D48,
)

@Serializable
data class TypographyConfiguration(
    val source: FontSource = FontSource.SYSTEM,
    val familyName: String = "Inter",
    /** Persisted SAF URI. Access must be retained by ContentResolver before saving it. */
    val localFontUri: String? = null,
    val googleFontQuery: String? = null,
    val scale: Float = 1f,
)

@Serializable
enum class FontSource { SYSTEM, GOOGLE_FONTS, LOCAL_FILE }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class SurfaceConfiguration(
    /** Base corner radius in dp; the flat system derives the rest from it. */
    val cornerRadiusDp: Float = 12f,
)

sealed interface WorkspaceValidationError {
    data object EmptyName : WorkspaceValidationError
    data object NameTooLong : WorkspaceValidationError
    data object InvalidPrimaryRoute : WorkspaceValidationError
    data object InvalidFallbackRoute : WorkspaceValidationError
    data object InvalidTemperature : WorkspaceValidationError
    data object InvalidTopP : WorkspaceValidationError
    data object ContextWindowTooSmall : WorkspaceValidationError
    data object OutputReserveExceedsContext : WorkspaceValidationError
}
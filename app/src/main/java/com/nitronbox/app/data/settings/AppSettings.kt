package com.nitronbox.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "nitronbox_settings")

private val settingsJson = Json { ignoreUnknownKeys = true }

/** Currently selected model target shown in the chat header and used for generation. */
data class ActiveModel(
    val providerId: String,
    val modelId: String,
    val displayName: String,
)

enum class ThemeModeSetting { SYSTEM, LIGHT, DARK }

enum class LanguageSetting { SYSTEM, ENGLISH, RUSSIAN }

enum class WallpaperPreset { NONE, MIDNIGHT, AURORA, SUNSET, GRAPHITE, LOGO, CUSTOM }

/** A reusable instruction snippet the user can toggle into every chat's system prompt. */
@Serializable
data class Skill(
    val name: String,
    val prompt: String,
    val enabled: Boolean = true,
)

/**
 * Process-wide UI state that should survive restarts but does not belong in Room:
 * which workspace/conversation is open and which model is selected.
 */
class AppSettings(private val context: Context) {
    val activeWorkspaceId: Flow<String?> = context.dataStore.data.map { it[KEY_WORKSPACE_ID] }

    val activeConversationId: Flow<String?> = context.dataStore.data.map { it[KEY_CONVERSATION_ID] }

    val activeModel: Flow<ActiveModel?> = context.dataStore.data.map { prefs ->
        val providerId = prefs[KEY_MODEL_PROVIDER] ?: return@map null
        val modelId = prefs[KEY_MODEL_ID] ?: return@map null
        ActiveModel(providerId, modelId, prefs[KEY_MODEL_DISPLAY] ?: modelId)
    }

    val themeMode: Flow<ThemeModeSetting> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { stored ->
            ThemeModeSetting.entries.firstOrNull { it.name == stored }
        } ?: ThemeModeSetting.SYSTEM
    }

    val language: Flow<LanguageSetting> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE]?.let { stored ->
            LanguageSetting.entries.firstOrNull { it.name == stored }
        } ?: LanguageSetting.SYSTEM
    }

    val wallpaper: Flow<WallpaperPreset> = context.dataStore.data.map { prefs ->
        prefs[KEY_WALLPAPER]?.let { stored ->
            WallpaperPreset.entries.firstOrNull { it.name == stored }
        } ?: WallpaperPreset.LOGO
    }

    val wallpaperImageUri: Flow<String?> = context.dataStore.data.map { it[KEY_WALLPAPER_URI] }

    suspend fun setActiveWorkspace(id: String?) = context.dataStore.edit { prefs ->
        if (id == null) prefs.remove(KEY_WORKSPACE_ID) else prefs[KEY_WORKSPACE_ID] = id
    }

    suspend fun setActiveConversation(id: String?) = context.dataStore.edit { prefs ->
        if (id == null) prefs.remove(KEY_CONVERSATION_ID) else prefs[KEY_CONVERSATION_ID] = id
    }

    suspend fun setActiveModel(model: ActiveModel?) = context.dataStore.edit { prefs ->
        if (model == null) {
            prefs.remove(KEY_MODEL_PROVIDER)
            prefs.remove(KEY_MODEL_ID)
            prefs.remove(KEY_MODEL_DISPLAY)
        } else {
            prefs[KEY_MODEL_PROVIDER] = model.providerId
            prefs[KEY_MODEL_ID] = model.modelId
            prefs[KEY_MODEL_DISPLAY] = model.displayName
        }
    }

    suspend fun setThemeMode(mode: ThemeModeSetting) = context.dataStore.edit { prefs ->
        prefs[KEY_THEME_MODE] = mode.name
    }

    suspend fun setLanguage(language: LanguageSetting) = context.dataStore.edit { prefs ->
        prefs[KEY_LANGUAGE] = language.name
    }

    suspend fun setWallpaper(preset: WallpaperPreset) = context.dataStore.edit { prefs ->
        prefs[KEY_WALLPAPER] = preset.name
    }

    suspend fun setWallpaperImageUri(uri: String?) = context.dataStore.edit { prefs ->
        if (uri == null) prefs.remove(KEY_WALLPAPER_URI) else prefs[KEY_WALLPAPER_URI] = uri
    }

    /** One-time upgrade: workspaces created with the old capped defaults become unlimited. */
    val legacyDefaultsMigrated: Flow<Boolean> = context.dataStore.data.map { it[KEY_DEFAULTS_MIGRATED] == "true" }

    /** SAF tree of the project folder used by Creator-mode chats. */
    val activeCreatorFolder: Flow<String?> = context.dataStore.data.map { it[KEY_CREATOR_FOLDER] }

    suspend fun setActiveCreatorFolder(uri: String?) = context.dataStore.edit { prefs ->
        if (uri == null) prefs.remove(KEY_CREATOR_FOLDER) else prefs[KEY_CREATOR_FOLDER] = uri
    }

    // --- Skills ---

    val skills: Flow<List<Skill>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SKILLS]?.let { stored ->
            runCatching { settingsJson.decodeFromString<List<Skill>>(stored) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun setSkills(list: List<Skill>) = context.dataStore.edit { prefs ->
        prefs[KEY_SKILLS] = settingsJson.encodeToString(list)
    }

    suspend fun loadSkills(): List<Skill> = skills.first()

    // --- Background effects ---

    val blurEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BLUR_ENABLED] != false }

    /** Maximum blur radius in dp for panel overlays. */
    val blurStrength: Flow<Float> = context.dataStore.data.map { it[KEY_BLUR_STRENGTH] ?: 18f }

    /** When true, panels themselves are translucent frosted instead of solid. */
    val blurredPanels: Flow<Boolean> = context.dataStore.data.map { it[KEY_BLUR_PANELS] != false }

    /** Separate blur radius for frosted panels (dp). */
    val panelBlurStrength: Flow<Float> = context.dataStore.data.map { it[KEY_PANEL_BLUR_STRENGTH] ?: 24f }

    suspend fun setBlurEnabled(enabled: Boolean) = context.dataStore.edit { prefs ->
        prefs[KEY_BLUR_ENABLED] = enabled
    }

    suspend fun setBlurStrength(strength: Float) = context.dataStore.edit { prefs ->
        prefs[KEY_BLUR_STRENGTH] = strength
    }

    suspend fun setBlurredPanels(enabled: Boolean) = context.dataStore.edit { prefs ->
        prefs[KEY_BLUR_PANELS] = enabled
    }

    suspend fun setPanelBlurStrength(strength: Float) = context.dataStore.edit { prefs ->
        prefs[KEY_PANEL_BLUR_STRENGTH] = strength
    }

    suspend fun markLegacyDefaultsMigrated() = context.dataStore.edit { prefs ->
        prefs[KEY_DEFAULTS_MIGRATED] = "true"
    }

    suspend fun loadThemeMode(): ThemeModeSetting = themeMode.first()

    suspend fun loadLanguage(): LanguageSetting = language.first()

    suspend fun loadActiveModel(): ActiveModel? = activeModel.first()

    private companion object {
        val KEY_WORKSPACE_ID = stringPreferencesKey("active_workspace_id")
        val KEY_CONVERSATION_ID = stringPreferencesKey("active_conversation_id")
        val KEY_MODEL_PROVIDER = stringPreferencesKey("active_model_provider")
        val KEY_MODEL_ID = stringPreferencesKey("active_model_id")
        val KEY_MODEL_DISPLAY = stringPreferencesKey("active_model_display")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_WALLPAPER = stringPreferencesKey("wallpaper_preset")
        val KEY_WALLPAPER_URI = stringPreferencesKey("wallpaper_image_uri")
        val KEY_DEFAULTS_MIGRATED = stringPreferencesKey("legacy_defaults_migrated")
        val KEY_CREATOR_FOLDER = stringPreferencesKey("creator_folder_uri")
        val KEY_SKILLS = stringPreferencesKey("skills_json")
        val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        val KEY_BLUR_STRENGTH = floatPreferencesKey("blur_strength")
        val KEY_BLUR_PANELS = booleanPreferencesKey("blur_panels")
        val KEY_PANEL_BLUR_STRENGTH = floatPreferencesKey("panel_blur_strength")
    }
}

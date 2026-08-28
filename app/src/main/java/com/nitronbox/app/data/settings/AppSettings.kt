package com.nitronbox.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nitronbox_settings")

/** Currently selected model target shown in the chat header and used for generation. */
data class ActiveModel(
    val providerId: String,
    val modelId: String,
    val displayName: String,
)

enum class ThemeModeSetting { SYSTEM, LIGHT, DARK }

enum class LanguageSetting { SYSTEM, ENGLISH, RUSSIAN }

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
    }
}

package com.nitronbox.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitronbox.app.AppContainer
import com.nitronbox.app.data.remote.DiscoveredModel
import com.nitronbox.app.data.remote.ProviderHealth
import com.nitronbox.app.data.remote.ProviderProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.chatRepository
    private val credentialStore = container.credentialStore

    val providers: StateFlow<List<ProviderProfile>> = repository.observeProviderProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workspaces: StateFlow<List<com.nitronbox.app.data.model.Workspace>> = repository.observeWorkspaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeWorkspace: StateFlow<com.nitronbox.app.data.model.Workspace?> =
        kotlinx.coroutines.flow.combine(
            container.appSettings.activeWorkspaceId,
            repository.observeWorkspaces(),
        ) { id, list -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shared with the chat model picker so discoveries are visible app-wide immediately. */
    val discoveredModels: StateFlow<Map<String, List<DiscoveredModel>>> =
        container.modelCatalogStore.models

    private val _providerHealth = MutableStateFlow<Map<String, ProviderHealth>>(emptyMap())
    val providerHealth: StateFlow<Map<String, ProviderHealth>> = _providerHealth.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val themeMode: StateFlow<com.nitronbox.app.data.settings.ThemeModeSetting> =
        container.appSettings.themeMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.nitronbox.app.data.settings.ThemeModeSetting.SYSTEM)

    val language: StateFlow<com.nitronbox.app.data.settings.LanguageSetting> =
        container.appSettings.language
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.nitronbox.app.data.settings.LanguageSetting.SYSTEM)

    fun setThemeMode(mode: com.nitronbox.app.data.settings.ThemeModeSetting) {
        viewModelScope.launch { container.appSettings.setThemeMode(mode) }
    }

    fun setLanguage(language: com.nitronbox.app.data.settings.LanguageSetting) {
        viewModelScope.launch { container.appSettings.setLanguage(language) }
    }

    val wallpaper: StateFlow<com.nitronbox.app.data.settings.WallpaperPreset> =
        container.appSettings.wallpaper
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.nitronbox.app.data.settings.WallpaperPreset.NONE)

    val wallpaperImageUri: StateFlow<String?> = container.appSettings.wallpaperImageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWallpaper(preset: com.nitronbox.app.data.settings.WallpaperPreset) {
        viewModelScope.launch { container.appSettings.setWallpaper(preset) }
    }

    fun setWallpaperImage(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                container.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                container.appSettings.setWallpaperImageUri(uri.toString())
                container.appSettings.setWallpaper(com.nitronbox.app.data.settings.WallpaperPreset.CUSTOM)
            }.onFailure { emit("Unable to use this image") }
        }
    }

    fun saveProvider(profile: ProviderProfile, apiKey: CharArray?) {
        viewModelScope.launch {
            runCatching {
                var saved = profile
                repository.saveProviderProfile(saved)
                if (apiKey != null && apiKey.isNotEmpty()) {
                    val alias = credentialAlias(profile.id)
                    credentialStore.put(alias, apiKey)
                    // Persist the profile with its alias after the key is safely stored.
                    if (profile.credentialAlias != alias) {
                        saved = profile.copy(credentialAlias = alias)
                        repository.saveProviderProfile(saved)
                    }
                }
                saved
            }.onSuccess { saved ->
                emit("Provider saved")
                // Warm the shared catalog so the model picker is ready right away.
                container.modelCatalogStore.refresh(saved.id)
            }.onFailure { emit(it.message ?: "Unable to save provider") }
        }
    }

    fun deleteProvider(profileId: String) {
        viewModelScope.launch {
            repository.deleteProviderProfile(profileId)
            credentialStore.delete(credentialAlias(profileId))
            container.modelCatalogStore.forget(profileId)
            _providerHealth.value = _providerHealth.value - profileId
            emit("Provider removed")
        }
    }

    fun testProvider(profileId: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val bridge = bridgeFor(profileId) ?: return@launch
                val health = bridge.healthCheck()
                _providerHealth.value = _providerHealth.value + (profileId to health)
                emit(
                    if (health.reachable) "Provider reachable (${health.latencyMillis} ms)"
                    else "Unreachable: ${health.detail ?: "unknown error"}",
                )
            } finally {
                _busy.value = false
            }
        }
    }

    fun discoverModels(profileId: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val models = container.modelCatalogStore.refresh(profileId)
                if (models != null) {
                    emit("Loaded ${models.size} models")
                } else {
                    emit("Model discovery failed. Check the endpoint and API key.")
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveWorkspace(workspace: com.nitronbox.app.data.model.Workspace) {
        viewModelScope.launch {
            val errors = workspace.validate()
            if (errors.isNotEmpty()) {
                emit("Invalid workspace: ${errors.first().displayName()}")
            } else {
                runCatching { repository.saveWorkspace(workspace) }
                    .onSuccess { emit("Workspace saved") }
                    .onFailure { emit(it.message ?: "Unable to save workspace") }
            }
        }
    }

    private suspend fun bridgeFor(profileId: String) = runCatching {
        val profile = repository.providerProfile(profileId) ?: return@runCatching null
        container.providerRegistryFactory.create(listOf(profile)).require(profileId)
    }.getOrElse {
        emit(it.message ?: "Provider is not available")
        null
    }

    private fun credentialAlias(providerId: String) = "provider.$providerId"

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}

private fun com.nitronbox.app.data.model.WorkspaceValidationError.displayName(): String = when (this) {
    com.nitronbox.app.data.model.WorkspaceValidationError.EmptyName -> "the name cannot be empty"
    com.nitronbox.app.data.model.WorkspaceValidationError.NameTooLong -> "the name is too long"
    com.nitronbox.app.data.model.WorkspaceValidationError.InvalidPrimaryRoute -> "the primary route is incomplete"
    com.nitronbox.app.data.model.WorkspaceValidationError.InvalidFallbackRoute -> "a fallback route is incomplete"
    com.nitronbox.app.data.model.WorkspaceValidationError.InvalidTemperature -> "temperature must be between 0 and 2"
    com.nitronbox.app.data.model.WorkspaceValidationError.InvalidTopP -> "top-p must be between 0 and 1"
    com.nitronbox.app.data.model.WorkspaceValidationError.ContextWindowTooSmall -> "the context window is too small"
    com.nitronbox.app.data.model.WorkspaceValidationError.OutputReserveExceedsContext -> "the output reserve exceeds the context window"
}

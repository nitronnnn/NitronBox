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

    val skills: StateFlow<List<com.nitronbox.app.data.settings.Skill>> =
        container.appSettings.skills
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveSkill(skill: com.nitronbox.app.data.settings.Skill) {
        viewModelScope.launch {
            val updated = container.appSettings.loadSkills().filterNot { it.name == skill.name } + skill
            container.appSettings.setSkills(updated)
        }
    }

    fun toggleSkill(skill: com.nitronbox.app.data.settings.Skill) {
        saveSkill(skill.copy(enabled = !skill.enabled))
    }

    val blurEnabled: StateFlow<Boolean> = container.appSettings.blurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val blurStrength: StateFlow<Float> = container.appSettings.blurStrength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 18f)

    val blurredPanels: StateFlow<Boolean> = container.appSettings.blurredPanels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val panelBlurStrength: StateFlow<Float> = container.appSettings.panelBlurStrength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 24f)

    fun setBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setBlurEnabled(enabled) }
    }

    fun setBlurStrength(strength: Float) {
        viewModelScope.launch { container.appSettings.setBlurStrength(strength) }
    }

    fun setBlurredPanels(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setBlurredPanels(enabled) }
    }

    fun setPanelBlurStrength(strength: Float) {
        viewModelScope.launch { container.appSettings.setPanelBlurStrength(strength) }
    }

    private val _galleryImages = MutableStateFlow<List<String>>(emptyList())
    val galleryImages: StateFlow<List<String>> = _galleryImages.asStateFlow()

    init {
        loadGallery()
    }

    fun loadGallery() {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val list = mutableListOf<String>()
                runCatching {
                    container.contentResolver.query(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(android.provider.MediaStore.Images.Media._ID),
                        null,
                        null,
                        android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC",
                    )?.use { cursor ->
                        while (cursor.moveToNext() && list.size < 300) {
                            val id = cursor.getLong(0)
                            list.add(
                                android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    id,
                                ).toString(),
                            )
                        }
                    }
                }
                _galleryImages.value = list
            }
        }
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
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val file = java.io.File(container.filesDir, "wallpaper_custom.jpg")
                    container.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Unable to read the image")
                    container.appSettings.setWallpaperImageUri(file.absolutePath)
                    container.appSettings.setWallpaper(com.nitronbox.app.data.settings.WallpaperPreset.CUSTOM)
                }.onFailure { emit("Unable to use this image") }
            }
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

    /** Health-checks a possibly-unsaved draft provider straight from the editor form. */
    fun testDraft(profile: ProviderProfile) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val bridge = runCatching {
                    container.providerRegistryFactory.create(listOf(profile)).require(profile.id)
                }.getOrElse {
                    emit(it.message ?: "Invalid provider")
                    return@launch
                }
                val health = bridge.healthCheck()
                _providerHealth.value = _providerHealth.value + (profile.id to health)
                emit(if (health.reachable) "Provider reachable (${health.latencyMillis} ms)" else "Unreachable: ${health.detail ?: "unknown error"}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun testProvider(profileId: String) {
        viewModelScope.launch {
            repository.providerProfile(profileId)?.let { testDraft(it) }
        }
    }

    /** Discovers models for a possibly-unsaved draft provider and caches them app-wide. */
    fun discoverDraft(profile: ProviderProfile) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val bridge = runCatching {
                    container.providerRegistryFactory.create(listOf(profile)).require(profile.id)
                }.getOrElse {
                    emit(it.message ?: "Invalid provider")
                    return@launch
                }
                val models = runCatching { bridge.discoverModels(forceRefresh = true) }.getOrElse {
                    emit("Model discovery failed: ${it.message}")
                    return@launch
                }
                container.modelCatalogStore.putModels(profile.id, models)
                emit("Loaded ${models.size} models")
            } finally {
                _busy.value = false
            }
        }
    }

    fun discoverModels(profileId: String) {
        viewModelScope.launch {
            repository.providerProfile(profileId)?.let { discoverDraft(it) }
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

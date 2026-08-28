package com.nitronbox.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nitronbox.app.AppContainer
import com.nitronbox.app.data.local.ChatRepository
import com.nitronbox.app.data.local.ConversationEntity
import com.nitronbox.app.data.model.AttachmentReference
import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.ModelRoute
import com.nitronbox.app.data.model.ModelTarget
import com.nitronbox.app.data.model.Workspace
import com.nitronbox.app.data.remote.DiscoveredModel
import com.nitronbox.app.data.remote.ProviderRegistry
import com.nitronbox.app.data.settings.ActiveModel
import com.nitronbox.app.domain.chat.ChatService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.chatRepository
    private val service = container.chatService
    private val settings = container.appSettings
    private val catalogStore = container.modelCatalogStore

    val workspaces: StateFlow<List<Workspace>> = repository.observeWorkspaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeWorkspace: StateFlow<Workspace?> = combine(
        settings.activeWorkspaceId,
        repository.observeWorkspaces(),
    ) { id, list -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conversations: StateFlow<List<ConversationEntity>> = activeWorkspace
        .flatMapLatest { workspace ->
            workspace?.let { repository.observeConversations(it.id) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeConversation: StateFlow<ConversationEntity?> = combine(
        settings.activeConversationId,
        conversations,
    ) { id, list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeModel: StateFlow<ActiveModel?> = settings.activeModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val providers: StateFlow<List<com.nitronbox.app.data.remote.ProviderProfile>> =
        repository.observeProviderProfiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shared across the app, so models discovered in Settings appear here immediately. */
    val discoveredModels: StateFlow<Map<String, List<DiscoveredModel>>> = catalogStore.models

    val wallpaper: StateFlow<com.nitronbox.app.data.settings.WallpaperPreset> = settings.wallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.nitronbox.app.data.settings.WallpaperPreset.NONE)

    val wallpaperImageUri: StateFlow<String?> = settings.wallpaperImageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: kotlinx.coroutines.flow.Flow<PagingData<ChatMessage>> = settings.activeConversationId
        .flatMapLatest { id ->
            id?.let { repository.messages(it) } ?: flowOf<PagingData<ChatMessage>>(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    /** Rough fill level of the context window for the current conversation (0f..1f). */
    val contextUsage: StateFlow<Float> = combine(
        activeWorkspace,
        settings.activeConversationId.flatMapLatest { id ->
            id?.let { container.dao.observeContextChars(it) } ?: flowOf(0L)
        },
    ) { workspace, chars ->
        val policy = workspace?.contextPolicy ?: return@combine 0f
        val budget = (policy.maxInputTokens - policy.reservedOutputTokens).coerceAtLeast(1)
        ((chars / CHARS_PER_TOKEN_ESTIMATE) / budget).toFloat().coerceIn(0f, 1f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<AttachmentReference>>(emptyList())
    val pendingAttachments: StateFlow<List<AttachmentReference>> = _pendingAttachments.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val registryFlow: StateFlow<ProviderRegistry?> = repository.observeProviderProfiles()
        .map { profiles -> container.providerRegistryFactory.create(profiles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var generationJob: Job? = null

    init {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        if (repository.workspaceCount() == 0) {
            repository.saveWorkspace(defaultWorkspace())
        }
        val id = settings.activeWorkspaceId.first()
        val list = repository.observeWorkspaces().first()
        if (id == null || list.none { it.id == id }) {
            list.firstOrNull()?.let { settings.setActiveWorkspace(it.id) }
        }
        migrateLegacyDefaults()
    }

    /** Workspaces created before unlimited defaults keep working but get the new no-limit behavior. */
    private suspend fun migrateLegacyDefaults() {
        if (settings.legacyDefaultsMigrated.first()) return
        repository.observeWorkspaces().first().forEach { workspace ->
            val isLegacyDefault = workspace.generation.maxOutputTokens == 2_048 &&
                workspace.contextPolicy.maxInputTokens == 16_384
            if (isLegacyDefault) {
                repository.saveWorkspace(
                    workspace.copy(
                        generation = workspace.generation.copy(maxOutputTokens = null),
                        contextPolicy = workspace.contextPolicy.copy(
                            maxInputTokens = com.nitronbox.app.data.model.ContextPolicy.UNLIMITED_CONTEXT_TOKENS,
                            reservedOutputTokens = 0,
                        ),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
        settings.markLegacyDefaultsMigrated()
    }

    private fun defaultWorkspace() = Workspace(
        name = "Personal",
        systemPrompt = "You are a helpful assistant. Answer clearly and concisely.",
        route = ModelRoute(
            primary = ModelTarget(
                providerId = UNCONFIGURED_PROVIDER,
                modelId = UNCONFIGURED_MODEL,
                displayName = "Select a model",
            ),
        ),
    )

    // --- Messaging ---

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    fun send() {
        val workspace = activeWorkspace.value ?: return
        val model = activeModel.value
        val text = _draft.value.trim()
        if (text.isBlank() && _pendingAttachments.value.isEmpty()) return
        if (model == null || model.providerId == UNCONFIGURED_PROVIDER) {
            emit("Select a provider and model in Settings first.")
            return
        }
        if (_isStreaming.value) return
        val attachments = _pendingAttachments.value
        _draft.value = ""
        _pendingAttachments.value = emptyList()
        val conversationId = activeConversation.value?.id
        generationJob = viewModelScope.launch {
            try {
                val prepared = service.prepareUserMessage(workspace, conversationId, text, attachments)
                if (conversationId != prepared.conversationId) {
                    settings.setActiveConversation(prepared.conversationId)
                }
                runGeneration(
                    conversationId = prepared.conversationId,
                    workspace = workspace,
                    target = ModelTarget(model.providerId, model.modelId, model.displayName),
                )
            } catch (failure: Exception) {
                _draft.value = _draft.value.ifBlank { text }
                emit(failure.message ?: "Unable to send the message")
            }
        }
    }

    private suspend fun runGeneration(conversationId: String, workspace: Workspace, target: ModelTarget) {
        val registry = registryFlow.value ?: container.loadRegistry()
        _isStreaming.value = true
        try {
            when (val outcome = service.generateReply(conversationId, workspace, registry, target)) {
                is ChatService.TurnOutcome.Failed -> emit(outcome.errorText)
                else -> Unit
            }
        } finally {
            _isStreaming.value = false
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    /** Retries a failed reply or regenerates an existing assistant answer. */
    fun regenerate(messageId: String) {
        val workspace = activeWorkspace.value ?: return
        val model = activeModel.value ?: run {
            emit("Select a provider and model in Settings first.")
            return
        }
        if (_isStreaming.value) return
        generationJob = viewModelScope.launch {
            val conversationId = service.deleteForRetry(messageId) ?: return@launch
            runGeneration(
                conversationId = conversationId,
                workspace = workspace,
                target = ModelTarget(model.providerId, model.modelId, model.displayName),
            )
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    private fun activeConversationId(): String? = activeConversation.value?.id

    // --- Conversations ---

    /** Creates the conversation right away so it appears in the drawer immediately. */
    fun newConversation() {
        viewModelScope.launch {
            val workspace = activeWorkspace.value ?: return@launch
            val conversation = repository.createConversation(workspace.id, ChatRepository.DEFAULT_TITLE)
            settings.setActiveConversation(conversation.id)
        }
    }

    fun selectConversation(id: String) {
        viewModelScope.launch { settings.setActiveConversation(id) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (settings.activeConversationId.first() == id) {
                settings.setActiveConversation(null)
            }
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch {
            runCatching { repository.renameConversation(id, title.trim()) }
                .onFailure { emit(it.message ?: "Unable to rename") }
        }
    }

    fun selectWorkspace(id: String) {
        viewModelScope.launch {
            settings.setActiveWorkspace(id)
            settings.setActiveConversation(null)
        }
    }

    // --- Attachments ---

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                runCatching { container.attachmentPipeline.ingest(uri) }
                    .onSuccess { reference ->
                        _pendingAttachments.value = _pendingAttachments.value + reference
                    }
                    .onFailure { emit("Unsupported file: ${it.message}") }
            }
        }
    }

    fun removeAttachment(attachment: AttachmentReference) {
        _pendingAttachments.value = _pendingAttachments.value - attachment
    }

    // --- Model selection ---

    fun setActiveModel(model: ActiveModel) {
        viewModelScope.launch { settings.setActiveModel(model) }
    }

    fun refreshModels(providerId: String) {
        viewModelScope.launch {
            val models = catalogStore.refresh(providerId)
            if (models == null) emit("Model discovery failed. Check the provider settings.")
        }
    }

    fun refreshAllModels() {
        viewModelScope.launch {
            repository.providerProfiles().forEach { profile ->
                if (!catalogStore.hasCatalog(profile.id)) refreshModels(profile.id)
            }
        }
    }

    // --- Appearance ---

    fun setWallpaper(preset: com.nitronbox.app.data.settings.WallpaperPreset) {
        viewModelScope.launch { settings.setWallpaper(preset) }
    }

    fun setWallpaperImage(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                container.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                settings.setWallpaperImageUri(uri.toString())
                settings.setWallpaper(com.nitronbox.app.data.settings.WallpaperPreset.CUSTOM)
            }.onFailure { emit("Unable to use this image") }
        }
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }

    private companion object {
        const val UNCONFIGURED_PROVIDER = "unconfigured"
        const val UNCONFIGURED_MODEL = "unconfigured"
        const val CHARS_PER_TOKEN_ESTIMATE = 3.0
    }
}

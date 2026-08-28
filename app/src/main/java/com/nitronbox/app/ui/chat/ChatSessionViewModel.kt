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

    val normalConversations: kotlinx.coroutines.flow.Flow<List<ConversationEntity>> =
        conversations.map { list -> list.filter { it.folderUri == null } }

    val creatorConversations: kotlinx.coroutines.flow.Flow<List<ConversationEntity>> =
        conversations.map { list -> list.filter { it.folderUri != null } }

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

    // --- Creator mode ---

    private val _creatorMode = MutableStateFlow(false)
    val creatorMode: kotlinx.coroutines.flow.StateFlow<Boolean> = _creatorMode.asStateFlow()

    val creatorFolderUri: StateFlow<String?> = settings.activeCreatorFolder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCreatorMode(enabled: Boolean) {
        _creatorMode.value = enabled
    }

    fun pickCreatorFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                container.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                settings.setActiveCreatorFolder(uri.toString())
            }.onFailure { emit("Unable to use this folder") }
        }
    }

    /** Display name of the creator folder for the drawer (last path segment). */
    fun folderDisplayName(uri: String?): String = uri
        ?.let { android.net.Uri.parse(it).lastPathSegment }
        ?.substringAfterLast(':')
        ?.takeIf { it.isNotBlank() }
        ?: "project"

    // --- Skills ---

    val skills: StateFlow<List<com.nitronbox.app.data.settings.Skill>> = settings.skills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveSkill(skill: com.nitronbox.app.data.settings.Skill) {
        viewModelScope.launch {
            val updated = settings.loadSkills().filterNot { it.name == skill.name } + skill
            settings.setSkills(updated)
        }
    }

    fun toggleSkill(skill: com.nitronbox.app.data.settings.Skill) {
        saveSkill(skill.copy(enabled = !skill.enabled))
    }

    // --- Appearance effects ---

    fun setBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBlurEnabled(enabled) }
    }

    fun setBlurStrength(strength: Float) {
        viewModelScope.launch { settings.setBlurStrength(strength) }
    }

    fun setBlurredPanels(enabled: Boolean) {
        viewModelScope.launch { settings.setBlurredPanels(enabled) }
    }

    // --- Device gallery (MediaStore) for the wallpaper picker ---

    private val _galleryImages = MutableStateFlow<List<String>>(emptyList())
    val galleryImages: StateFlow<List<String>> = _galleryImages.asStateFlow()

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

    // --- Attachment viewer ---

    data class AttachmentView(
        val attachment: com.nitronbox.app.data.model.AttachmentReference,
        /** Loaded text content for text-like files; null for media opened as image/external. */
        val text: String? = null,
    )

    private val _viewer = MutableStateFlow<AttachmentView?>(null)
    val viewer: StateFlow<AttachmentView?> = _viewer.asStateFlow()

    fun openAttachment(
        attachment: com.nitronbox.app.data.model.AttachmentReference,
        loadText: Boolean,
    ) {
        _viewer.value = AttachmentView(attachment)
        if (loadText) {
            viewModelScope.launch {
                val text = container.attachmentPipeline.textContent(attachment, maximumTextChars = 200_000)
                _viewer.value = _viewer.value?.copy(text = text ?: "Unable to read the file")
            }
        }
    }

    fun closeViewer() {
        _viewer.value = null
    }

    init {
        viewModelScope.launch { bootstrap() }
        loadGallery()
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
                // Creator chats get filesystem tools rooted at their project folder.
                val folderUri = repository.conversation(prepared.conversationId)?.folderUri
                    ?: _creatorMode.value.takeIf { it }?.let { settings.activeCreatorFolder.first() }
                val creatorTools = folderUri?.let { container.creatorFileTools.rootFor(it) }?.let {
                    container.creatorFileTools
                }
                runGeneration(
                    conversationId = prepared.conversationId,
                    workspace = workspace,
                    target = ModelTarget(model.providerId, model.modelId, model.displayName),
                    creatorTools = creatorTools,
                    creatorRootUri = if (creatorTools != null) folderUri else null,
                    skills = settings.loadSkills(),
                )
            } catch (failure: Exception) {
                _draft.value = _draft.value.ifBlank { text }
                emit(failure.message ?: "Unable to send the message")
            }
        }
    }

    private suspend fun runGeneration(
        conversationId: String,
        workspace: Workspace,
        target: ModelTarget,
        creatorTools: com.nitronbox.app.data.filesystem.CreatorFileTools? = null,
        creatorRootUri: String? = null,
        skills: List<com.nitronbox.app.data.settings.Skill> = emptyList(),
    ) {
        val registry = registryFlow.value ?: container.loadRegistry()
        _isStreaming.value = true
        try {
            when (
                val outcome = service.generateReply(
                    conversationId,
                    workspace,
                    registry,
                    target,
                    creatorTools = creatorTools,
                    creatorRootUri = creatorRootUri,
                    skills = skills,
                )
            ) {
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
                skills = settings.loadSkills(),
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
            val folderUri = if (_creatorMode.value) {
                val folder = settings.activeCreatorFolder.first()
                if (folder == null) {
                    emit("Select a project folder first.")
                    return@launch
                }
                folder
            } else {
                null
            }
            val conversation = repository.createConversation(
                workspace.id,
                ChatRepository.DEFAULT_TITLE,
                folderUri,
            )
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

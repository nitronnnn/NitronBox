package com.nitronbox.app.data.local

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.nitronbox.app.data.model.AttachmentKind
import com.nitronbox.app.data.model.AttachmentReference
import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.MessageRole
import com.nitronbox.app.data.model.MessageStatus
import com.nitronbox.app.data.model.Workspace
import com.nitronbox.app.data.remote.ProviderProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ChatRepository(
    private val dao: NitronBoxDao,
    private val json: Json,
) {
    // --- Messages ---

    fun messages(conversationId: String): Flow<PagingData<ChatMessage>> = Pager(
        config = PagingConfig(
            pageSize = 40,
            prefetchDistance = 12,
            initialLoadSize = 60,
            enablePlaceholders = false,
            maxSize = 160,
        ),
        pagingSourceFactory = { dao.pagedMessages(conversationId) },
    ).flow.map { paging -> paging.map(MessageWithAttachments::toDomain) }

    suspend fun contextMessages(conversationId: String): List<ChatMessage> =
        dao.allMessagesForContext(conversationId).map(MessageWithAttachments::toDomain)

    suspend fun message(messageId: String): ChatMessage? =
        dao.messageWithAttachments(messageId)?.let { row -> row.toDomain() }

    suspend fun saveMessage(message: ChatMessage) {
        dao.insertMessageGraph(
            message = message.toEntity(),
            attachments = message.attachments.map { it.toEntity(message.id) },
        )
    }

    suspend fun updateMessage(message: ChatMessage) = dao.updateMessage(message.toEntity())

    suspend fun deleteMessage(messageId: String) = dao.deleteMessage(messageId)

    // --- Conversations ---

    suspend fun createConversation(workspaceId: String, title: String, folderUri: String? = null): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = newId(),
            workspaceId = workspaceId,
            title = title.ifBlank { DEFAULT_TITLE },
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            folderUri = folderUri,
        )
        dao.upsertConversation(conversation)
        return conversation
    }

    fun observeConversations(workspaceId: String): Flow<List<ConversationEntity>> =
        dao.observeConversations(workspaceId)

    fun observeConversation(id: String): Flow<ConversationEntity?> = dao.observeConversation(id)

    suspend fun conversation(id: String): ConversationEntity? = dao.conversation(id)

    suspend fun touchConversation(id: String) = dao.touchConversation(id, System.currentTimeMillis())

    suspend fun renameConversation(id: String, title: String) {
        require(title.isNotBlank()) { "Conversation title cannot be blank" }
        dao.renameConversation(id, title.take(TITLE_MAX_LENGTH), System.currentTimeMillis())
    }

    suspend fun deleteConversation(id: String) = dao.deleteConversation(id)

    suspend fun persistSummary(conversationId: String, summary: String, through: Long) =
        dao.updateSummary(conversationId, summary, through, System.currentTimeMillis())

    // --- Workspaces ---

    suspend fun workspaceCount(): Int = dao.workspaceCount()

    fun observeWorkspaces(): Flow<List<Workspace>> =
        dao.observeWorkspaces().map { list -> list.map(::decodeWorkspace) }

    fun observeWorkspace(id: String): Flow<Workspace?> =
        dao.observeWorkspace(id).map { it?.let(::decodeWorkspace) }

    suspend fun workspace(id: String): Workspace? = dao.workspace(id)?.let(::decodeWorkspace)

    suspend fun saveWorkspace(workspace: Workspace) {
        val errors = workspace.validate()
        require(errors.isEmpty()) { "Invalid workspace: $errors" }
        dao.upsertWorkspace(
            WorkspaceEntity(
                id = workspace.id,
                name = workspace.name,
                serializedConfiguration = json.encodeToString(Workspace.serializer(), workspace),
                createdAtEpochMillis = workspace.createdAtEpochMillis,
                updatedAtEpochMillis = workspace.updatedAtEpochMillis,
            ),
        )
    }

    suspend fun deleteWorkspace(id: String) = dao.deleteWorkspace(id)

    // --- Provider profiles ---

    fun observeProviderProfiles(): Flow<List<ProviderProfile>> =
        dao.observeProviderProfiles().map { list -> list.map(::decodeProfile) }

    suspend fun providerProfiles(): List<ProviderProfile> = dao.providerProfiles().map(::decodeProfile)

    suspend fun providerProfile(id: String): ProviderProfile? = dao.providerProfile(id)?.let(::decodeProfile)

    suspend fun saveProviderProfile(profile: ProviderProfile) {
        require(profile.baseUrl.toHttpUrlOrNull() != null) { "Provider base URL is invalid" }
        val now = System.currentTimeMillis()
        val existing = dao.providerProfile(profile.id)
        dao.upsertProviderProfile(
            ProviderProfileEntity(
                id = profile.id,
                displayName = profile.displayName,
                serializedProfile = json.encodeToString(ProviderProfile.serializer(), profile),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun deleteProviderProfile(id: String) = dao.deleteProviderProfile(id)

    private fun decodeWorkspace(entity: WorkspaceEntity) =
        json.decodeFromString(Workspace.serializer(), entity.serializedConfiguration)

    private fun decodeProfile(entity: ProviderProfileEntity) =
        json.decodeFromString(ProviderProfile.serializer(), entity.serializedProfile)

    companion object {
        const val TITLE_MAX_LENGTH = 80
        const val DEFAULT_TITLE = "New conversation"

        fun newId(): String = java.util.UUID.randomUUID().toString()

        fun defaultConversationTitle(): String = DEFAULT_TITLE
    }
}

private fun MessageWithAttachments.toDomain() = ChatMessage(
    id = message.id,
    conversationId = message.conversationId,
    role = MessageRole.valueOf(message.role),
    content = message.content,
    attachments = attachments.map(AttachmentEntity::toDomain),
    createdAtEpochMillis = message.createdAtEpochMillis,
    status = MessageStatus.valueOf(message.status),
    providerId = message.providerId,
    modelId = message.modelId,
    inputTokens = message.inputTokens,
    outputTokens = message.outputTokens,
    errorText = message.errorText,
    generationDurationMillis = message.generationDurationMillis,
    reasoning = message.reasoning,
)

private fun AttachmentEntity.toDomain() = AttachmentReference(
    id = id,
    displayName = displayName,
    persistedUri = persistedUri,
    mimeType = mimeType,
    byteSize = byteSize,
    sha256 = sha256,
    kind = AttachmentKind.valueOf(kind),
)

private fun ChatMessage.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    createdAtEpochMillis = createdAtEpochMillis,
    status = status.name,
    providerId = providerId,
    modelId = modelId,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    errorText = errorText,
    generationDurationMillis = generationDurationMillis,
    reasoning = reasoning,
)

private fun AttachmentReference.toEntity(messageId: String) = AttachmentEntity(
    id = id,
    messageId = messageId,
    displayName = displayName,
    persistedUri = persistedUri,
    mimeType = mimeType,
    byteSize = byteSize,
    sha256 = sha256,
    kind = kind.name,
)

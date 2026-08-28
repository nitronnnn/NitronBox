package com.nitronbox.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workspaces",
    indices = [Index(value = ["updatedAtEpochMillis"])],
)
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serializedConfiguration: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workspaceId", "updatedAtEpochMillis"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val summary: String? = null,
    val summaryThroughEpochMillis: Long? = null,
    /** SAF tree URI for Creator-mode chats; null for regular conversations. */
    val folderUri: String? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "createdAtEpochMillis"]),
        Index(value = ["status"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val status: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val errorText: String? = null,
    val generationDurationMillis: Long? = null,
)

@Entity(
    tableName = "provider_profiles",
    indices = [Index(value = ["updatedAtEpochMillis"])],
)
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    /** Serialized [com.nitronbox.app.data.remote.ProviderProfile]; secrets live only in the credential vault. */
    val serializedProfile: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId"]), Index(value = ["sha256"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val displayName: String,
    val persistedUri: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
    val kind: String,
)

data class MessageWithAttachments(
    @androidx.room.Embedded val message: MessageEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
)
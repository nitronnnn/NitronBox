package com.nitronbox.app.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NitronBoxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspaces ORDER BY updatedAtEpochMillis DESC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun workspace(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    fun observeWorkspace(id: String): Flow<WorkspaceEntity?>

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun workspaceCount(): Int

    @Query("DELETE FROM workspaces WHERE id = :workspaceId")
    suspend fun deleteWorkspace(workspaceId: String)

    // --- Provider profiles ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviderProfile(profile: ProviderProfileEntity)

    @Query("SELECT * FROM provider_profiles ORDER BY updatedAtEpochMillis DESC")
    fun observeProviderProfiles(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles ORDER BY updatedAtEpochMillis DESC")
    suspend fun providerProfiles(): List<ProviderProfileEntity>

    @Query("SELECT * FROM provider_profiles WHERE id = :id LIMIT 1")
    suspend fun providerProfile(id: String): ProviderProfileEntity?

    @Query("DELETE FROM provider_profiles WHERE id = :id")
    suspend fun deleteProviderProfile(id: String)

    // --- Conversations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMillis DESC")
    fun observeConversations(workspaceId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId ORDER BY updatedAtEpochMillis DESC")
    fun conversations(workspaceId: String): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun conversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("UPDATE conversations SET updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun touchConversation(id: String, now: Long)

    @Query("UPDATE conversations SET title = :title, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, now: Long)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    // --- Messages ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun message(messageId: String): MessageEntity?

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun messageWithAttachments(messageId: String): MessageWithAttachments?

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    /** Newest-first query works with reverseLayout and avoids loading a whole thread. */
    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis DESC, id DESC")
    fun pagedMessages(conversationId: String): PagingSource<Int, MessageWithAttachments>

    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC, id ASC")
    suspend fun allMessagesForContext(conversationId: String): List<MessageWithAttachments>

    @Query("UPDATE conversations SET summary = :summary, summaryThroughEpochMillis = :through, updatedAtEpochMillis = :now WHERE id = :conversationId")
    suspend fun updateSummary(conversationId: String, summary: String, through: Long, now: Long)

    /** Cheap live estimate of conversation size in characters, for the context-usage indicator. */
    @Query("SELECT COALESCE(SUM(LENGTH(content)), 0) FROM messages WHERE conversationId = :conversationId")
    fun observeContextChars(conversationId: String): Flow<Long>

    @Transaction
    suspend fun insertMessageGraph(message: MessageEntity, attachments: List<AttachmentEntity>) {
        insertMessage(message)
        if (attachments.isNotEmpty()) insertAttachments(attachments)
    }
}
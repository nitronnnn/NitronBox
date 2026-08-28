package com.nitronbox.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val attachments: List<AttachmentReference> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val providerId: String? = null,
    val modelId: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val errorText: String? = null,
)

@Serializable
enum class MessageStatus { QUEUED, STREAMING, COMPLETE, FAILED, CANCELLED }

@Serializable
data class AttachmentReference(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val persistedUri: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
    val kind: AttachmentKind,
)

@Serializable
enum class AttachmentKind { TEXT, PDF, CSV, JSON, IMAGE }

@Serializable
data class ConversationSummary(
    val conversationId: String,
    val content: String,
    val throughMessageEpochMillis: Long,
    val estimatedTokens: Int,
)
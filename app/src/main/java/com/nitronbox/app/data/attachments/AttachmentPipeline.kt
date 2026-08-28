package com.nitronbox.app.data.attachments

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.nitronbox.app.data.model.AttachmentKind
import com.nitronbox.app.data.model.AttachmentReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.FileNotFoundException
import java.security.MessageDigest

class AttachmentPipeline(
    private val resolver: ContentResolver,
    private val maximumBytes: Long = DEFAULT_MAX_BYTES,
) {
    suspend fun ingest(uri: Uri, persistPermission: Boolean = true): AttachmentReference =
        withContext(Dispatchers.IO) {
            if (persistPermission) {
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val metadata = queryMetadata(uri)
            require(metadata.size < 0 || metadata.size <= maximumBytes) {
                "Attachment exceeds the configured size limit"
            }
            val mime = resolver.getType(uri) ?: inferMime(metadata.name)
            val kind = mime.toSupportedKind()
                ?: throw IllegalArgumentException("Unsupported attachment type: $mime")
            var actualSize = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.buffered()?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    actualSize += read
                    require(actualSize <= maximumBytes) { "Attachment exceeds the configured size limit" }
                    digest.update(buffer, 0, read)
                }
            } ?: throw FileNotFoundException("Unable to read selected document")
            AttachmentReference(
                displayName = metadata.name,
                persistedUri = uri.toString(),
                mimeType = mime,
                byteSize = actualSize,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                kind = kind,
            )
        }

    fun multipartPart(attachment: AttachmentReference, fieldName: String = "file"): MultipartBody.Part {
        val uri = Uri.parse(attachment.persistedUri)
        val body = object : RequestBody() {
            override fun contentType() = attachment.mimeType.toMediaTypeOrNull()
            override fun contentLength() = attachment.byteSize
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input -> sink.writeAll(input.source()) }
                    ?: throw FileNotFoundException("Attachment permission was revoked")
            }
        }
        return MultipartBody.Part.createFormData(fieldName, attachment.displayName, body)
    }

    /** Reads a text-like document as UTF-8, bounded to [maximumTextChars]. Returns null on failure. */
    suspend fun textContent(attachment: AttachmentReference, maximumTextChars: Int = 200_000): String? =
        withContext(Dispatchers.IO) {
            if (attachment.byteSize > maximumTextChars * 4L) return@withContext null
            runCatching {
                resolver.openInputStream(Uri.parse(attachment.persistedUri))?.use { input ->
                    input.readBytes().decodeToString().take(maximumTextChars)
                }
            }.getOrNull()
        }

    /** Intended for providers that require inline data. Enforces a lower bound to avoid OOM. */
    suspend fun base64(attachment: AttachmentReference, maximumInlineBytes: Long = 8L * 1_024 * 1_024): String =
        withContext(Dispatchers.IO) {
            require(attachment.byteSize <= maximumInlineBytes) { "Attachment is too large for inline base64" }
            resolver.openInputStream(Uri.parse(attachment.persistedUri))?.use {
                Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            } ?: throw FileNotFoundException("Attachment permission was revoked")
        }

    private fun queryMetadata(uri: Uri): Metadata {
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                size = cursor.longOrNull(OpenableColumns.SIZE) ?: -1L
            }
        }
        return Metadata(name?.take(200) ?: "attachment", size)
    }

    private fun inferMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "txt", "md", "log", "rtf" -> "text/plain"
        "pdf" -> "application/pdf"
        "csv", "tsv" -> "text/csv"
        "json" -> "application/json"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "mp4", "mov", "mkv", "webm", "avi", "m4v" -> "video/mp4"
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus" -> "audio/mpeg"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"
        "js", "mjs", "cjs" -> "text/javascript"
        "ts", "tsx", "jsx" -> "text/plain"
        "py" -> "text/x-python"
        "kt", "kts" -> "text/plain"
        "java" -> "text/plain"
        "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift" -> "text/plain"
        "html", "htm", "css", "scss", "xml", "yaml", "yml", "toml", "ini", "sh", "bat", "sql", "gradle", "properties" -> "text/plain"
        "apk", "exe", "dll", "so", "bin" -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    private data class Metadata(val name: String, val size: Long)

    companion object { const val DEFAULT_MAX_BYTES = 200L * 1_024 * 1_024 }
}

private fun String.toSupportedKind(): AttachmentKind? = when {
    this == "text/plain" || startsWith("text/") -> AttachmentKind.TEXT
    this == "application/pdf" -> AttachmentKind.PDF
    this == "text/csv" || this == "application/csv" -> AttachmentKind.CSV
    this == "application/json" || this == "text/json" -> AttachmentKind.JSON
    startsWith("image/") -> AttachmentKind.IMAGE
    startsWith("video/") -> AttachmentKind.VIDEO
    startsWith("audio/") -> AttachmentKind.AUDIO
    this in setOf(
        "application/zip",
        "application/vnd.rar",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip",
    ) -> AttachmentKind.ARCHIVE
    // Unknown binary formats are still attachable — they ride along as generic files.
    else -> AttachmentKind.FILE
}

private fun Cursor.stringOrNull(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

private fun Cursor.longOrNull(column: String): Long? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

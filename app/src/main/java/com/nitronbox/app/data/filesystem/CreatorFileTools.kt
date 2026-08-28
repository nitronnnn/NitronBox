package com.nitronbox.app.data.filesystem

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * File operations for Creator-mode chats, rooted at a user-picked SAF tree. Paths are relative
 * to the tree root and are sanitized against traversal. Everything runs off the main thread.
 */
class CreatorFileTools(private val context: Context) {

    fun rootFor(treeUri: String): DocumentFile? =
        DocumentFile.fromTreeUri(context, Uri.parse(treeUri))

    private fun sanitize(relativePath: String): List<String> =
        relativePath.replace('\\', '/')
            .split('/')
            .map(String::trim)
            .filter { it.isNotEmpty() && it != "." && it != ".." }

    private fun resolve(root: DocumentFile, relativePath: String): DocumentFile? {
        var current: DocumentFile = root
        for (segment in sanitize(relativePath)) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "txt", "md", "log", "kt", "kts", "java", "py", "js", "ts", "json", "xml", "yaml", "yml",
        "html", "css", "csv", "sh", "gradle", "toml", "ini", "sql", "c", "cpp", "h", "hpp", "cs",
        "go", "rs", "rb", "php", "swift", "dart" -> "text/plain"
        else -> "application/octet-stream"
    }

    /** Recursive listing up to [maxDepth], one entry per line: `dir/ name` or `file name (size)`. */
    suspend fun list(root: DocumentFile, maxDepth: Int = 3): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            for (entry in dir.listFiles()) {
                val name = entry.name ?: continue
                if (entry.isDirectory) {
                    sb.append("dir/ ").append(prefix).append(name).append("/\n")
                    if (depth < maxDepth) walk(entry, "$prefix$name/", depth + 1)
                } else {
                    sb.append("file ").append(prefix).append(name)
                        .append(" (").append(entry.length()).append(" bytes)\n")
                }
            }
        }
        walk(root, "", 0)
        sb.toString().ifBlank { "(empty folder)" }
    }

    suspend fun read(root: DocumentFile, relativePath: String, maxChars: Int = 40_000): String =
        withContext(Dispatchers.IO) {
            val file = resolve(root, relativePath)
                ?: return@withContext "ERROR: not found: $relativePath"
            if (file.isDirectory) return@withContext "ERROR: $relativePath is a folder"
            runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    input.readBytes().decodeToString().take(maxChars)
                } ?: "ERROR: unable to open $relativePath"
            }.getOrElse { "ERROR: ${it.message}" }
        }

    suspend fun write(root: DocumentFile, relativePath: String, content: String): String =
        withContext(Dispatchers.IO) {
            val segments = sanitize(relativePath)
            if (segments.isEmpty()) return@withContext "ERROR: empty path"
            val parentPath = segments.dropLast(1).joinToString("/")
            val parent = if (parentPath.isEmpty()) root else {
                ensureFolder(root, parentPath)
            } ?: return@withContext "ERROR: cannot create parent folder for $relativePath"
            val name = segments.last()
            val existing = parent.findFile(name)
            val target = existing ?: parent.createFile(mimeFor(name), name)
                ?: return@withContext "ERROR: cannot create $relativePath"
            runCatching {
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                    output.write(content.encodeToByteArray())
                } ?: return@withContext "ERROR: unable to open $relativePath for writing"
                "OK: wrote ${content.encodeToByteArray().size} bytes to $relativePath"
            }.getOrElse { "ERROR: ${it.message}" }
        }

    suspend fun createFolder(root: DocumentFile, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val folder = ensureFolder(root, relativePath)
                ?: return@withContext "ERROR: cannot create $relativePath"
            "OK: folder ready ${relativePath.trimEnd('/')}"
        }

    private fun ensureFolder(root: DocumentFile, relativePath: String): DocumentFile? {
        var current: DocumentFile = root
        for (segment in sanitize(relativePath)) {
            val found = current.findFile(segment)
            current = when {
                found != null && found.isDirectory -> found
                found != null -> return null // a file blocks the path
                else -> current.createDirectory(segment) ?: return null
            }
        }
        return current
    }

    suspend fun delete(root: DocumentFile, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val target = resolve(root, relativePath)
                ?: return@withContext "ERROR: not found: $relativePath"
            if (target.uri == root.uri) return@withContext "ERROR: cannot delete the project root"
            if (target.delete()) "OK: deleted $relativePath" else "ERROR: delete failed for $relativePath"
        }

    suspend fun rename(root: DocumentFile, relativePath: String, newName: String): String =
        withContext(Dispatchers.IO) {
            val target = resolve(root, relativePath)
                ?: return@withContext "ERROR: not found: $relativePath"
            if (DocumentsContract.renameDocument(context.contentResolver, target.uri, newName) != null) {
                "OK: renamed $relativePath -> $newName"
            } else {
                "ERROR: rename failed for $relativePath"
            }
        }

    /**
     * Executes one tool call emitted by the model: a JSON object with "action" plus arguments.
     * Returns null when the payload is not a parsable tool call, otherwise a result report.
     */
    suspend fun executeTool(root: DocumentFile, toolJson: String): String? =
        withContext(Dispatchers.IO) {
            val obj = runCatching {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(toolJson).jsonObject
            }.getOrNull() ?: return@withContext null
            val action = (obj["action"] ?: obj["name"])?.jsonPrimitive?.contentOrNull
                ?: return@withContext null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
            when (action.lowercase()) {
                "list" -> list(root)
                "read" -> read(root, path)
                "write" -> write(root, path, obj["content"]?.jsonPrimitive?.contentOrNull ?: "")
                "create_folder" -> createFolder(root, path)
                "delete" -> delete(root, path)
                "rename" -> rename(root, path, obj["new_name"]?.jsonPrimitive?.contentOrNull ?: "")
                else -> null
            }
        }
}

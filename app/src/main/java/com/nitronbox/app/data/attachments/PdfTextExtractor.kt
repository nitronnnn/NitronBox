package com.nitronbox.app.data.attachments

import android.content.Context
import android.net.Uri
import com.nitronbox.app.data.model.AttachmentReference
import com.nitronbox.app.data.model.AttachmentKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort text extraction for PDF attachments so their content can be inlined into the
 * conversation context. Initialization is lazy and safe to call repeatedly; failures return null.
 */
class PdfTextExtractor(
    private val context: Context,
    private val resolver: android.content.ContentResolver,
) {
    private val initializationLock = Any()

    @Volatile private var initialized = false

    suspend fun extract(attachment: AttachmentReference, maximumChars: Int = 80_000): String? =
        withContext(Dispatchers.IO) {
            if (attachment.kind != AttachmentKind.PDF) return@withContext null
            val text = runCatching {
                ensureInitialized()
                resolver.openInputStream(Uri.parse(attachment.persistedUri))?.use { input ->
                    PDDocument.load(input).use { document ->
                        val stripper = PDFTextStripper().apply { endPage = MAX_PAGES }
                        stripper.getText(document)
                    }
                }
            }.getOrNull()
            text?.take(maximumChars)?.takeIf(String::isNotBlank)
        }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initializationLock) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }

    private companion object { const val MAX_PAGES = 60 }
}

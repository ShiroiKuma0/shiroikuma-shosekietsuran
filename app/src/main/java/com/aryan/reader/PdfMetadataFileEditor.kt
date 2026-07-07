package com.aryan.reader

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.aryan.reader.data.BookMetadataEdit
import com.aryan.reader.data.RecentFileItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class AndroidPdfMetadataEditResult(
    val title: String?,
    val author: String?,
    val description: String?,
    val fileSize: Long,
    val fileContentModifiedTimestamp: Long
)

/**
 * 白い熊 UI: writes user-edited metadata into the PDF file itself (document information
 * dictionary): Title, Author, Subject (= description) and Keywords (= library tags).
 * Mirrors EpubMetadataFileEditor: copy the source beside the app, back the original up once,
 * rewrite, then write the bytes back to the original URI.
 */
class PdfMetadataFileEditor(private val context: Context) {

    suspend fun writeMetadata(
        item: RecentFileItem,
        metadata: BookMetadataEdit
    ): Result<AndroidPdfMetadataEditResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(item.type == FileType.PDF) { "Only PDF metadata editing is supported here." }
            val sourceUri = item.uriString?.toUri() ?: error("Book file is not available.")
            PDFBoxResourceLoader.init(context.applicationContext)

            val token = UUID.randomUUID().toString()
            val sourceCopy = File(context.cacheDir, "pdf_metadata_source_$token.pdf")
            val editedCopy = File(context.cacheDir, "pdf_metadata_edited_$token.pdf")

            try {
                copyUriToFile(sourceUri, sourceCopy)
                backupOriginalIfNeeded(item, sourceCopy)

                PDDocument.load(sourceCopy).use { document ->
                    require(!document.isEncrypted) { "Encrypted PDFs cannot be edited." }
                    val info = document.documentInformation
                    info.title = metadata.title?.trim()?.takeIf { it.isNotEmpty() }
                    info.author = metadata.author?.trim()?.takeIf { it.isNotEmpty() }
                    info.subject = metadata.description?.trim()?.takeIf { it.isNotEmpty() }
                    metadata.tags?.let { tags ->
                        info.keywords = tags
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .joinToString(", ")
                            .takeIf { it.isNotEmpty() }
                    }
                    document.save(editedCopy)
                }
                replaceUriBytes(sourceUri, editedCopy)

                AndroidPdfMetadataEditResult(
                    title = metadata.title?.trim()?.takeIf { it.isNotEmpty() },
                    author = metadata.author?.trim()?.takeIf { it.isNotEmpty() },
                    description = metadata.description?.trim()?.takeIf { it.isNotEmpty() },
                    fileSize = queryFileSize(sourceUri).takeIf { it > 0L } ?: editedCopy.length(),
                    fileContentModifiedTimestamp = queryLastModified(sourceUri).takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                )
            } finally {
                sourceCopy.delete()
                editedCopy.delete()
            }
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read PDF source.")
    }

    private fun replaceUriBytes(uri: Uri, editedFile: File) {
        if (uri.scheme == "file") {
            val target = File(uri.path ?: error("Invalid file URI."))
            editedFile.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            editedFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to write PDF source.")
    }

    private fun backupOriginalIfNeeded(item: RecentFileItem, sourceCopy: File) {
        val backupFile = File(
            File(context.filesDir, "metadata_backups").apply { mkdirs() },
            "${item.bookId.toSafePdfBackupName()}.pdf"
        )
        if (!backupFile.exists()) {
            sourceCopy.inputStream().use { input ->
                backupFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).length() } ?: 0L
        } else {
            DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
        }
    }

    private fun queryLastModified(uri: Uri): Long {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).lastModified() } ?: 0L
        } else {
            DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
        }
    }
}

private fun String.toSafePdfBackupName(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "book" }
}

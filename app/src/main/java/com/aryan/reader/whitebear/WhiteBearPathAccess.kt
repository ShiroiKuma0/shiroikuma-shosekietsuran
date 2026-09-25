package com.aryan.reader.whitebear

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * The second route to 白い熊's own files: all-files access, used wherever a Storage Access
 * Framework grant is what the folder pipeline would normally have asked for.
 *
 * ## Why the app must not depend on the grant (白い熊, 2026-09-25)
 *
 * The folders are deep-frozen between readings — suspended, disabled **and hidden** — and hiding
 * a package makes the system broadcast a per-user package removal for it. Runtime permissions
 * survive that. The all-files app-op survives it. The persisted URI grants do not: they are
 * dropped exactly as they would be on an uninstall, so every unfreeze started with a library
 * pointing at folders this installation was no longer allowed to open, and the grant gate asked
 * for the same folder again, every single day.
 *
 * No app can hold on to a grant the system revokes. So the grant stops being the thing the
 * folder pipeline is built on. A Storage Access Framework document id on the external-storage
 * provider is `volume:relative/path` — a path with a volume in front of it — and with all-files
 * access held that path can be listed, read and written directly. This object is that
 * translation, in one place, so the rest of the app keeps speaking in document URIs.
 *
 * ## The one rule everything here obeys
 *
 * **The document URI stays the identity.** A folder walked by path reports the *same* document
 * ids, and therefore byte-identical document URIs, that the framework would have reported. A
 * library row scanned without a grant is indistinguishable from one scanned with it — no
 * re-import, no duplicate, no rewritten path, and nothing to undo when the grant comes back.
 */
object WhiteBearPathAccess {

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /**
     * True while this build may read 白い熊's own files rather than only its own sandbox —
     * [com.aryan.reader.whitebear.hasAllFilesAccess], named apart so the call inside this object
     * cannot resolve to itself.
     */
    fun allFilesAccessHeld(): Boolean = hasAllFilesAccess()

    /**
     * The document id inside a tree, document or tree-document URI.
     *
     * Read off the path segments rather than through [DocumentsContract], which answers the
     * *tree* id for a tree-document URI — right for comparing folders, wrong for resolving the
     * book inside one. Segments are split before they are decoded, so an encoded `/` inside an
     * id stays where it belongs.
     */
    fun documentIdOf(uri: Uri): String? {
        val segments = uri.pathSegments ?: return null
        val document = segments.indexOf("document")
        if (document >= 0 && segments.size > document + 1) return segments[document + 1]
        if (segments.firstOrNull() == "tree" && segments.size >= 2) return segments[1]
        return null
    }

    /**
     * `primary:〇/[06] 蔵書` → `/storage/emulated/0/〇/[06] 蔵書`.
     *
     * `primary` is shared storage; anything else is a card, mounted under `/storage` by its
     * volume id. A `..` **segment** is refused — a name that merely contains dots is not.
     */
    fun fileForDocumentId(documentId: String): File? {
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        val relative = documentId.substringAfter(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        if (relative.split('/').any { it == ".." }) return null
        @Suppress("DEPRECATION")
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }
        return runCatching { if (relative.isEmpty()) root else File(root, relative) }.getOrNull()
    }

    /**
     * The real file or directory behind [uri], or null when this app has no business resolving
     * one: without all-files access the path is unreadable, and returning it would trade a clear
     * failure for a confusing one.
     */
    fun fileFor(uri: Uri): File? {
        if (!allFilesAccessHeld()) return null
        if (!uri.authority.equals(EXTERNAL_STORAGE, ignoreCase = true)) return null
        val documentId = documentIdOf(uri) ?: return null
        return fileForDocumentId(documentId)
    }

    /** Whether the path route can stand in for a grant on [treeUri] right now. */
    fun covers(treeUri: Uri): Boolean = fileFor(treeUri)?.isDirectory == true

    /**
     * Whether [treeUri] is being served by the path route because the grant is gone — the state
     * this object exists for, worth a log line and nothing else.
     */
    fun standsInFor(context: Context, treeUri: Uri): Boolean =
        !WhiteBearFolderGrants.isCovered(context, treeUri) && covers(treeUri)

    /**
     * A [DocumentFile] for a folder root — through the framework while the grant is held, over
     * the raw filesystem when it is not.
     *
     * Both answer the same small interface (`listFiles`, `findFile`, `createFile`, `renameTo`,
     * `delete`), and a `file://` URI opens through the same `ContentResolver` calls a document
     * URI does, so callers need to know nothing about which one they were handed.
     */
    fun documentTree(context: Context, treeUri: Uri): DocumentFile? {
        if (!treeUri.scheme.equals("content", ignoreCase = true)) return null
        if (WhiteBearFolderGrants.isCovered(context, treeUri)) {
            runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
                ?.let { return it }
        }
        fileFor(treeUri)?.takeIf { it.isDirectory }?.let { return DocumentFile.fromFile(it) }
        // Neither key answered. Hand back the framework's tree anyway, exactly as the callers
        // built it before this object existed: its operations will fail the way they used to,
        // which is never worse than the null they would otherwise read as 「folder gone」.
        return runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
    }

    /**
     * A [DocumentFile] for a single file — the framework's while it will answer for it, the raw
     * one over the same path when it will not.
     *
     * `exists()`, `length()`, `lastModified()` and `delete()` are the whole of what the callers
     * want, and a file the app is allowed to read by path answers all four. Without this, losing
     * the grant turned 「delete this book」 into a silent no-op that the next folder scan undid by
     * importing the file straight back.
     */
    fun document(context: Context, uri: Uri): DocumentFile? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
            return DocumentFile.fromFile(File(path))
        }
        if (!uri.scheme.equals("content", ignoreCase = true)) return null
        if (uri.authority.equals(EXTERNAL_STORAGE, ignoreCase = true) &&
            !WhiteBearFolderGrants.isCovered(context, uri)
        ) {
            fileFor(uri)?.takeIf { it.exists() }?.let { return DocumentFile.fromFile(it) }
        }
        return runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
    }

    /**
     * A URI this app can actually open — for reading or for writing: [uri] untouched while the
     * framework will serve it, the `file://` behind it when only the path route will.
     *
     * Every caller already opens a `file://` URI by path and a `content://` one through the
     * resolver, so this is the whole change at a site that opens a stored URI.
     */
    fun openableUri(context: Context, uri: Uri): Uri {
        if (!uri.scheme.equals("content", ignoreCase = true)) return uri
        if (!uri.authority.equals(EXTERNAL_STORAGE, ignoreCase = true)) return uri
        if (WhiteBearFolderGrants.isCovered(context, uri)) return uri
        val file = fileFor(uri)?.takeIf { it.isFile } ?: return uri
        return Uri.fromFile(file)
    }

    /** `primary:〇/蔵書` + `book.epub` → `primary:〇/蔵書/book.epub`, as the provider forms it. */
    fun childDocumentId(parentDocumentId: String, name: String): String = when {
        parentDocumentId.endsWith(":") || parentDocumentId.endsWith("/") -> parentDocumentId + name
        else -> "$parentDocumentId/$name"
    }

    /** One directory's children, named the way the framework would have named them. */
    data class PathChild(
        val documentId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    /**
     * The children of [parentDocumentId] read straight off the filesystem, or null when the
     * directory cannot be listed — which a caller must treat exactly as it treats a provider
     * that returned no cursor: an incomplete view, never proof that a book is gone.
     */
    fun childrenOf(parentDocumentId: String): List<PathChild>? {
        val directory = readableDirectory(parentDocumentId) ?: return null
        val children = directory.listFiles() ?: return null
        return children.mapNotNull { child ->
            val name = child.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PathChild(
                documentId = childDocumentId(parentDocumentId, name),
                name = name,
                isDirectory = child.isDirectory,
                size = if (child.isFile) child.length().coerceAtLeast(0L) else 0L,
                lastModified = child.lastModified().coerceAtLeast(0L)
            )
        }
    }

    /**
     * The document URIs a library row could be holding for [file] — one per known folder root
     * that contains it.
     *
     * The repair for the other direction of the same translation: a book opened by path arrives
     * back at a save as a `file://` URI, while its row is keyed by the document URI it was
     * scanned under. Rebuilding that URI is exact, so the save lands on the right row instead of
     * quietly finding none.
     */
    fun documentUrisFor(file: File, folderUris: Collection<String>): List<Uri> {
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return emptyList()
        return folderUris.asSequence()
            .mapNotNull { runCatching { it.toUri() }.getOrNull() }
            .filter { it.authority.equals(EXTERNAL_STORAGE, ignoreCase = true) }
            .distinct()
            .mapNotNull { tree ->
                val treeId = documentIdOf(tree) ?: return@mapNotNull null
                val root = fileForDocumentId(treeId)
                    ?.let { runCatching { it.canonicalFile }.getOrNull() }
                    ?: return@mapNotNull null
                val prefix = root.path.trimEnd(File.separatorChar) + File.separator
                val path = target.path
                if (!path.startsWith(prefix)) return@mapNotNull null
                val relative = path.removePrefix(prefix).takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(
                        tree,
                        childDocumentId(treeId, relative)
                    )
                }.getOrNull()
            }
            .distinct()
            .toList()
    }

    /** The directory behind a document id, gated on the permission that makes listing it legal. */
    private fun readableDirectory(documentId: String): File? =
        if (allFilesAccessHeld()) fileForDocumentId(documentId)?.takeIf { it.isDirectory } else null
}

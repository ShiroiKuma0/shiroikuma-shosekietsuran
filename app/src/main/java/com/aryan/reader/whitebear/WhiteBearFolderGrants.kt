package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri

/**
 * Storage Access Framework grants for the folders the library is built on — held, missing, and
 * how to ask for one back.
 *
 * ## Why this exists (白い熊, 2026-09-09)
 *
 * A library row records the folder it came from as a tree URI and the book as a document URI
 * beneath it. Both are just paths in disguise and both survive a backup perfectly. **The grant
 * does not.** Permission to a tree is held by an *installation*, so a restore onto a reinstalled
 * app brings back every book, every reading position and every annotation, and not one of them
 * can be opened — the rows are right, the files are there, and the app is no longer allowed to
 * look. That is the worst shape a restore can take, because nothing about it looks broken.
 *
 * All-files access opens the file by path and is worth having, but it is a way around the
 * problem rather than through it: the folder pipeline — scanning for new books, reading covers
 * and metadata, writing back — is built on the tree grant, so an app leaning on the path is
 * limping. Re-granting is the repair, and it is exact: document ids are path-based, so picking
 * the same folder again produces a **byte-identical** tree URI and every existing row starts
 * working again with nothing rewritten and nothing re-imported.
 */
object WhiteBearFolderGrants {

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** The tree URIs this installation still holds a persisted read permission for. */
    fun heldTrees(context: Context): List<Uri> = runCatching {
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
            .filter { isTree(context, it) }
    }.getOrDefault(emptyList())

    /**
     * The held tree that contains [target], or null when nothing we hold does.
     *
     * Compared as document ids rather than as URI strings: `volume:relative/path`, matched on
     * whole path segments so that `〇/[06] 蔵書` never counts as a parent of `〇/[06] 蔵書別`.
     */
    fun treeCovering(context: Context, target: Uri): Uri? {
        val wanted = documentIdOf(context, target) ?: return null
        return heldTrees(context).firstOrNull { tree ->
            val held = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            held != null && tree.authority == target.authority && isAncestorId(held, wanted)
        }
    }

    /** Whether this install can still reach [target] through the framework that indexed it. */
    fun isCovered(context: Context, target: Uri): Boolean = treeCovering(context, target) != null

    /**
     * Of the folders the library points at, the ones this install no longer holds — in the order
     * they were given, each at most once.
     */
    fun missingGrants(context: Context, sourceFolderUris: Collection<String>): List<Uri> =
        sourceFolderUris.asSequence()
            .mapNotNull { runCatching { it.toUri() }.getOrNull() }
            .filter { it.authority != null }
            .distinct()
            .filterNot { isCovered(context, it) }
            .toList()

    /**
     * Where the picker should open — the folder we are missing, as a document URI.
     *
     * A hint, not a command: AOSP's picker honours it, and one that ignores it simply opens
     * where it likes, which costs 白い熊 some navigating and nothing else. The grant that comes
     * back is exact either way.
     */
    fun initialUriFor(tree: Uri): Uri? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        // The picker wants a *document* URI to land on, not the tree URI itself.
        return runCatching {
            DocumentsContract.buildDocumentUri(tree.authority ?: EXTERNAL_STORAGE, id)
        }.getOrNull()
    }

    /**
     * Keep what the picker just handed back, so it survives the next launch.
     *
     * A grant that is not taken persistably lasts until the process dies, which would turn this
     * whole repair into something 白い熊 has to redo every morning.
     */
    fun persist(context: Context, granted: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            granted,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        true
    }.getOrDefault(false)

    /** `〇/[06] 蔵書/[06][607] 書籍` — the readable half of a tree URI, for telling 白い熊 which. */
    fun displayPathOf(context: Context, tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return tree.toString()
        val relative = id.substringAfter(':', missingDelimiterValue = id)
        return relative.ifEmpty { id }
    }

    private fun isTree(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.isTreeUri(uri) }.getOrElse {
            uri.pathSegments.firstOrNull() == "tree"
        }

    private fun documentIdOf(context: Context, uri: Uri): String? = runCatching {
        when {
            DocumentsContract.isTreeUri(uri) -> DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.getDocumentId(uri)
            else -> null
        }
    }.getOrNull()

    /** True when [candidate] is [ancestor] itself or something beneath it, by whole segments. */
    private fun isAncestorId(ancestor: String, candidate: String): Boolean {
        if (ancestor == candidate) return true
        val volumeA = ancestor.substringBefore(':', missingDelimiterValue = "")
        val volumeB = candidate.substringBefore(':', missingDelimiterValue = "")
        if (volumeA != volumeB) return false
        val pathA = ancestor.substringAfter(':', missingDelimiterValue = "").trimEnd('/')
        val pathB = candidate.substringAfter(':', missingDelimiterValue = "")
        // An empty ancestor path is the volume root and covers everything on it.
        if (pathA.isEmpty()) return true
        return pathB == pathA || pathB.startsWith("$pathA/")
    }
}

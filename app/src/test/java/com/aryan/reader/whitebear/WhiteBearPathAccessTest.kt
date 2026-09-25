package com.aryan.reader.whitebear

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The rule these guard: a folder served by path must report the **same** document ids, and
 * therefore the same document URIs, that the Storage Access Framework would have reported. Break
 * that and a library scanned without a grant re-imports every book it already has.
 */
@RunWith(RobolectricTestRunner::class)
class WhiteBearPathAccessTest {

    private val authority = "com.android.externalstorage.documents"
    private val treeId = "primary:〇/[06] 蔵書/[06][607] 書籍"
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(authority, treeId)

    private fun externalRoot(): File = @Suppress("DEPRECATION") Environment.getExternalStorageDirectory()

    @Test
    fun `document id of a tree uri is the tree id`() {
        assertEquals(treeId, WhiteBearPathAccess.documentIdOf(tree))
    }

    @Test
    fun `document id of a tree-document uri is the document, not the tree`() {
        val bookId = "$treeId/夜の底.epub"
        val book = DocumentsContract.buildDocumentUriUsingTree(tree, bookId)
        assertEquals(bookId, WhiteBearPathAccess.documentIdOf(book))
    }

    @Test
    fun `document id of a plain document uri`() {
        val id = "primary:tmp/a.pdf"
        assertEquals(id, WhiteBearPathAccess.documentIdOf(DocumentsContract.buildDocumentUri(authority, id)))
    }

    @Test
    fun `child document ids are joined the way the provider joins them`() {
        assertEquals("primary:蔵書/a.epub", WhiteBearPathAccess.childDocumentId("primary:蔵書", "a.epub"))
        // A volume root ends at the colon and takes no separator of its own.
        assertEquals("primary:a.epub", WhiteBearPathAccess.childDocumentId("primary:", "a.epub"))
    }

    @Test
    fun `primary ids resolve under shared storage and card ids under storage`() {
        assertEquals(
            File(externalRoot(), "〇/[06] 蔵書").path,
            WhiteBearPathAccess.fileForDocumentId("primary:〇/[06] 蔵書")?.path
        )
        assertEquals(
            "/storage/1A2B-3C4D/books",
            WhiteBearPathAccess.fileForDocumentId("1A2B-3C4D:books")?.path
        )
        assertEquals(externalRoot().path, WhiteBearPathAccess.fileForDocumentId("primary:")?.path)
    }

    @Test
    fun `a parent segment is refused and a dotted name is not`() {
        assertNull(WhiteBearPathAccess.fileForDocumentId("primary:蔵書/../secrets"))
        assertNull(WhiteBearPathAccess.fileForDocumentId("no-volume"))
        assertTrue(
            WhiteBearPathAccess.fileForDocumentId("primary:蔵書/第..二章.epub")?.path
                ?.endsWith("第..二章.epub") == true
        )
    }

    @Test
    fun `a path rebuilds the exact document uri its row was scanned under`() {
        val book = File(externalRoot(), "〇/[06] 蔵書/[06][607] 書籍/雨月物語.epub")
        val rebuilt = WhiteBearPathAccess.documentUrisFor(book, listOf(tree.toString()))
        val expected = DocumentsContract.buildDocumentUriUsingTree(tree, "$treeId/雨月物語.epub")
        assertEquals(listOf(expected), rebuilt)
        // Byte-identical, not merely equivalent: the row is matched by string.
        assertEquals(expected.toString(), rebuilt.single().toString())
    }

    @Test
    fun `a nested path keeps its subdirectories`() {
        val book = File(externalRoot(), "〇/[06] 蔵書/[06][607] 書籍/上田秋成/雨月物語.epub")
        val rebuilt = WhiteBearPathAccess.documentUrisFor(book, listOf(tree.toString()))
        assertEquals(
            DocumentsContract.buildDocumentUriUsingTree(tree, "$treeId/上田秋成/雨月物語.epub"),
            rebuilt.single()
        )
    }

    @Test
    fun `a file outside every known root rebuilds nothing`() {
        val outside = File(externalRoot(), "〇/[07] 別/x.epub")
        assertEquals(emptyList<Uri>(), WhiteBearPathAccess.documentUrisFor(outside, listOf(tree.toString())))
    }

    @Test
    fun `a sibling root whose name merely starts the same is not a parent`() {
        val sibling = DocumentsContract.buildTreeDocumentUri(authority, "primary:〇/[06] 蔵書/[06][607] 書籍別")
        val book = File(externalRoot(), "〇/[06] 蔵書/[06][607] 書籍別/x.epub")
        val rebuilt = WhiteBearPathAccess.documentUrisFor(book, listOf(tree.toString(), sibling.toString()))
        assertEquals(
            listOf(DocumentsContract.buildDocumentUriUsingTree(sibling, "primary:〇/[06] 蔵書/[06][607] 書籍別/x.epub")),
            rebuilt
        )
    }

    @Test
    fun `a folder that is not the external-storage provider is left alone`() {
        val foreign = "content://com.example.provider/tree/whatever".toUri()
        assertEquals(
            emptyList<Uri>(),
            WhiteBearPathAccess.documentUrisFor(File(externalRoot(), "x.epub"), listOf(foreign.toString()))
        )
    }
}

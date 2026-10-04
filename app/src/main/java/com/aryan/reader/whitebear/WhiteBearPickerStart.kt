package com.aryan.reader.whitebear

import android.content.Intent
import android.provider.DocumentsContract

/**
 * Keeps the system file picker from crashing on 白い熊's phone.
 *
 * ## Why (白い熊, 2026-10-04: “Save a copy” crashes the app)
 *
 * The phone's DocumentsUI (Huawei build 42.0.0.6) opens every picker without an initial location
 * at the *last accessed stack* it remembers for the calling package. When that remembered stack
 * is non-null but empty, `ActionHandler.onLastAccessedStackLoaded` calls `stack.peek().derivedUri`
 * on a null document and the picker dies with a NullPointerException in `PickActivity.onCreate`.
 * The picker runs in our task, so its death drops the reader back to the launcher — which reads
 * as the app itself crashing. Every SAF picker the app opens takes the same path, so “Save a
 * copy” was only the first place it showed.
 *
 * The picker consults the last accessed stack only after `EXTRA_INITIAL_URI` has failed to
 * resolve. A *root* URI resolves without touching a document at all, so handing it the primary
 * storage root skips the broken code entirely. Intents that already carry an initial location
 * (the folder-grant gate) keep theirs.
 */
private val PICKER_ACTIONS = setOf(
    Intent.ACTION_CREATE_DOCUMENT,
    Intent.ACTION_OPEN_DOCUMENT,
    Intent.ACTION_OPEN_DOCUMENT_TREE,
    Intent.ACTION_GET_CONTENT,
)

private val PRIMARY_STORAGE_ROOT =
    DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")

fun Intent.withWhiteBearPickerStart(): Intent {
    if (action !in PICKER_ACTIONS || hasExtra(DocumentsContract.EXTRA_INITIAL_URI)) return this
    return putExtra(DocumentsContract.EXTRA_INITIAL_URI, PRIMARY_STORAGE_ROOT)
}

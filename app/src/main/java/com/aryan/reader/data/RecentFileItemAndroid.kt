package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.aryan.reader.whitebear.WhiteBearPathAccess

fun RecentFileItem.getUri(): Uri? = uriString?.toUri()

/**
 * The URI to hand a reader — [getUri] while the framework will open it, the path behind it when
 * the folder's grant is gone and all-files access still reaches the book (白い熊, 2026-09-25).
 *
 * Kept apart from [getUri] on purpose: the stored URI is the book's **identity**, written into
 * the library row and compared against it, and that must not change with the state of a
 * permission. This one is only ever the answer to 「what do I open?」.
 */
fun RecentFileItem.getOpenableUri(context: Context): Uri? =
    getUri()?.let { WhiteBearPathAccess.openableUri(context, it) }

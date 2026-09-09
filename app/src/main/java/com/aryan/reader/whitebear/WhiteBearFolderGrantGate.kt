package com.aryan.reader.whitebear

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aryan.reader.R

/**
 * 「This library points at folders you have not granted access to」 — asked once, at start,
 * before a single book can be tapped.
 *
 * ## Why at start, and not when a book fails to open (白い熊, 2026-09-09)
 *
 * Because at the moment a book fails there is nothing good left to do. The library has already
 * drawn, complete and convincing; the failure arrives as one book behaving oddly rather than as
 * what it is, which is *the whole library being unreachable*. Worse, the app used to answer that
 * failure by deleting the row — see `MainViewModel.canReach` — so the first thing a restored
 * copy did was start eating itself, one tap at a time.
 *
 * Asking at start turns a permanent, silent, per-book data loss into one question with one
 * answer. And the answer is exact rather than approximate: re-picking the same folder yields a
 * byte-identical tree URI, so every row that pointed at it works again untouched.
 *
 * The path fallback in `MainViewModel.externalStorageFileFor` stays where it is, demoted to what
 * it always was — a way to open a file when the proper route is unavailable, not a substitute
 * for the grant the folder machinery is built on.
 */
@Composable
fun WhiteBearFolderGrantGate(
    missingFolders: suspend () -> List<Uri>,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf<List<Uri>?>(null) }
    // Not remembered across launches: a folder 白い熊 declined today may be one they want back
    // tomorrow, and the cost of asking again is one glance.
    var skipped by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { granted ->
        if (granted != null) WhiteBearFolderGrants.persist(context, granted)
        // Re-ask either way: a cancelled pick leaves the list as it was, and a successful one
        // may have covered more than the folder it was opened for.
        reload++
    }

    LaunchedEffect(reload) {
        missing = runCatching { missingFolders() }.getOrDefault(emptyList())
    }

    val pending = missing
    // Null means the sweep has not answered yet. Showing the library underneath meanwhile is
    // deliberate: the check reads the library table, and a splash in front of a working app is a
    // worse trade than a prompt arriving a moment late.
    if (skipped || pending == null || pending.isEmpty()) {
        content()
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.whitebear_folder_grant_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.whitebear_folder_grant_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            pending.forEach { folder ->
                Text(
                    text = WhiteBearFolderGrants.displayPathOf(context, folder),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }
            Button(onClick = {
                val next = pending.firstOrNull() ?: return@Button
                // The contract puts this in as EXTRA_INITIAL_URI, so the picker opens at the
                // folder we are missing instead of wherever it happened to be last.
                runCatching { picker.launch(WhiteBearFolderGrants.initialUriFor(next)) }
                    .onFailure { reload++ }
            }) {
                Text(stringResource(R.string.whitebear_folder_grant_grant))
            }
            TextButton(onClick = { skipped = true }) {
                Text(stringResource(R.string.whitebear_folder_grant_skip))
            }
        }
    }
}

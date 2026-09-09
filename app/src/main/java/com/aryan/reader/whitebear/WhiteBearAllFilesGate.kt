package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aryan.reader.R

/**
 * All-files access, asked for **before the library is shown** rather than at the moment a book
 * fails to open.
 *
 * ## Why this exists (白い熊, 2026-09-09)
 *
 * A restored install is the case that makes the old ordering untenable. 応用管理 reinstalls the
 * APK and hands this app its data back, and the data comes back complete — every book, every
 * reading position, every annotation. What does **not** come back is permission: an install is a
 * new app as far as the framework is concerned, so all-files access is off and every persisted
 * SAF grant is gone. The library therefore draws perfectly and every single book in it is
 * unreadable, which is the most misleading state this app can be in — it looks restored.
 *
 * Worse, it used to be actively destructive: tapping a book asked 「is this file there?」, got
 * 「no」 from a framework that was really saying 「you may not ask」, and deleted the row. See
 * `MainViewModel.canReach`, which is the other half of this fix and the one that stops the
 * damage; this half stops the situation arising.
 *
 * ## Why it can still be skipped
 *
 * 白い熊 asked for it to be requested before anything else, and it is — the gate is what the app
 * opens on, and the library is not reachable behind it. But a permission screen with no way past
 * is a way for an app to become unopenable on a device or ROM that will not grant it, and an
 * e-book reader that cannot be opened is worse than one with an unreadable library. So the skip
 * is there, deliberately quiet, and lasts only for this launch: it is not remembered, so the next
 * start asks again.
 */
@Composable
fun WhiteBearAllFilesGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
    // Survives the configuration changes a fold or a rotation causes, so answering the gate and
    // then unfolding the phone does not put it back in the way.
    var skipped by rememberSaveable { mutableStateOf(false) }

    // Re-checked on every resume, because the grant is given in Settings and the only sign of it
    // is coming back to this activity. Without this the gate would still be standing after 白い熊
    // had already granted what it asked for.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasAllFilesAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted || skipped) {
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
                text = stringResourceOrFallback(
                    context, R.string.whitebear_all_files_gate_title,
                    "All-files access is needed"
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResourceOrFallback(
                    context, R.string.whitebear_all_files_gate_body,
                    "Your library is stored on this device, and this app cannot read a single " +
                        "book without permission to see files. Grant it now — otherwise every " +
                        "book will look missing."
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Button(onClick = { openAllFilesAccessSettings(context) }) {
                Text(
                    stringResourceOrFallback(
                        context, R.string.whitebear_all_files_gate_grant, "Grant access"
                    )
                )
            }
            TextButton(onClick = { skipped = true }) {
                Text(
                    stringResourceOrFallback(
                        context, R.string.whitebear_all_files_gate_skip, "Not now"
                    )
                )
            }
        }
    }
}

/** True when this build may read 白い熊's own files rather than only its own sandbox. */
fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

/**
 * The per-app page first, the global list as a fallback: EMUI has been known to refuse the
 * targeted intent, and a button that does nothing is worse than one extra tap.
 */
fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val direct = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri()
    )
    runCatching { context.startActivity(direct) }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

/** A string that may not have been added to every locale yet, without crashing the gate. */
private fun stringResourceOrFallback(context: Context, id: Int, fallback: String): String =
    runCatching { context.getString(id) }.getOrDefault(fallback)

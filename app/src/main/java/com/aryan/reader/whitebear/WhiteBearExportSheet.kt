package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Warning color for the unset-directory / no-export states, as in the sister forks. */
private val WarnColor = Color(0xFFFF5252)

/**
 * Where one export is going, in the three steps that keep a half-written one from being mistaken
 * for a backup: [open] the file it streams into, [place] it under the name a backup really wears
 * once the archive is closed and whole, and [discard] whatever is there when it never gets that
 * far. Only [place] may leave a file behind. A backup written by hand is interrupted exactly as
 * easily as an automated one, and 白い熊 cannot tell from the directory listing which is which —
 * see [WhiteBearExport.PART_SUFFIX].
 */
private class Destination(
    val open: () -> OutputStream?,
    /** Where the finished archive ended up, for the result dialog — null if it could not be placed. */
    val place: () -> String?,
    val discard: () -> Unit
)

/**
 * 白い熊 export/import panel — same idea and flow as the sister forks: a bordered box
 * with the persisted export directory (tap to choose via SAF), the last-export line,
 * a category checklist, and the pill button row (Cancel alone on the left, Import and
 * Export grouped on the right).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteBearExportImportSheet(
    onDismiss: () -> Unit,
    /** Called after a successful export/import is acknowledged — closes panel AND page. */
    onFinished: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dirName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("" to false) }
    val checks = remember {
        mutableStateMapOf<WhiteBearExport.Cat, Boolean>().apply {
            WhiteBearExport.Cat.entries.forEach { put(it, it.defaultSelected) }
        }
    }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf("") }
    var offerRestart by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }

    // Import is chosen from the archive, not from the catalogue: once a file is picked it is
    // read for what it really holds, and only that is offered — everything in it ticked, and a
    // category that is not in there not shown at all, so the list can never promise a restore
    // the file cannot deliver.
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importName by remember { mutableStateOf("") }
    val importChecks = remember { mutableStateMapOf<WhiteBearExport.Cat, Boolean>() }
    var busyText by remember { mutableStateOf<String?>(null) }

    fun selectedCats(): Set<WhiteBearExport.Cat> =
        checks.filterValues { it }.keys.toSet()

    fun failed(what: String) {
        resultTitle = "Import"
        resultText = what
        offerRestart = false
        resultSuccess = false
    }

    suspend fun refresh() {
        val (name, st) = withContext(Dispatchers.IO) {
            WhiteBearExport.dirDisplayName(context) to WhiteBearExport.lastExportStatus(context)
        }
        dirName = name
        status = st
    }
    LaunchedEffect(Unit) { refresh() }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            WhiteBearExport.setDirUri(context, uri)
            scope.launch { refresh() }
        }
    }

    fun exportTo(destination: Destination) {
        val cats = selectedCats()
        if (cats.isEmpty()) {
            resultTitle = "Export"
            resultText = "No categories selected."
            offerRestart = false
            resultSuccess = false
            return
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val out = destination.open() ?: error("Cannot open the output file.")
                    val written = out.use { WhiteBearExport.export(context, cats, it) }
                    written to (destination.place() ?: error("Cannot put the finished file in place."))
                    // Anything that stopped us short of a placed file — including failing to
                    // place it — takes the half-written archive away with it.
                }.onFailure { destination.discard() }
            }
            refresh()
            resultTitle = "Export"
            resultText = outcome.fold(
                onSuccess = { (written, where) ->
                    // A backup that had to give up on something says so here too — a partial
                    // one is only safe to keep if it is visibly partial.
                    buildString {
                        append("Exported ${written.summary} to $where.")
                        if (written.skipped.isNotEmpty()) {
                            append("\n\nSkipped:\n")
                            append(written.skipped.take(10).joinToString("\n"))
                            if (written.skipped.size > 10) {
                                append("\n… and ${written.skipped.size - 10} more")
                            }
                        }
                    }
                },
                onFailure = { "Export failed: ${it.message}" }
            )
            offerRestart = false
            resultSuccess = outcome.isSuccess
        }
    }

    /** The configured export directory: a `.part` beside the backups, renamed onto its name at the end. */
    fun directoryDestination(dir: DocumentFile, name: String): Destination {
        var part: DocumentFile? = null
        val shown = "${dirName ?: "the export directory"}/$name"
        return Destination(
            open = {
                WhiteBearExport.sweepStaleParts(dir)
                val created = WhiteBearExport.createPart(dir, name) ?: error("Cannot create the file.")
                part = created
                context.contentResolver.openOutputStream(created.uri)
            },
            place = {
                val created = part
                if (created != null && created.renameTo(name)) shown else null
            },
            discard = { runCatching { part?.delete() } }
        )
    }

    /**
     * A location 白い熊 picked by hand. The system's picker creates the file under its final name
     * before a byte is written, so keeping that name off a half-written archive means moving the
     * file aside first and moving it back once the archive is whole. A provider that will not
     * rename gets the plain write — and either way, a run that fails takes the file with it
     * instead of leaving a corpse under a backup's name.
     */
    fun chosenFileDestination(uri: Uri): Destination {
        var writing = uri
        var shown = uri.lastPathSegment?.substringAfterLast('/') ?: "the chosen file"
        return Destination(
            open = {
                // Here rather than in the picker callback: every line of this talks to the
                // provider, and the callback runs on the main thread.
                val name = DocumentFile.fromSingleUri(context, uri)?.name?.takeIf { it.isNotBlank() }
                if (name != null) {
                    shown = name
                    writing = WhiteBearExport.renameDocument(context, uri, WhiteBearExport.partName(name))
                        ?: uri
                }
                context.contentResolver.openOutputStream(writing)
            },
            place = {
                // Unmoved means it is already under the name 白い熊 chose, and there is nothing
                // to put back.
                if (writing == uri) shown
                else WhiteBearExport.renameDocument(context, writing, shown)?.let { shown }
            },
            discard = { WhiteBearExport.deleteDocument(context, writing) }
        )
    }

    val exportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) exportTo(chosenFileDestination(uri)) }

    fun openImport(uri: Uri) = context.contentResolver.openInputStream(uri)
        ?: error("Cannot read the file.")

    /** Step one: find out what the picked archive holds, and offer exactly that. */
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busyText = "Reading the archive…"
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    runCatching { WhiteBearExport.categoriesIn { openImport(uri) } }
                }
                busyText = null
                found.fold(
                    onSuccess = { cats ->
                        if (cats.isEmpty()) {
                            failed("There is nothing this app can import in that file.")
                        } else {
                            importUri = uri
                            importName = uri.lastPathSegment?.substringAfterLast('/') ?: "the chosen file"
                            importChecks.clear()
                            cats.forEach { importChecks[it] = true }
                        }
                    },
                    onFailure = { failed("Cannot read that file: ${it.message}") }
                )
            }
        }
    }

    /** Step two: restore what is still ticked of it. */
    fun onImport() {
        val uri = importUri ?: return
        val cats = importChecks.filterValues { it }.keys.toSet()
        if (cats.isEmpty()) {
            failed("No categories selected.")
            return
        }
        busyText = "Importing…"
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { WhiteBearExport.import(context, { openImport(uri) }, cats) }
            }
            busyText = null
            importUri = null
            resultTitle = "Import"
            resultText = outcome.fold(
                onSuccess = { "$it\n\nRestart the app so every imported setting takes effect." },
                onFailure = { "Import failed: ${it.message}" }
            )
            offerRestart = outcome.isSuccess
            resultSuccess = outcome.isSuccess
        }
    }

    fun onExport() {
        val dir = WhiteBearExport.exportDir(context)
        val name = WhiteBearExport.exportFileName()
        if (dir != null) exportTo(directoryDestination(dir, name)) else exportSaver.launch(name)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open at full height straight away — the panel is taller than the half-expanded
        // stop, which would otherwise cut it off until a second upward swipe.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .verticalScroll(rememberScrollState())
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text(
                "Export / Import",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)
            )
            Text(
                "Export or import everything the app holds — 白い熊 UI, gestures, reader settings, " +
                    "and the book library with its annotations and covers — by category, as one ZIP.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Persisted export directory — a bordered, clearly-tappable box, warn-red when unset.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .clickable { dirPicker.launch(WhiteBearExport.dirUri(context)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "Export directory (tap to choose)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    dirName ?: "Not set — tap to choose a directory",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dirName == null) WarnColor else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                status.first,
                fontSize = 14.sp,
                color = if (status.second) WarnColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))

            when {
                busyText != null -> Text(
                    busyText.orEmpty(),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )

                // Importing: the archive's own contents, all taken unless unticked.
                importUri != null -> {
                    Text(
                        "From $importName",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
                    )
                    CheckRow(
                        label = "Select all",
                        checked = importChecks.values.all { it },
                        bold = true,
                        onToggle = { value ->
                            importChecks.keys.toList().forEach { importChecks[it] = value }
                        }
                    )
                    val present = WhiteBearExport.Cat.entries.filter { importChecks.containsKey(it) }
                    present.forEach { cat ->
                        CheckRow(
                            label = cat.label,
                            checked = importChecks[cat] == true,
                            // Indented only under a parent the archive also holds — an orphan
                            // sub-option stands on its own.
                            indent = if (present.any { it.id == cat.parentId }) 28.dp else 0.dp,
                            onToggle = { importChecks[cat] = it }
                        )
                    }
                }

                else -> {
                    CheckRow(
                        label = "Select all",
                        checked = checks.values.all { it },
                        bold = true,
                        onToggle = { value -> WhiteBearExport.Cat.entries.forEach { checks[it] = value } }
                    )
                    // Top-level categories, each followed by its indented sub-options; toggling
                    // a parent carries its children with it, and each child stays selectable
                    // alone.
                    WhiteBearExport.Cat.entries.filter { it.parentId == null }.forEach { cat ->
                        CheckRow(
                            label = cat.label,
                            checked = checks[cat] == true,
                            onToggle = { value ->
                                checks[cat] = value
                                cat.children.forEach { checks[it] = value }
                            }
                        )
                        cat.children.forEach { child ->
                            CheckRow(
                                label = child.label,
                                checked = checks[child] == true,
                                indent = 28.dp,
                                onToggle = { checks[child] = it }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 8.dp)
            )

            // ArcaneChat-style dialog button row: round pills, Cancel alone on the left,
            // the Import / Export actions grouped on the right.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (importUri != null) {
                    PillButton("Back") { importUri = null }
                    Spacer(Modifier.weight(1f))
                    PillButton("Import") { onImport() }
                } else {
                    PillButton("Cancel") { onDismiss() }
                    Spacer(Modifier.weight(1f))
                    PillButton("Import") {
                        importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Export") { onExport() }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    resultTitle?.let { title ->
        // Acknowledging a successful export/import closes the whole chain: this info
        // dialog, the Export/Import panel underneath, and the UI settings page.
        fun acknowledge() {
            resultTitle = null
            if (resultSuccess) onFinished()
        }
        AlertDialog(
            onDismissRequest = { acknowledge() },
            title = { Text(title) },
            text = { Text(resultText) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)),
            confirmButton = {
                if (offerRestart) {
                    TextButton(onClick = { restartApp(context) }) { Text("Restart now") }
                } else {
                    TextButton(onClick = { acknowledge() }) { Text("OK") }
                }
            },
            dismissButton = if (offerRestart) {
                { TextButton(onClick = { acknowledge() }) { Text("Later") } }
            } else null
        )
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    bold: Boolean = false,
    indent: Dp = 0.dp,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Fully-rounded outline pill: surface fill, thin accent stroke, accent text. */
@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary)
    }
}

/** Relaunch the app so freshly imported prefs are re-read by every settings singleton. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Warning color for the unset-directory / no-export states, as in the sister forks. */
private val WarnColor = Color(0xFFFF5252)

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
            WhiteBearExport.Cat.entries.forEach { put(it, true) }
        }
    }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf("") }
    var offerRestart by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }

    fun selectedCats(): Set<WhiteBearExport.Cat> =
        checks.filterValues { it }.keys.toSet()

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

    fun exportTo(open: () -> java.io.OutputStream?, shownTarget: String) {
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
                    val out = open() ?: error("Cannot open the output file.")
                    out.use { WhiteBearExport.export(context, cats, it) }
                }
            }
            refresh()
            resultTitle = "Export"
            resultText = outcome.fold(
                onSuccess = { "Exported $it to $shownTarget." },
                onFailure = { "Export failed: ${it.message}" }
            )
            offerRestart = false
            resultSuccess = outcome.isSuccess
        }
    }

    val exportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            exportTo({ context.contentResolver.openOutputStream(uri) }, uri.lastPathSegment ?: "the chosen file")
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val cats = selectedCats()
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Cannot read the file.")
                        WhiteBearExport.import(context, bytes, cats)
                    }
                }
                resultTitle = "Import"
                resultText = outcome.fold(
                    onSuccess = { "$it\n\nRestart the app so every imported setting takes effect." },
                    onFailure = { "Import failed: ${it.message}" }
                )
                offerRestart = outcome.isSuccess
                resultSuccess = outcome.isSuccess
            }
        }
    }

    fun onExport() {
        val dir = WhiteBearExport.exportDir(context)
        val name = WhiteBearExport.exportFileName(BuildConfig.VERSION_NAME)
        if (dir != null) {
            exportTo(
                {
                    val file = dir.createFile("application/zip", name) ?: error("Cannot create the file.")
                    context.contentResolver.openOutputStream(file.uri)
                },
                "${dirName ?: "the export directory"}/$name"
            )
        } else {
            exportSaver.launch(name)
        }
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
                "Export or import every setting in the app — 白い熊 UI, gestures, library, reader — by category.",
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

            val allChecked = checks.values.all { it }
            CheckRow(
                label = "Select all",
                checked = allChecked,
                bold = true,
                onToggle = { value -> WhiteBearExport.Cat.entries.forEach { checks[it] = value } }
            )
            WhiteBearExport.Cat.entries.forEach { cat ->
                CheckRow(
                    label = cat.label,
                    checked = checks[cat] == true,
                    onToggle = { checks[cat] = it }
                )
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
                PillButton("Cancel") { onDismiss() }
                Spacer(Modifier.weight(1f))
                PillButton("Import") { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                Spacer(Modifier.width(8.dp))
                PillButton("Export") { onExport() }
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
private fun CheckRow(label: String, checked: Boolean, bold: Boolean = false, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
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

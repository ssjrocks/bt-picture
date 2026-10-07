package com.spl1nt.snaplabel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spl1nt.snaplabel.update.UpdateChecker
import com.spl1nt.snaplabel.update.UpdateInfo
import kotlinx.coroutines.launch

/** Shown when [UpdateChecker] finds a newer release; walks through download -> install. */
@Composable
fun UpdateAvailableDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf("idle") } // idle | downloading | installing | failed
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (phase != "downloading") onDismiss() },
        title = { Text("Update available: ${info.tagName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (info.notes.isNotBlank()) {
                    Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                when (phase) {
                    "downloading" -> { CircularProgressIndicator(); Spacer(Modifier.height(4.dp)); Text("Downloading…") }
                    "failed" -> Text("Update failed: ${error ?: "unknown error"}", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = phase != "downloading",
                onClick = {
                    phase = "downloading"
                    scope.launch {
                        val id = UpdateChecker.startDownload(context, info)
                        val ok = UpdateChecker.awaitDownload(context, id)
                        if (ok) {
                            UpdateChecker.installDownloaded(context, id)
                            onDismiss()
                        } else {
                            phase = "failed"
                            error = "download did not complete"
                        }
                    }
                },
            ) { Text(if (phase == "downloading") "Downloading…" else "Update") }
        },
        dismissButton = {
            TextButton(enabled = phase != "downloading", onClick = onDismiss) { Text("Later") }
        },
    )
}

package com.spl1nt.snaplabel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spl1nt.snaplabel.printer.BlePrinterManager
import com.spl1nt.snaplabel.printer.PrinterRepository
import com.spl1nt.snaplabel.update.UpdateChecker
import com.spl1nt.snaplabel.update.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun PrinterScreen(repo: PrinterRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val found by repo.foundDevices.collectAsState()
    val scanning by repo.isScanning.collectAsState()
    val connState by repo.ble.state.collectAsState()
    val rememberedName by repo.rememberedName.collectAsState(initial = null)
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        repo.startScan()
        onDispose { repo.stopScan() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Connect printer") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            val statusText = when (val s = connState) {
                is BlePrinterManager.State.Connected -> "Connected" + (rememberedName?.let { " to $it" } ?: "")
                is BlePrinterManager.State.Connecting -> "Connecting…"
                is BlePrinterManager.State.Failed -> "Connection failed: ${s.message}"
                else -> "Not connected"
            }
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("Nearby Bluetooth devices", style = MaterialTheme.typography.labelLarge)

            LazyColumn(Modifier.weight(1f)) {
                items(found) { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.address) },
                        trailingContent = {
                            TextButton(onClick = {
                                repo.stopScan()
                                repo.bond(device)
                                repo.connect(device)
                                scope.launch { repo.remember(device) }
                            }) { Text("Connect") }
                        },
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = !checkingUpdate,
                onClick = {
                    checkingUpdate = true
                    updateStatus = null
                    scope.launch {
                        val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).let {
                            @Suppress("DEPRECATION") it.versionCode
                        }
                        val result = UpdateChecker.checkForUpdate(currentCode)
                        checkingUpdate = false
                        result.fold(
                            onSuccess = { info ->
                                if (info != null) availableUpdate = info
                                else updateStatus = "You're on the latest version."
                            },
                            onFailure = { e -> updateStatus = "Couldn't check for updates: ${e.message}" },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (checkingUpdate) "Checking…" else "Check for updates") }
            updateStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }

    availableUpdate?.let { info ->
        UpdateAvailableDialog(info = info, onDismiss = { availableUpdate = null })
    }
}

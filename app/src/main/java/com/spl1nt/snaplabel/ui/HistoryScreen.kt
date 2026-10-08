package com.spl1nt.snaplabel.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spl1nt.snaplabel.printer.BlePrinterManager
import com.spl1nt.snaplabel.printer.ImageDitherer
import com.spl1nt.snaplabel.printer.PrinterProtocol
import com.spl1nt.snaplabel.printer.PrinterRepository
import com.spl1nt.snaplabel.util.PrintHistory
import kotlinx.coroutines.launch
import java.io.File

/** Thumbnails of everything printed so far, with a tap to view full-size and reprint or delete. */
@Composable
fun HistoryScreen(repo: PrinterRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(PrintHistory.list(context)) }
    var viewing by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") } },
            )
        },
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing printed yet — your labels will show up here.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(files) { file ->
                    val bmp = remember(file) { BitmapFactory.decodeFile(file.absolutePath) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = file.name,
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(PrinterProtocol.LABEL_WIDTH_DOTS.toFloat() / PrinterProtocol.LABEL_HEIGHT_DOTS)
                                .clickable { viewing = file },
                        )
                    }
                }
            }
        }
    }

    viewing?.let { file ->
        val connState by repo.ble.state.collectAsState()
        var status by remember(file) { mutableStateOf<String?>(null) }
        var sending by remember(file) { mutableStateOf(false) }
        val bmp = remember(file) { BitmapFactory.decodeFile(file.absolutePath) }

        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("Reprint this label?") },
            text = {
                Column {
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(PrinterProtocol.LABEL_WIDTH_DOTS.toFloat() / PrinterProtocol.LABEL_HEIGHT_DOTS),
                        )
                    }
                    if (connState !is BlePrinterManager.State.Connected) {
                        Spacer(Modifier.height(8.dp))
                        Text("Connect the printer first to reprint.")
                    }
                    status?.let { Spacer(Modifier.height(8.dp)); Text(it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = bmp != null && connState is BlePrinterManager.State.Connected && !sending,
                    onClick = {
                        val b = bmp ?: return@TextButton
                        sending = true
                        status = null
                        scope.launch {
                            // Already a pure black & white, label-sized image — just re-pack it, no need to dither again.
                            val packed = ImageDitherer.dither(b, 0, 0)
                            val result = repo.ble.print(PrinterProtocol.bitmapCommand(listOf(packed)))
                            sending = false
                            status = result.fold(
                                onSuccess = { "Sent to printer." },
                                onFailure = { e -> "Failed: ${e.message}" },
                            )
                        }
                    },
                ) { Text(if (sending) "Sending…" else "Reprint") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        PrintHistory.delete(file)
                        files = PrintHistory.list(context)
                        viewing = null
                    }) { Text("Delete") }
                    TextButton(onClick = { viewing = null }) { Text("Close") }
                }
            },
        )
    }
}

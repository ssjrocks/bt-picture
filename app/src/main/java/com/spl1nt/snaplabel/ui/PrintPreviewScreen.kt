package com.spl1nt.snaplabel.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.spl1nt.snaplabel.printer.BlePrinterManager
import com.spl1nt.snaplabel.printer.ImageDitherer
import com.spl1nt.snaplabel.printer.PackedBitmap
import com.spl1nt.snaplabel.printer.PrinterProtocol
import com.spl1nt.snaplabel.printer.PrinterRepository
import kotlinx.coroutines.launch

/**
 * Shows exactly what will be sent to the printer — dithered to 1-bit the same
 * way [ImageDitherer] does it for real — before committing to a print. The
 * printer gives no feedback of its own, so this simulated preview is the only
 * confirmation available before the label is actually made.
 */
@Composable
fun PrintPreviewScreen(
    photo: Bitmap,
    repo: PrinterRepository,
    onDone: () -> Unit,
    onConnectPrinter: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val connState by repo.ble.state.collectAsState()
    var brightness by remember { mutableFloatStateOf(1.12f) }
    var contrast by remember { mutableFloatStateOf(1.15f) }
    var status by remember { mutableStateOf<String?>(null) }
    var printing by remember { mutableStateOf(false) }

    val fitted = remember(photo) { fitToLabel(photo) }
    val dithered = remember(fitted, brightness, contrast) {
        ImageDitherer.dither(fitted.bitmap, fitted.x, fitted.y, brightness, contrast)
    }
    val previewBitmap = remember(dithered) {
        renderPreview(dithered, PrinterProtocol.LABEL_WIDTH_DOTS, PrinterProtocol.LABEL_HEIGHT_DOTS)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Print preview") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "This is a simulation of what the label will look like — the printer confirms nothing on its own.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Print preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PrinterProtocol.LABEL_WIDTH_DOTS.toFloat() / PrinterProtocol.LABEL_HEIGHT_DOTS),
            )
            Spacer(Modifier.height(16.dp))
            Text("Brightness")
            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.7f..1.6f)
            Text("Contrast")
            Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.7f..1.8f)
            Spacer(Modifier.height(12.dp))

            if (connState !is BlePrinterManager.State.Connected) {
                OutlinedButton(onClick = onConnectPrinter, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to printer first")
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                enabled = connState is BlePrinterManager.State.Connected && !printing,
                onClick = {
                    printing = true
                    status = null
                    val bytes = PrinterProtocol.bitmapCommand(listOf(dithered))
                    scope.launch {
                        val result = repo.ble.print(bytes)
                        printing = false
                        status = result.fold(
                            onSuccess = { s -> "Sent ${s.bytes}B in ${s.chunks} chunks over ${s.elapsedMs}ms (MTU ${s.mtu})." },
                            onFailure = { e -> "Failed: ${e.message}" },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (printing) "Sending…" else "Print") }

            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

private data class FittedPhoto(val bitmap: Bitmap, val x: Int, val y: Int)

/** Scale+crop [src] to exactly fill the printable label area, centered. */
private fun fitToLabel(src: Bitmap): FittedPhoto {
    val targetW = PrinterProtocol.LABEL_WIDTH_DOTS - PrinterProtocol.MARGIN_DOTS * 2
    val targetH = PrinterProtocol.LABEL_HEIGHT_DOTS - PrinterProtocol.MARGIN_DOTS * 2
    val srcRatio = src.width.toFloat() / src.height
    val targetRatio = targetW.toFloat() / targetH
    val cw: Int
    val ch: Int
    if (srcRatio > targetRatio) {
        ch = src.height
        cw = (src.height * targetRatio).toInt().coerceAtMost(src.width)
    } else {
        cw = src.width
        ch = (src.width / targetRatio).toInt().coerceAtMost(src.height)
    }
    val cx = ((src.width - cw) / 2).coerceAtLeast(0)
    val cy = ((src.height - ch) / 2).coerceAtLeast(0)
    val cropped = Bitmap.createBitmap(src, cx, cy, cw, ch)
    val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    return FittedPhoto(scaled, PrinterProtocol.MARGIN_DOTS, PrinterProtocol.MARGIN_DOTS)
}

private fun renderPreview(bmp: PackedBitmap, labelW: Int, labelH: Int): Bitmap {
    val out = Bitmap.createBitmap(labelW, labelH, Bitmap.Config.ARGB_8888)
    out.eraseColor(AndroidColor.WHITE)
    for (row in 0 until bmp.heightDots) {
        val packedRow = bmp.rows[row]
        for (col in 0 until bmp.widthDots) {
            val byte = packedRow[col / 8].toInt()
            val bit = (byte ushr (7 - (col % 8))) and 1
            if (bit == 1) out.setPixel(bmp.x + col, bmp.y + row, AndroidColor.BLACK)
        }
    }
    return out
}

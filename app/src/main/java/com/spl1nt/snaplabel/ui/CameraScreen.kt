package com.spl1nt.snaplabel.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spl1nt.snaplabel.editor.Layer
import com.spl1nt.snaplabel.printer.PrinterProtocol
import com.spl1nt.snaplabel.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Live camera preview with a shutter button, a gallery-picker fallback, a blank canvas, and label templates. */
@Composable
fun CameraScreen(onPhotoReady: (Bitmap) -> Unit, onTemplateReady: (Bitmap, Layer) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { BitmapUtils.loadFromUri(context, it)?.let(onPhotoReady) }
    }

    var cameraError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableStateOf(0) }
    var showTemplates by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<LabelTemplate?>(null) }

    LaunchedEffect(retryToken) {
        cameraError = null
        // ProcessCameraProvider.getInstance(...).get() BLOCKS until the future
        // resolves. On a cold start that's near-instant, but after the app is
        // backgrounded and its process is killed to reclaim memory, returning
        // to it reruns this effect and CameraX has to reinitialize the camera
        // device from scratch — which can take a real moment. Calling .get()
        // directly on LaunchedEffect's (main-thread) dispatcher would freeze
        // the entire UI — no frames, no touch input — for that whole time.
        // Dispatchers.IO keeps the block off the UI thread.
        val provider = runCatching {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        }.getOrElse {
            cameraError = "Camera unavailable: ${it.message ?: it::class.simpleName}"
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        // Tie target rotation to this view's own display rather than leaving
        // it at CameraX's default, which can otherwise mis-tag the captured
        // JPEG's EXIF orientation on a tablet started in landscape.
        val capture = ImageCapture.Builder()
            .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
            .build()
        imageCapture = capture
        provider.unbindAll()
        runCatching {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        }.onFailure { cameraError = "Camera failed to start: ${it.message ?: it::class.simpleName}" }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        cameraError?.let { message ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { retryToken++ }) { Text("Retry") }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FilledTonalIconButton(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose from gallery") }

            FilledIconButton(
                onClick = {
                    val capture = imageCapture ?: return@FilledIconButton
                    val file = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(file).build()
                    capture.takePicture(
                        output,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                BitmapUtils.loadFromFile(file.absolutePath)?.let { bmp ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { onPhotoReady(bmp) }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {}
                        },
                    )
                },
                modifier = Modifier.size(80.dp),
            ) { Icon(Icons.Default.Camera, contentDescription = "Take photo", modifier = Modifier.size(36.dp)) }

            FilledTonalIconButton(
                onClick = { onPhotoReady(blankCanvas()) },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Default.Brush, contentDescription = "Start from a blank canvas") }

            FilledTonalIconButton(
                onClick = { showTemplates = true },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Default.Article, contentDescription = "Start from a label template") }
        }
    }

    if (showTemplates) {
        ModalBottomSheet(onDismissRequest = { showTemplates = false }) {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(LABEL_TEMPLATES) { template ->
                    ListItem(
                        headlineContent = { Text(template.label) },
                        modifier = Modifier.clickable {
                            showTemplates = false
                            if (template.format.contains("%s")) {
                                pendingTemplate = template
                            } else {
                                onTemplateReady(blankCanvas(), templateTextLayer(template.format))
                            }
                        },
                    )
                }
            }
        }
    }

    pendingTemplate?.let { template ->
        var blank by remember(template) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            title = { Text(template.label) },
            text = {
                OutlinedTextField(
                    value = blank,
                    onValueChange = { blank = it },
                    singleLine = true,
                    placeholder = { Text("e.g. SAM'S ROBOT") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = blank.isNotBlank(),
                    onClick = {
                        val text = template.format.format(blank.uppercase())
                        onTemplateReady(blankCanvas(), templateTextLayer(text))
                        pendingTemplate = null
                    },
                ) { Text("Use it") }
            },
            dismissButton = { TextButton(onClick = { pendingTemplate = null }) { Text("Cancel") } },
        )
    }
}

private data class LabelTemplate(val label: String, val format: String)

private val LABEL_TEMPLATES = listOf(
    LabelTemplate("Property of ___", "PROPERTY OF %s"),
    LabelTemplate("World's Best ___", "WORLD'S BEST %s"),
    LabelTemplate("Warning: ___", "WARNING: %s"),
    LabelTemplate("Caution: ___ Inside", "CAUTION: %s INSIDE"),
    LabelTemplate("Danger: ___", "DANGER: %s"),
    LabelTemplate("#1 ___", "#1 %s"),
    LabelTemplate("Do Not Touch", "DO NOT TOUCH\n(SERIOUSLY)"),
    LabelTemplate("Top Secret", "TOP SECRET\nKEEP OUT"),
)

private fun templateTextLayer(text: String): Layer {
    val w = (PrinterProtocol.LABEL_WIDTH_DOTS - PrinterProtocol.MARGIN_DOTS * 2) * 3
    val h = (PrinterProtocol.LABEL_HEIGHT_DOTS - PrinterProtocol.MARGIN_DOTS * 2) * 3
    return Layer.TextLayer(text = text, center = Offset(w / 2f, h / 2f), baseFontSizePx = 90f)
}

/**
 * A plain white bitmap sized to exactly match the printer's printable-area
 * aspect ratio (see [PrinterProtocol.printableCropRect]), so a from-scratch
 * drawing fills the whole label with nothing cropped off — unlike a photo,
 * which usually has some aspect-ratio mismatch to crop away.
 */
private fun blankCanvas(): Bitmap {
    val w = PrinterProtocol.LABEL_WIDTH_DOTS - PrinterProtocol.MARGIN_DOTS * 2
    val h = PrinterProtocol.LABEL_HEIGHT_DOTS - PrinterProtocol.MARGIN_DOTS * 2
    val scale = 3 // edit at a few times the label's own dot resolution for smoother strokes/text
    return Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
    }
}

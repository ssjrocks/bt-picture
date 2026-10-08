package com.spl1nt.snaplabel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.spl1nt.snaplabel.editor.BrushShape
import com.spl1nt.snaplabel.editor.Layer
import com.spl1nt.snaplabel.editor.LayerRenderer
import com.spl1nt.snaplabel.printer.PrinterProtocol
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Tool { SELECT, TEXT, EMOJI, DRAW }

/**
 * Photo editor: move/scale/rotate text & emoji stickers with drag + pinch,
 * freehand drawing, all flattened onto the photo at full resolution when the
 * user taps Print (see [LayerRenderer.flattenAtPhotoResolution]).
 */
@Composable
fun EditorScreen(
    photo: Bitmap,
    onPrint: (Bitmap) -> Unit,
    onBack: () -> Unit,
) {
    var layers by remember { mutableStateOf(listOf<Layer>()) }
    var tool by remember { mutableStateOf(Tool.SELECT) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf(false) }
    var pendingTextTap by remember { mutableStateOf<Offset?>(null) }
    var activeDraw by remember { mutableStateOf<List<Offset>?>(null) }
    val drawColor = Color.Black
    var brushSize by remember { mutableFloatStateOf(10f) }
    var brushShape by remember { mutableStateOf(BrushShape.ROUND) }

    var canvasSize by remember { mutableStateOf(Offset(1f, 1f)) }
    val photoW = photo.width.toFloat()
    val photoH = photo.height.toFloat()
    // Exactly the rectangle PrintPreviewScreen's fitToLabel() will later crop
    // to — shown as a guide so what you compose here is what actually prints,
    // instead of a silent crop surprising you after the fact.
    val cropRect = remember(photo) { PrinterProtocol.printableCropRect(photo.width, photo.height) }

    fun displayScale(): Float = min(canvasSize.x / photoW, canvasSize.y / photoH)
    fun displayOffset(): Offset {
        val s = displayScale()
        return Offset((canvasSize.x - photoW * s) / 2f, (canvasSize.y - photoH * s) / 2f)
    }
    fun toPhotoSpace(p: Offset): Offset {
        val s = displayScale()
        val o = displayOffset()
        return Offset((p.x - o.x) / s, (p.y - o.y) / s)
    }
    fun hitTest(photoPoint: Offset): Layer? {
        for (layer in layers.asReversed()) {
            val radius = when (layer) {
                is Layer.TextLayer -> layer.baseFontSizePx * layer.scale
                is Layer.EmojiLayer -> layer.baseSizePx * layer.scale * 0.6f
                is Layer.DrawLayer -> continue
            }
            if ((layer.center - photoPoint).getDistance() <= radius) return layer
        }
        return null
    }
    fun updateLayer(id: String, transform: (Layer) -> Layer) {
        layers = layers.map { if (it.id == id) transform(it) else it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Discard") } },
                actions = {
                    IconButton(onClick = { if (layers.isNotEmpty()) layers = layers.dropLast(1) }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    TextButton(onClick = { onPrint(LayerRenderer.flattenAtPhotoResolution(photo, layers)) }) {
                        Text("Print")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (tool == Tool.DRAW) {
                    BrushControls(
                        size = brushSize,
                        onSizeChange = { brushSize = it },
                        shape = brushShape,
                        onShapeChange = { brushShape = it },
                    )
                }
                NavigationBar {
                NavigationBarItem(
                    selected = tool == Tool.SELECT,
                    onClick = { tool = Tool.SELECT },
                    icon = { Icon(Icons.Default.PanTool, contentDescription = null) },
                    label = { Text("Move") },
                )
                NavigationBarItem(
                    selected = tool == Tool.TEXT,
                    onClick = { tool = Tool.TEXT },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    label = { Text("Text") },
                )
                NavigationBarItem(
                    selected = tool == Tool.EMOJI,
                    onClick = { tool = Tool.EMOJI; showEmojiPicker = true },
                    icon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null) },
                    label = { Text("Emoji") },
                )
                NavigationBarItem(
                    selected = tool == Tool.DRAW,
                    onClick = { tool = Tool.DRAW },
                    icon = { Icon(Icons.Default.Draw, contentDescription = null) },
                    label = { Text("Draw") },
                )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF111111)),
        ) {
            val imageBitmap = remember(photo) { photo.asImageBitmap() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tool) {
                        when (tool) {
                            Tool.SELECT -> detectTransformGestures { centroid, pan, zoom, rotation ->
                                val id = hitTest(toPhotoSpace(centroid))?.id ?: return@detectTransformGestures
                                val s = displayScale()
                                val moved = Offset(pan.x / s, pan.y / s)
                                updateLayer(id) { l ->
                                    when (l) {
                                        is Layer.TextLayer -> l.copy(
                                            center = l.center + moved,
                                            scale = (l.scale * zoom).coerceIn(0.3f, 6f),
                                            rotationDeg = l.rotationDeg + rotation,
                                        )
                                        is Layer.EmojiLayer -> l.copy(
                                            center = l.center + moved,
                                            scale = (l.scale * zoom).coerceIn(0.3f, 6f),
                                            rotationDeg = l.rotationDeg + rotation,
                                        )
                                        else -> l
                                    }
                                }
                            }
                            Tool.TEXT -> detectTapGestures { tap ->
                                pendingTextTap = toPhotoSpace(tap)
                                editingText = true
                            }
                            Tool.EMOJI -> {}
                            Tool.DRAW -> detectDragGestures(
                                onDragStart = { activeDraw = listOf(toPhotoSpace(it)) },
                                onDrag = { change, _ -> activeDraw = (activeDraw ?: emptyList()) + toPhotoSpace(change.position) },
                                onDragEnd = {
                                    activeDraw?.let { pts ->
                                        if (pts.size > 1) {
                                            layers = layers + Layer.DrawLayer(
                                                points = pts,
                                                color = drawColor,
                                                strokeWidthPx = brushSize,
                                                brushShape = brushShape,
                                            )
                                        }
                                    }
                                    activeDraw = null
                                },
                            )
                        }
                    },
            ) {
                canvasSize = Offset(size.width, size.height)
                val s = displayScale()
                val o = displayOffset()
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                    dstSize = IntSize((photoW * s).roundToInt(), (photoH * s).roundToInt()),
                )
                drawIntoCanvas { c ->
                    LayerRenderer.draw(c.nativeCanvas, layers, s, s, o.x, o.y)
                    activeDraw?.let { pts ->
                        val preview = Layer.DrawLayer(points = pts, color = drawColor, strokeWidthPx = brushSize, brushShape = brushShape)
                        LayerRenderer.draw(c.nativeCanvas, listOf(preview), s, s, o.x, o.y)
                    }
                }
                drawRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(o.x + cropRect[0] * s, o.y + cropRect[1] * s),
                    size = Size(cropRect[2] * s, cropRect[3] * s),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                )
            }
            Text(
                "Dashed box = what actually prints",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            onDismiss = { showEmojiPicker = false; tool = Tool.SELECT },
            onPick = { emoji ->
                layers = layers + Layer.EmojiLayer(emoji = emoji, center = Offset(photoW / 2f, photoH / 2f))
                showEmojiPicker = false
                tool = Tool.SELECT
            },
        )
    }

    if (editingText) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingText = false; pendingTextTap = null },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isNotBlank()) {
                        val center = pendingTextTap ?: Offset(photoW / 2f, photoH / 2f)
                        layers = layers + Layer.TextLayer(text = value, center = center)
                    }
                    editingText = false
                    pendingTextTap = null
                    tool = Tool.SELECT
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { editingText = false; pendingTextTap = null }) { Text("Cancel") }
            },
        )
    }
}

/** Shown above the tool bar only while the Draw tool is active: stroke width + brush shape. */
@Composable
private fun BrushControls(
    size: Float,
    onSizeChange: (Float) -> Unit,
    shape: BrushShape,
    onShapeChange: (BrushShape) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A small filled dot that grows with the slider, so size is felt as well as read.
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size((8 + size).dp.coerceAtMost(40.dp))
                    .background(Color.Black, shape = CircleShape),
            )
            Slider(
                value = size,
                onValueChange = onSizeChange,
                valueRange = 2f..40f,
                modifier = Modifier.weight(1f),
            )
            BrushShapeButton(Icons.Default.Circle, "Round brush", shape == BrushShape.ROUND) { onShapeChange(BrushShape.ROUND) }
            BrushShapeButton(Icons.Default.CropSquare, "Square brush", shape == BrushShape.SQUARE) { onShapeChange(BrushShape.SQUARE) }
            BrushShapeButton(Icons.Default.MoreHoriz, "Dashed brush", shape == BrushShape.DASHED) { onShapeChange(BrushShape.DASHED) }
        }
    }
}

@Composable
private fun BrushShapeButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Icon(icon, contentDescription = label) }
}

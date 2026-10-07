package com.spl1nt.snaplabel.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Everything the editor can draw on top of a photo. Positions/sizes live in the
 * *photo's own pixel space*, not screen pixels, so the same layer list replays
 * correctly at any output resolution: once scaled down for the live on-screen
 * preview, and again at full label resolution when flattening for print.
 */
sealed class Layer {
    abstract val id: String
    abstract val center: Offset
    abstract val rotationDeg: Float
    abstract val scale: Float

    data class TextLayer(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        override val center: Offset,
        override val rotationDeg: Float = 0f,
        override val scale: Float = 1f,
        val color: Color = Color.Black,
        val baseFontSizePx: Float = 72f,
    ) : Layer()

    data class EmojiLayer(
        override val id: String = UUID.randomUUID().toString(),
        val emoji: String,
        override val center: Offset,
        override val rotationDeg: Float = 0f,
        override val scale: Float = 1f,
        val baseSizePx: Float = 140f,
    ) : Layer()

    data class DrawLayer(
        override val id: String = UUID.randomUUID().toString(),
        val points: List<Offset>,
        val color: Color = Color.Black,
        val strokeWidthPx: Float = 10f,
        override val center: Offset = Offset.Zero,
        override val rotationDeg: Float = 0f,
        override val scale: Float = 1f,
    ) : Layer()
}

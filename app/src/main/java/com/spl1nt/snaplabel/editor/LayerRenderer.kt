package com.spl1nt.snaplabel.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb

/**
 * Draws [layers] (stored in the base photo's own pixel space) onto [canvas],
 * which may belong to a differently-sized bitmap than the photo (e.g. a small
 * on-screen preview vs. the full-resolution print export) — [scaleX]/[scaleY]
 * map photo-space coordinates onto that canvas so the same layer list looks
 * identical, just sharper or softer, at any output size.
 */
object LayerRenderer {

    fun draw(canvas: Canvas, layers: List<Layer>, scaleX: Float, scaleY: Float) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (layer in layers) {
            when (layer) {
                is Layer.TextLayer -> {
                    textPaint.color = layer.color.toArgb()
                    textPaint.textSize = layer.baseFontSizePx * layer.scale * scaleX
                    drawCentered(canvas, layer.center, layer.rotationDeg, scaleX, scaleY) {
                        val w = textPaint.measureText(layer.text)
                        canvas.drawText(layer.text, -w / 2f, textPaint.textSize / 3f, textPaint)
                    }
                }
                is Layer.EmojiLayer -> {
                    emojiPaint.textSize = layer.baseSizePx * layer.scale * scaleX
                    drawCentered(canvas, layer.center, layer.rotationDeg, scaleX, scaleY) {
                        val w = emojiPaint.measureText(layer.emoji)
                        canvas.drawText(layer.emoji, -w / 2f, emojiPaint.textSize / 3f, emojiPaint)
                    }
                }
                is Layer.DrawLayer -> {
                    if (layer.points.size < 2) continue
                    strokePaint.color = layer.color.toArgb()
                    strokePaint.strokeWidth = (layer.strokeWidthPx * scaleX).coerceAtLeast(1f)
                    val path = Path()
                    val first = layer.points.first()
                    path.moveTo(first.x * scaleX, first.y * scaleY)
                    for (p in layer.points.drop(1)) path.lineTo(p.x * scaleX, p.y * scaleY)
                    canvas.drawPath(path, strokePaint)
                }
            }
        }
    }

    private inline fun drawCentered(
        canvas: Canvas,
        center: androidx.compose.ui.geometry.Offset,
        rotationDeg: Float,
        scaleX: Float,
        scaleY: Float,
        block: () -> Unit,
    ) {
        canvas.save()
        canvas.translate(center.x * scaleX, center.y * scaleY)
        canvas.rotate(rotationDeg)
        block()
        canvas.restore()
    }

    /** Flatten [base] + [layers] into a single opaque bitmap at the photo's own resolution. */
    fun flattenAtPhotoResolution(base: Bitmap, layers: List<Layer>): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        draw(Canvas(out), layers, 1f, 1f)
        return out
    }
}

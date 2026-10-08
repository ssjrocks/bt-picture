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
 * map photo-space coordinates onto that canvas, and [offsetX]/[offsetY] must
 * match wherever the photo itself was drawn (e.g. letterbox/pillarbox padding
 * when the photo's aspect ratio doesn't match the canvas's). Get that offset
 * wrong and every layer renders in the wrong place relative to the photo —
 * exactly the "lines don't match my finger" bug this used to have, which only
 * showed up once the letterbox gap got big (tablet, landscape, non-matching
 * photo aspect ratio).
 */
object LayerRenderer {

    fun draw(canvas: Canvas, layers: List<Layer>, scaleX: Float, scaleY: Float, offsetX: Float = 0f, offsetY: Float = 0f) {
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
                    drawCentered(canvas, layer.center, layer.rotationDeg, scaleX, scaleY, offsetX, offsetY) {
                        val w = textPaint.measureText(layer.text)
                        canvas.drawText(layer.text, -w / 2f, textPaint.textSize / 3f, textPaint)
                    }
                }
                is Layer.EmojiLayer -> {
                    emojiPaint.textSize = layer.baseSizePx * layer.scale * scaleX
                    drawCentered(canvas, layer.center, layer.rotationDeg, scaleX, scaleY, offsetX, offsetY) {
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
                    path.moveTo(first.x * scaleX + offsetX, first.y * scaleY + offsetY)
                    for (p in layer.points.drop(1)) path.lineTo(p.x * scaleX + offsetX, p.y * scaleY + offsetY)
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
        offsetX: Float,
        offsetY: Float,
        block: () -> Unit,
    ) {
        canvas.save()
        canvas.translate(center.x * scaleX + offsetX, center.y * scaleY + offsetY)
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

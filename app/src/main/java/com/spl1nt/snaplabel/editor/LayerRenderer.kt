package com.spl1nt.snaplabel.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

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
                        // Templates can have more than one line (e.g. "DO NOT TOUCH\n(SERIOUSLY)"); Canvas.drawText
                        // doesn't break on \n itself, so lines are split and stacked here, centered as a block.
                        val lines = layer.text.split("\n")
                        val lineHeight = textPaint.textSize * 1.15f
                        var y = -lineHeight * (lines.size - 1) / 2f + textPaint.textSize * 0.35f
                        for (line in lines) {
                            val w = textPaint.measureText(line)
                            canvas.drawText(line, -w / 2f, y, textPaint)
                            y += lineHeight
                        }
                    }
                }
                is Layer.EmojiLayer -> {
                    val sizePx = layer.baseSizePx * layer.scale * scaleX
                    drawCentered(canvas, layer.center, layer.rotationDeg, scaleX, scaleY, offsetX, offsetY) {
                        if (layer.isGoogly) {
                            drawGooglyEyes(canvas, layer.id, sizePx)
                        } else {
                            emojiPaint.textSize = sizePx
                            val w = emojiPaint.measureText(layer.emoji)
                            canvas.drawText(layer.emoji, -w / 2f, emojiPaint.textSize / 3f, emojiPaint)
                        }
                    }
                }
                is Layer.DrawLayer -> {
                    if (layer.points.size < 2) continue
                    strokePaint.color = layer.color.toArgb()
                    val width = (layer.strokeWidthPx * scaleX).coerceAtLeast(1f)
                    strokePaint.strokeWidth = width
                    when (layer.brushShape) {
                        BrushShape.ROUND -> {
                            strokePaint.strokeCap = Paint.Cap.ROUND
                            strokePaint.strokeJoin = Paint.Join.ROUND
                            strokePaint.pathEffect = null
                        }
                        BrushShape.SQUARE -> {
                            strokePaint.strokeCap = Paint.Cap.SQUARE
                            strokePaint.strokeJoin = Paint.Join.MITER
                            strokePaint.pathEffect = null
                        }
                        BrushShape.DASHED -> {
                            strokePaint.strokeCap = Paint.Cap.ROUND
                            strokePaint.strokeJoin = Paint.Join.ROUND
                            val dash = (width * 1.6f).coerceAtLeast(4f)
                            strokePaint.pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
                        }
                    }
                    val path = Path()
                    val first = layer.points.first()
                    path.moveTo(first.x * scaleX + offsetX, first.y * scaleY + offsetY)
                    for (p in layer.points.drop(1)) path.lineTo(p.x * scaleX + offsetX, p.y * scaleY + offsetY)
                    canvas.drawPath(path, strokePaint)
                }
            }
        }
    }

    /**
     * A pair of classic prank googly eyes, drawn as actual circles rather
     * than relying on any single emoji glyph rendering consistently across
     * devices/fonts. Each pupil sits at a fixed-but-silly offset derived from
     * [seed] (the layer's own id), so it looks "random" without jittering on
     * every redraw.
     */
    private fun drawGooglyEyes(canvas: Canvas, seed: String, totalSizePx: Float) {
        val eyeRadius = totalSizePx * 0.28f
        val gap = totalSizePx * 0.1f
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (totalSizePx * 0.035f).coerceAtLeast(1f)
        }
        val pupil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        val centers = floatArrayOf(-(eyeRadius + gap / 2f), eyeRadius + gap / 2f)
        for ((i, cx) in centers.withIndex()) {
            canvas.drawCircle(cx, 0f, eyeRadius, white)
            canvas.drawCircle(cx, 0f, eyeRadius, outline)
            val hash = (seed + i).hashCode()
            val angle = ((hash and 0xFFFF) / 65535f) * (2 * Math.PI).toFloat()
            val dist = eyeRadius * (0.25f + (((hash ushr 16) and 0xFF) / 255f) * 0.45f)
            canvas.drawCircle(cx + cos(angle) * dist, sin(angle) * dist, eyeRadius * 0.4f, pupil)
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

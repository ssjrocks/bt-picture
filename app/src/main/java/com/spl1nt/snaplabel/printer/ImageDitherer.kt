package com.spl1nt.snaplabel.printer

import android.graphics.Bitmap

/**
 * Converts a photo to the 1-bit image this thermal printer actually needs,
 * using Floyd-Steinberg error-diffusion dithering (far better for photos than
 * a flat threshold). The brightness/contrast nudge compensates thermal dot
 * gain: prints come out darker than the source image, found by comparing a
 * printed test label against its source on 2026-10-07.
 */
object ImageDitherer {

    fun dither(
        src: Bitmap,
        x: Int,
        y: Int,
        brightness: Float = 1.12f,
        contrast: Float = 1.15f,
    ): PackedBitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            var lum = 0.299f * r + 0.587f * g + 0.114f * b
            lum = (lum - 128f) * contrast + 128f
            lum *= brightness
            gray[i] = lum.coerceIn(0f, 255f)
        }

        val bytesPerRow = (w + 7) / 8
        val rows = ArrayList<ByteArray>(h)
        for (row in 0 until h) {
            val packed = ByteArray(bytesPerRow)
            val base = row * w
            for (col in 0 until w) {
                val idx = base + col
                val old = gray[idx]
                val black = old < 128f
                if (black) {
                    packed[col / 8] = (packed[col / 8].toInt() or (0x80 ushr (col % 8))).toByte()
                }
                val newVal = if (black) 0f else 255f
                val err = old - newVal
                if (col + 1 < w) gray[idx + 1] += err * 7f / 16f
                if (row + 1 < h) {
                    if (col > 0) gray[idx + w - 1] += err * 3f / 16f
                    gray[idx + w] += err * 5f / 16f
                    if (col + 1 < w) gray[idx + w + 1] += err * 1f / 16f
                }
            }
            rows.add(packed)
        }
        return PackedBitmap(x, y, w, h, bytesPerRow, rows)
    }
}

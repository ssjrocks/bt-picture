package com.spl1nt.snaplabel.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Decodes photos down to a sane working resolution and applies EXIF rotation.
 * A tablet or modern phone camera can produce 20-100+ megapixel JPEGs; the
 * printer only ever needs a few hundred pixels per side, and carrying the
 * full-resolution bitmap through the editor's Compose Canvas and the
 * per-pixel flatten/dither steps is needlessly slow and memory-heavy. This
 * samples down at decode time (never fully allocating the original size)
 * rather than decoding huge then scaling down after.
 */
object BitmapUtils {
    private const val MAX_DIMENSION = 2200

    fun loadFromFile(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val bmp = BitmapFactory.decodeFile(path, options) ?: return null
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        applyRotationAndDownscale(bmp, orientation)
    }.getOrNull()

    fun loadFromUri(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        applyRotationAndDownscale(bmp, orientation)
    }.getOrNull()

    /** inSampleSize must be a power of two; aim for roughly 2x [MAX_DIMENSION] so the final precise resize still has headroom. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION * 2 || height / sample > MAX_DIMENSION * 2) sample *= 2
        return sample
    }

    private fun applyRotationAndDownscale(bmp: Bitmap, exifOrientation: Int): Bitmap {
        val degrees = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (degrees == 0f) {
            bmp
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).also { if (it !== bmp) bmp.recycle() }
        }
        val longest = maxOf(rotated.width, rotated.height)
        if (longest <= MAX_DIMENSION) return rotated
        val scale = MAX_DIMENSION.toFloat() / longest
        val w = (rotated.width * scale).toInt().coerceAtLeast(1)
        val h = (rotated.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(rotated, w, h, true).also { if (it !== rotated) rotated.recycle() }
    }
}

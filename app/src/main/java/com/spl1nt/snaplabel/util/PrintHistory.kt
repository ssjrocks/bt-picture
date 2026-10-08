package com.spl1nt.snaplabel.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * A simple on-disk history of printed labels (as PNGs), so past labels can be
 * browsed and reprinted later — no database, just files in app-private
 * storage. Each saved file is already the exact label-sized, dithered image
 * that was sent to the printer, so reprinting just re-packs the same pixels.
 */
object PrintHistory {
    private const val DIR_NAME = "print_history"

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun save(context: Context, bitmap: Bitmap) {
        val file = File(dir(context), "label_${System.currentTimeMillis()}.png")
        runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File) {
        runCatching { file.delete() }
    }
}

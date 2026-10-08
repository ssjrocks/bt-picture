package com.spl1nt.snaplabel.printer

/**
 * CPCL command building for the Rongta RPP30 (and compatible) BLE label printers.
 *
 * Geometry below was measured by hand on 2026-10-07 against a real RPP30 and a
 * physical ruler label (scaled against a 28.65mm coin in a photo, +/-3%):
 *  - ~8 dots/mm, ~203dpi -> the CPCL header's nominal "200 200" is correct.
 *  - Printable area is about 576x320 dots on this printer's stock; content
 *    placed outside that gets clipped off the label, so [LABEL_WIDTH_DOTS] /
 *    [LABEL_HEIGHT_DOTS] are set to the *measured* printable area, not the
 *    full physical label.
 *  - The printer never sends anything back (no ACK/status) over BLE, so a
 *    successful [BlePrinterManager.print] call only means the Bluetooth
 *    write succeeded, not that the label came out — there is no read-back.
 */
object PrinterProtocol {
    const val PRINTER_DPI = 200
    const val DOTS_PER_MM = 8
    const val LABEL_WIDTH_DOTS = 576
    const val LABEL_HEIGHT_DOTS = 320
    const val MARGIN_DOTS = 8

    private const val CRLF = "\r\n"
    private const val FOOTER = "FORM\r\nPRINT\r\n"

    private fun header(heightDots: Int) = "! 0 $PRINTER_DPI $PRINTER_DPI $heightDots 1$CRLF"

    /** One line of the printer's built-in bitmap font. */
    fun textCommand(
        text: String,
        x: Int = MARGIN_DOTS,
        y: Int = MARGIN_DOTS,
        heightDots: Int = LABEL_HEIGHT_DOTS,
        font: Int = 4,
        size: Int = 0,
    ): ByteArray {
        val safe = text.replace("\r", " ").replace("\n", " ")
        return (header(heightDots) + "TEXT $font $size $x $y $safe$CRLF" + FOOTER)
            .toByteArray(Charsets.US_ASCII)
    }

    /**
     * Build an `EG` bitmap command for one or more already-dithered, packed
     * 1-bit images (as produced by [ImageDitherer]) and wrap it in a form.
     */
    fun bitmapCommand(bitmaps: List<PackedBitmap>, heightDots: Int = LABEL_HEIGHT_DOTS): ByteArray {
        val sb = StringBuilder()
        sb.append(header(heightDots))
        for (bmp in bitmaps) {
            sb.append("EG ${bmp.bytesPerRow} ${bmp.heightDots} ${bmp.x} ${bmp.y} ")
            for (row in bmp.rows) {
                for (b in row) {
                    val v = b.toInt() and 0xFF
                    sb.append(HEX[(v ushr 4)])
                    sb.append(HEX[v and 0x0F])
                }
            }
            sb.append(CRLF)
        }
        sb.append(FOOTER)
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    private val HEX = "0123456789ABCDEF".toCharArray()

    /**
     * The centered rectangle, in `srcW`x`srcH` source-pixel space, that
     * survives cropping to this printer's aspect ratio. Shared by the print
     * pipeline's actual crop and the editor's crop-guide overlay so a photo
     * never prints a different area than what the editor showed as the
     * printable region.
     */
    fun printableCropRect(srcW: Int, srcH: Int): IntArray {
        val targetW = LABEL_WIDTH_DOTS - MARGIN_DOTS * 2
        val targetH = LABEL_HEIGHT_DOTS - MARGIN_DOTS * 2
        val srcRatio = srcW.toFloat() / srcH
        val targetRatio = targetW.toFloat() / targetH
        val cw: Int
        val ch: Int
        if (srcRatio > targetRatio) {
            ch = srcH
            cw = (srcH * targetRatio).toInt().coerceAtMost(srcW)
        } else {
            cw = srcW
            ch = (srcW / targetRatio).toInt().coerceAtMost(srcH)
        }
        val cx = ((srcW - cw) / 2).coerceAtLeast(0)
        val cy = ((srcH - ch) / 2).coerceAtLeast(0)
        return intArrayOf(cx, cy, cw, ch)
    }
}

/** A dithered 1-bit image, MSB-first per row, already positioned on the label. */
data class PackedBitmap(
    val x: Int,
    val y: Int,
    val widthDots: Int,
    val heightDots: Int,
    val bytesPerRow: Int,
    val rows: List<ByteArray>,
)

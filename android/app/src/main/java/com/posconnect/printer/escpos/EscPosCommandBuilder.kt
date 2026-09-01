package com.posconnect.printer.escpos

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class EscPosCommandBuilder(private val charset: Charset = Charsets.UTF_8) {
    private val buffer = ByteArrayOutputStream()

    enum class Alignment(val value: Byte) {
        LEFT(0),
        CENTER(1),
        RIGHT(2)
    }

    enum class BarcodeType(val value: Byte) {
        UPC_A(0x41),
        UPC_E(0x42),
        EAN13(0x43),
        EAN8(0x44),
        CODE39(0x45),
        ITF(0x46),
        CODEBAR(0x47),
        CODE93(0x48),
        CODE128(0x49)
    }

    enum class QrErrorCorrection(val value: Byte) {
        L(48), // 7%
        M(49), // 15%
        Q(50), // 25%
        H(51)  // 30%
    }

    fun initialize(): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x40)) // ESC @
    }

    fun setAlignment(align: Alignment): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x61, align.value)) // ESC a n
    }

    fun setBold(enable: Boolean): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x45, if (enable) 1 else 0)) // ESC E n
    }

    fun setUnderline(enable: Boolean): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x2D, if (enable) 1 else 0)) // ESC - n
    }

    fun setTextSize(doubleWidth: Boolean, doubleHeight: Boolean): EscPosCommandBuilder = apply {
        var n = 0
        if (doubleWidth) n = n or 0x20
        if (doubleHeight) n = n or 0x01
        buffer.write(byteArrayOf(0x1D, 0x21, n.toByte())) // GS ! n
    }

    fun resetTextSize(): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1D, 0x21, 0x00))
    }

    fun setInverted(enable: Boolean): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1D, 0x42, if (enable) 1 else 0)) // GS B n
    }

    fun text(str: String): EscPosCommandBuilder = apply {
        buffer.write(str.toByteArray(charset))
    }

    fun textLine(str: String): EscPosCommandBuilder = apply {
        buffer.write(str.toByteArray(charset))
        buffer.write(0x0A) // LF
    }

    fun lineFeed(lines: Int = 1): EscPosCommandBuilder = apply {
        if (lines <= 1) {
            buffer.write(0x0A)
        } else {
            buffer.write(byteArrayOf(0x1B, 0x64, lines.coerceIn(1, 255).toByte())) // ESC d n
        }
    }

    fun horizontalLine(columns: Int = 48, char: Char = '-'): EscPosCommandBuilder = apply {
        textLine(char.toString().repeat(columns))
    }

    fun doubleHorizontalLine(columns: Int = 48): EscPosCommandBuilder = apply {
        horizontalLine(columns, '=')
    }

    fun twoColumnRow(left: String, right: String, totalColumns: Int = 48): EscPosCommandBuilder = apply {
        val availableLeft = (totalColumns - right.length - 1).coerceAtLeast(0)
        val truncatedLeft = if (left.length > availableLeft) left.take(availableLeft) else left
        val spaces = (totalColumns - truncatedLeft.length - right.length).coerceAtLeast(1)
        textLine(truncatedLeft + " ".repeat(spaces) + right)
    }

    fun threeColumnRow(col1: String, col2: String, col3: String, c1Width: Int, c2Width: Int, c3Width: Int): EscPosCommandBuilder = apply {
        val p1 = col1.padEnd(c1Width).take(c1Width)
        val p2 = col2.padStart(c2Width).take(c2Width)
        val p3 = col3.padStart(c3Width).take(c3Width)
        textLine("$p1 $p2 $p3")
    }

    fun fourColumnRow(col1: String, col2: String, col3: String, col4: String, totalColumns: Int = 48): EscPosCommandBuilder = apply {
        if (totalColumns >= 64) {
            // 4-inch width layout (64 cols): Item (32), Qty (8), Rate (10), Total (12) + 2 spaces
            val p1 = col1.padEnd(32).take(32)
            val p2 = col2.padStart(8).take(8)
            val p3 = col3.padStart(10).take(10)
            val p4 = col4.padStart(12).take(12)
            textLine("$p1 $p2 $p3 $p4")
        } else {
            // 3-inch width layout (48 cols): Item (22), Qty (6), Rate (8), Total (10) + 2 spaces
            val p1 = col1.padEnd(22).take(22)
            val p2 = col2.padStart(6).take(6)
            val p3 = col3.padStart(8).take(8)
            val p4 = col4.padStart(10).take(10)
            textLine("$p1 $p2 $p3 $p4")
        }
    }

    fun cut(partial: Boolean = false): EscPosCommandBuilder = apply {
        // Feed 2 lines then cut
        lineFeed(2)
        buffer.write(byteArrayOf(0x1D, 0x56, if (partial) 0x01 else 0x00)) // GS V m
    }

    fun openCashDrawer(pin: Int = 0): EscPosCommandBuilder = apply {
        val m = if (pin == 0) 0x00.toByte() else 0x01.toByte()
        buffer.write(byteArrayOf(0x1B, 0x70, m, 0x19, 0xFA.toByte())) // ESC p m t1 t2
    }

    fun beep(count: Int = 1, duration: Int = 2): EscPosCommandBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x42, count.coerceIn(1, 9).toByte(), duration.coerceIn(1, 9).toByte())) // ESC B n t
    }

    /**
     * Standard ESC/POS QR Code command generation (GS ( k)
     */
    fun printQRCode(data: String, moduleSize: Int = 6, errorCorrection: QrErrorCorrection = QrErrorCorrection.M): EscPosCommandBuilder = apply {
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val dataLen = dataBytes.size + 3

        // 1. Set model (Model 2)
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // 2. Set module size (1-16 dots)
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleSize.coerceIn(1, 16).toByte()))

        // 3. Set error correction level
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, errorCorrection.value))

        // 4. Store data in symbol storage area
        val pL = (dataLen and 0xFF).toByte()
        val pH = ((dataLen shr 8) and 0xFF).toByte()
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        buffer.write(dataBytes)

        // 5. Print the QR code
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        lineFeed(1)
    }

    /**
     * Standard ESC/POS Barcode generation (GS k)
     */
    fun printBarcode(data: String, type: BarcodeType = BarcodeType.CODE128, height: Int = 64, width: Int = 2): EscPosCommandBuilder = apply {
        // Set barcode height
        buffer.write(byteArrayOf(0x1D, 0x68, height.coerceIn(1, 255).toByte())) // GS h n
        // Set barcode width
        buffer.write(byteArrayOf(0x1D, 0x77, width.coerceIn(2, 6).toByte())) // GS w n
        // Set HRI characters below barcode
        buffer.write(byteArrayOf(0x1D, 0x48, 0x02)) // GS H 2

        val barcodeBytes = data.toByteArray(Charsets.US_ASCII)
        buffer.write(byteArrayOf(0x1D, 0x6B, type.value, barcodeBytes.size.toByte()))
        buffer.write(barcodeBytes)
        lineFeed(1)
    }

    /**
     * Print Bitmap Image using GS v 0 raster bit image command
     */
    fun printBitmap(bitmap: Bitmap): EscPosCommandBuilder = apply {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        buffer.write(byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(),
            ((height shr 8) and 0xFF).toByte()
        ))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (xb in 0 until widthBytes) {
                var byteVal = 0
                for (b in 0 until 8) {
                    val x = xb * 8 + b
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val bl = Color.blue(pixel)
                        val alpha = Color.alpha(pixel)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bl).toInt()
                        // Black dot if alpha > 128 and luminance < 160
                        if (alpha > 128 && luminance < 160) {
                            byteVal = byteVal or (1 shl (7 - b))
                        }
                    }
                }
                buffer.write(byteVal)
            }
        }
        lineFeed(1)
    }

    fun rawBytes(bytes: ByteArray): EscPosCommandBuilder = apply {
        buffer.write(bytes)
    }

    fun build(): ByteArray = buffer.toByteArray()
}

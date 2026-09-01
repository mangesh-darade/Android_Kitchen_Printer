package com.posconnect.printer.escpos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.posconnect.printer.model.ReceiptData
import java.util.Locale

object TextRasterizer {

    /**
     * Checks if a string contains complex non-ASCII or Indian unicode characters (Devanagari, ₹, etc.)
     */
    fun containsComplexUnicode(text: String): Boolean {
        for (char in text) {
            val code = char.code
            // Devanagari range: 0x0900 - 0x097F
            // Rupee symbol: 0x20B9
            // General Unicode > 127
            if (code > 127 || code == 0x20B9 || (code in 0x0900..0x097F)) {
                return true
            }
        }
        return false
    }

    /**
     * Decodes Base64 encoded image (PNG/JPEG) into a scaled monochrome Bitmap suitable for thermal printing
     */
    fun decodeBase64Image(base64Str: String, maxWidth: Int = 384): Bitmap? {
        return try {
            val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val original = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return null
            scaleBitmap(original, maxWidth)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmap(src: Bitmap, targetWidth: Int): Bitmap {
        if (src.width <= targetWidth) return src
        val ratio = targetWidth.toFloat() / src.width.toFloat()
        val targetHeight = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }

    /**
     * Rasterizes a single line or block of multilingual text (English, Marathi, Hindi) into a monochrome Bitmap
     * with full-width margin distribution, centered headers, expanded divider lines, and right-aligned quantities.
     */
    fun rasterizeText(
        text: String,
        widthPx: Int = 576, // 576px for 3-inch 203dpi, 832px for 4-inch
        textSizeSp: Float = 24f,
        isBold: Boolean = false,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Bitmap {
        val cleanText = text.trim()
        val lines = cleanText.split(Regex("\r?\n"))
        if (lines.size <= 1 && cleanText.length < 60) {
            // Single short line: render centered or aligned
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = textSizeSp
                typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                isAntiAlias = true
            }
            val height = (textSizeSp * 2.2f).toInt().coerceAtLeast(40)
            val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawCenteredText(canvas, cleanText, textSizeSp + 6f, textPaint, widthPx)
            return bitmap
        }

        val paddingHorizontal = 16f
        val paintTitle = TextPaint().apply {
            color = Color.BLACK
            textSize = textSizeSp * 1.15f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val paintBold = TextPaint().apply {
            color = Color.BLACK
            textSize = textSizeSp
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val paintRegular = TextPaint().apply {
            color = Color.BLACK
            textSize = textSizeSp * 0.92f
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintLine = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val estimatedLines = lines.size * 2 + 10
        val heightPx = (estimatedLines * (textSizeSp + 10f)).toInt().coerceAtLeast(300)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = textSizeSp

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                y += textSizeSp * 0.3f
                continue
            }

            // 1. Divider line (---- or ====)
            if (KotTextFormatter.isDivider(trimmed)) {
                y += 2f
                canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
                y += 14f
                continue
            }

            // 2. Table Header (e.g. Items Qty)
            if (KotTextFormatter.isTableHeader(trimmed)) {
                val parts = trimmed.split(Regex("\\s{2,}"))
                val left = parts.firstOrNull() ?: trimmed
                val right = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                canvas.drawText(left, paddingHorizontal, y, paintBold)
                if (right.isNotEmpty()) {
                    val rightW = paintBold.measureText(right)
                    canvas.drawText(right, widthPx - paddingHorizontal - rightW, y, paintBold)
                }
                y += paintBold.textSize + 6f
                continue
            }

            // 3. Item row with quantity (e.g. "• American Cheese Burger (Small)     1.00")
            val itemMatch = KotTextFormatter.parseItemRow(trimmed)
            if (itemMatch != null) {
                val (item, qty) = itemMatch
                val qtyW = paintBold.measureText(qty)
                val rightX = widthPx - paddingHorizontal - qtyW
                canvas.drawText(qty, rightX, y, paintBold)

                val maxItemWidth = (rightX - paddingHorizontal - 12f).toInt().coerceAtLeast(100)
                val itemLayout = StaticLayout.Builder.obtain(item, 0, item.length, paintRegular, maxItemWidth)
                    .setLineSpacing(2f, 1f)
                    .setIncludePad(false)
                    .build()

                canvas.save()
                canvas.translate(paddingHorizontal, y - paintRegular.textSize + 4f)
                itemLayout.draw(canvas)
                canvas.restore()

                y += (itemLayout.height.toFloat() + 4f).coerceAtLeast(paintRegular.textSize + 6f)
                continue
            }

            // 4. Header / Metadata line -> Center it
            if (KotTextFormatter.isHeaderOrMeta(trimmed) || line.startsWith("   ")) {
                y = drawCenteredText(canvas, trimmed, y, paintBold, widthPx)
                continue
            }

            // 5. Default line -> check length
            if (trimmed.length < 35) {
                y = drawCenteredText(canvas, trimmed, y, paintRegular, widthPx)
            } else {
                val layout = StaticLayout.Builder.obtain(trimmed, 0, trimmed.length, paintRegular, (widthPx - paddingHorizontal * 2).toInt())
                    .setLineSpacing(2f, 1f)
                    .setIncludePad(false)
                    .build()
                canvas.save()
                canvas.translate(paddingHorizontal, y - paintRegular.textSize + 4f)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height.toFloat() + 4f
            }
        }

        val finalHeight = (y + 6f).toInt().coerceAtMost(heightPx).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, 0, 0, widthPx, finalHeight)
    }

    /**
     * Complete Rasterized Indian POS Receipt with full Marathi / Hindi / English Unicode and ₹ symbol support
     */
    fun rasterizeReceipt(receipt: ReceiptData, isFourInch: Boolean = false): Bitmap {
        val widthPx = if (isFourInch) 832 else 576 // 203 DPI standard dots
        val paddingHorizontal = 16f

        // First pass: measure total height required
        val paintTitle = TextPaint().apply {
            color = Color.BLACK
            textSize = if (isFourInch) 32f else 28f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val paintSubtitle = TextPaint().apply {
            color = Color.BLACK
            textSize = if (isFourInch) 22f else 18f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintItemBold = TextPaint().apply {
            color = Color.BLACK
            textSize = if (isFourInch) 22f else 19f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val paintItemRegular = TextPaint().apply {
            color = Color.BLACK
            textSize = if (isFourInch) 21f else 18f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintLine = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        // Estimate canvas height
        val estimatedLines = 15 + (receipt.items.size * 2) + 12
        val heightPx = (estimatedLines * 30).coerceAtLeast(400)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 24f

        // 1. Logo if present
        if (!receipt.header.logoBase64.isNullOrBlank()) {
            decodeBase64Image(receipt.header.logoBase64, maxWidth = widthPx / 2)?.let { logo ->
                val logoX = (widthPx - logo.width) / 2f
                canvas.drawBitmap(logo, logoX, y, null)
                y += logo.height + 16f
            }
        }

        // 2. Business Header
        y = drawCenteredText(canvas, receipt.header.businessName, y, paintTitle, widthPx)
        receipt.header.branchName?.let {
            y = drawCenteredText(canvas, it, y, paintSubtitle, widthPx)
        }
        if (receipt.header.address.isNotBlank()) {
            y = drawCenteredText(canvas, receipt.header.address, y, paintSubtitle, widthPx)
        }
        if (receipt.header.phone.isNotBlank()) {
            y = drawCenteredText(canvas, "Tel: ${receipt.header.phone}", y, paintSubtitle, widthPx)
        }
        receipt.header.gstNumber?.let {
            y = drawCenteredText(canvas, "GSTIN: $it", y, paintSubtitle, widthPx)
        }

        y += 8f
        canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
        y += 16f

        // 3. Invoice & Date info
        drawTwoColText(canvas, "Invoice: ${receipt.header.invoiceNumber}", "Date: ${receipt.header.dateTime.ifBlank { "17-08-2026" }}", y, paintSubtitle, widthPx, paddingHorizontal)
        y += 24f
        drawTwoColText(canvas, "Cashier: ${receipt.header.cashier}", receipt.header.tableNumber?.let { "Table: $it" } ?: "", y, paintSubtitle, widthPx, paddingHorizontal)
        y += 24f

        if (receipt.customerName != null) {
            drawTwoColText(canvas, "Customer: ${receipt.customerName}", receipt.customerPhone ?: "", y, paintSubtitle, widthPx, paddingHorizontal)
            y += 24f
        }

        y += 4f
        canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
        y += 18f

        // 4. Items Table Header
        val colItemWidth = if (isFourInch) 440f else 280f
        val colQtyWidth = 70f
        val colRateWidth = 90f
        val colAmtWidth = if (isFourInch) 150f else 100f

        canvas.drawText("ITEM / तपशील", paddingHorizontal, y, paintItemBold)
        canvas.drawText("QTY", paddingHorizontal + colItemWidth, y, paintItemBold)
        canvas.drawText("RATE", paddingHorizontal + colItemWidth + colQtyWidth, y, paintItemBold)
        val rightX = widthPx - paddingHorizontal
        val amtMeasure = paintItemBold.measureText("AMOUNT")
        canvas.drawText("AMOUNT", rightX - amtMeasure, y, paintItemBold)
        y += 12f
        canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
        y += 20f

        // 5. Items List
        for (item in receipt.items) {
            // Item name (supports Marathi / Hindi / English)
            canvas.drawText(item.name, paddingHorizontal, y, paintItemRegular)
            val qtyStr = if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else String.format(Locale.US, "%.2f", item.qty)
            canvas.drawText(qtyStr, paddingHorizontal + colItemWidth, y, paintItemRegular)
            val rateStr = String.format(Locale.US, "%.2f", item.price)
            canvas.drawText(rateStr, paddingHorizontal + colItemWidth + colQtyWidth, y, paintItemRegular)

            val amtStr = String.format(Locale.US, "₹%.2f", item.amount)
            val amtW = paintItemBold.measureText(amtStr)
            canvas.drawText(amtStr, rightX - amtW, y, paintItemBold)
            y += 26f
        }

        y += 4f
        canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
        y += 20f

        // 6. Totals & Tax Breakdown
        drawTwoColText(canvas, "Subtotal / उपएकूण", String.format(Locale.US, "₹%.2f", receipt.subtotal), y, paintSubtitle, widthPx, paddingHorizontal)
        y += 22f

        if (receipt.tax > 0) {
            drawTwoColText(canvas, "GST / कर (5%)", String.format(Locale.US, "+₹%.2f", receipt.tax), y, paintSubtitle, widthPx, paddingHorizontal)
            y += 22f
        }
        if (receipt.discount > 0) {
            drawTwoColText(canvas, "Discount / सूट", String.format(Locale.US, "-₹%.2f", receipt.discount), y, paintSubtitle, widthPx, paddingHorizontal)
            y += 22f
        }

        y += 4f
        paintLine.strokeWidth = 3f
        canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
        paintLine.strokeWidth = 2f
        y += 26f

        // Grand Total (Big Bold)
        paintItemBold.textSize = if (isFourInch) 28f else 24f
        val grandTotalStr = String.format(Locale.US, "₹%.2f", receipt.grandTotal)
        canvas.drawText("GRAND TOTAL / एकूण", paddingHorizontal, y, paintItemBold)
        val gtW = paintItemBold.measureText(grandTotalStr)
        canvas.drawText(grandTotalStr, rightX - gtW, y, paintItemBold)
        paintItemBold.textSize = if (isFourInch) 22f else 19f
        y += 28f

        canvas.drawLine(paddingHorizontal, y, widthPx - paddingHorizontal, y, paintLine)
        y += 20f

        // 7. Payment info
        drawTwoColText(canvas, "Payment Mode", receipt.payment.mode, y, paintSubtitle, widthPx, paddingHorizontal)
        y += 22f
        if (receipt.payment.paid > 0) {
            drawTwoColText(canvas, "Paid / दिलेले", String.format(Locale.US, "₹%.2f", receipt.payment.paid), y, paintSubtitle, widthPx, paddingHorizontal)
            y += 22f
        }
        if (receipt.payment.change > 0) {
            drawTwoColText(canvas, "Change / परत", String.format(Locale.US, "₹%.2f", receipt.payment.change), y, paintSubtitle, widthPx, paddingHorizontal)
            y += 22f
        }
        receipt.payment.transactionRef?.let {
            drawTwoColText(canvas, "Txn Ref", it, y, paintSubtitle, widthPx, paddingHorizontal)
            y += 22f
        }

        y += 12f

        // 8. Footer note
        y = drawCenteredText(canvas, receipt.footer, y, paintSubtitle, widthPx)
        y += 12f

        // Crop bitmap to actual drawn height + 20px bottom margin
        val finalHeight = (y + 20f).toInt().coerceAtMost(heightPx)
        return Bitmap.createBitmap(bitmap, 0, 0, widthPx, finalHeight)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, y: Float, paint: TextPaint, widthPx: Int): Float {
        val textWidth = paint.measureText(text)
        val x = (widthPx - textWidth) / 2f
        canvas.drawText(text, x.coerceAtLeast(0f), y, paint)
        return y + paint.textSize + 6f
    }

    private fun drawTwoColText(canvas: Canvas, left: String, right: String, y: Float, paint: TextPaint, widthPx: Int, padding: Float) {
        canvas.drawText(left, padding, y, paint)
        val rightW = paint.measureText(right)
        canvas.drawText(right, widthPx - padding - rightW, y, paint)
    }
}

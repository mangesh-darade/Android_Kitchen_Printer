package com.posconnect.printer.escpos

import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.model.ReceiptData
import java.util.Locale

object ReceiptLayoutEngine {

    fun generateReceiptBytes(receipt: ReceiptData, profile: PrinterProfile): ByteArray {
        val isFourInch = profile.widthMm >= 100
        val isUnicodeReceipt = receipt.isUnicode || TextRasterizer.containsComplexUnicode(
            receipt.header.businessName + receipt.footer + receipt.items.joinToString { it.name }
        )

        val builder = EscPosCommandBuilder().initialize()

        if (isUnicodeReceipt) {
            // High fidelity graphical rasterization for Marathi / Hindi / Indian symbols / Logos
            val bitmap = TextRasterizer.rasterizeReceipt(receipt, isFourInch)
            builder.setAlignment(EscPosCommandBuilder.Alignment.CENTER)
            builder.printBitmap(bitmap)
            builder.lineFeed(1)

            // Print QR code if specified
            if (!receipt.qrCode.isNullOrBlank() && profile.qrSupported) {
                builder.setAlignment(EscPosCommandBuilder.Alignment.CENTER)
                builder.printQRCode(receipt.qrCode, moduleSize = if (isFourInch) 7 else 5)
                builder.lineFeed(1)
            }

            // Print Barcode if specified
            if (!receipt.barcode.isNullOrBlank() && profile.barcodeSupported) {
                builder.setAlignment(EscPosCommandBuilder.Alignment.CENTER)
                builder.printBarcode(receipt.barcode, height = 50, width = 2)
                builder.lineFeed(1)
            }

            if (profile.cutSupported) {
                builder.cut(partial = false)
            }
            if (profile.cashDrawerSupported) {
                builder.openCashDrawer(0)
            }

            return builder.build()
        }

        // Standard Text ESC/POS formatting
        val cols = profile.charactersPerLine
        builder.setAlignment(EscPosCommandBuilder.Alignment.CENTER)
        builder.setTextSize(doubleWidth = true, doubleHeight = true)
        builder.setBold(true)
        builder.textLine(receipt.header.businessName)
        builder.resetTextSize()
        builder.setBold(false)

        receipt.header.branchName?.let { builder.textLine(it) }
        if (receipt.header.address.isNotBlank()) builder.textLine(receipt.header.address)
        if (receipt.header.phone.isNotBlank()) builder.textLine("Phone: ${receipt.header.phone}")
        receipt.header.gstNumber?.let { builder.textLine("GST: $it") }

        builder.doubleHorizontalLine(cols)
        builder.setAlignment(EscPosCommandBuilder.Alignment.LEFT)
        builder.twoColumnRow("Invoice: ${receipt.header.invoiceNumber}", "Date: ${receipt.header.dateTime}", cols)
        builder.twoColumnRow("Cashier: ${receipt.header.cashier}", receipt.header.tableNumber?.let { "Table: $it" } ?: "", cols)
        if (receipt.customerName != null) {
            builder.twoColumnRow("Customer: ${receipt.customerName}", receipt.customerPhone ?: "", cols)
        }
        builder.horizontalLine(cols)

        // Item columns
        builder.setBold(true)
        builder.fourColumnRow("ITEM", "QTY", "PRICE", "TOTAL", cols)
        builder.setBold(false)
        builder.horizontalLine(cols)

        for (item in receipt.items) {
            val qtyStr = if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else String.format(Locale.US, "%.2f", item.qty)
            val priceStr = String.format(Locale.US, "%.2f", item.price)
            val totalStr = String.format(Locale.US, "%.2f", item.amount)
            builder.fourColumnRow(item.name, qtyStr, priceStr, totalStr, cols)
        }

        builder.horizontalLine(cols)
        builder.twoColumnRow("Subtotal", String.format(Locale.US, "%.2f", receipt.subtotal), cols)
        if (receipt.tax > 0) {
            builder.twoColumnRow("Tax", String.format(Locale.US, "+%.2f", receipt.tax), cols)
        }
        if (receipt.discount > 0) {
            builder.twoColumnRow("Discount", String.format(Locale.US, "-%.2f", receipt.discount), cols)
        }

        builder.doubleHorizontalLine(cols)
        builder.setBold(true)
        builder.setTextSize(doubleWidth = false, doubleHeight = true)
        builder.twoColumnRow("GRAND TOTAL", String.format(Locale.US, "INR %.2f", receipt.grandTotal), cols)
        builder.resetTextSize()
        builder.setBold(false)
        builder.horizontalLine(cols)

        builder.twoColumnRow("Payment Mode", receipt.payment.mode, cols)
        if (receipt.payment.paid > 0) {
            builder.twoColumnRow("Paid", String.format(Locale.US, "%.2f", receipt.payment.paid), cols)
        }
        if (receipt.payment.change > 0) {
            builder.twoColumnRow("Change", String.format(Locale.US, "%.2f", receipt.payment.change), cols)
        }

        builder.lineFeed(1)
        builder.setAlignment(EscPosCommandBuilder.Alignment.CENTER)
        builder.textLine(receipt.footer)
        builder.lineFeed(1)

        if (!receipt.qrCode.isNullOrBlank() && profile.qrSupported) {
            builder.printQRCode(receipt.qrCode, moduleSize = 5)
            builder.lineFeed(1)
        }

        if (!receipt.barcode.isNullOrBlank() && profile.barcodeSupported) {
            builder.printBarcode(receipt.barcode, height = 50, width = 2)
            builder.lineFeed(1)
        }

        if (profile.cutSupported) {
            builder.cut(partial = false)
        }

        if (profile.cashDrawerSupported) {
            builder.openCashDrawer(0)
        }

        return builder.build()
    }
}

package com.posconnect.printer.adapters

import android.graphics.Bitmap
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.escpos.EscPosCommandBuilder
import com.posconnect.printer.escpos.ReceiptLayoutEngine
import com.posconnect.printer.escpos.TextRasterizer
import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import com.posconnect.printer.model.ReceiptData
import com.posconnect.printer.transports.PrinterTransport
import org.json.JSONObject

open class GenericEscPosAdapter(
    override val transport: PrinterTransport,
    override val profile: PrinterProfile,
    override val brand: PrinterBrand = PrinterBrand.GENERIC_ESC_POS
) : PrinterAdapter {

    override suspend fun initialize(): PrinterResult {
        DiagnosticLogger.i(LogCategory.PRINTER, "EscPosAdapter", "Initializing ESC/POS adapter with transport ${transport.transportName}")
        val initBytes = EscPosCommandBuilder().initialize().build()
        return transport.send(initBytes)
    }

    override suspend fun connect(): PrinterResult = transport.connect()
    override suspend fun disconnect(): PrinterResult = transport.disconnect()
    override suspend fun isConnected(): Boolean = transport.isConnected()
    override suspend fun getStatus(): PrinterStatus = transport.readStatus()

    override suspend fun print(rawBytes: ByteArray): PrinterResult = transport.send(rawBytes)

    override suspend fun printReceipt(receipt: ReceiptData): PrinterResult {
        DiagnosticLogger.i(LogCategory.PRINTER, "EscPosAdapter", "Printing receipt (Items: ${receipt.items.size}, Total: ${receipt.grandTotal})")
        val bytes = ReceiptLayoutEngine.generateReceiptBytes(receipt, profile)
        return transport.send(bytes)
    }

    override suspend fun printText(text: String, isBold: Boolean, textSizeSp: Float): PrinterResult {
        return if (TextRasterizer.containsComplexUnicode(text)) {
            val widthPx = if (profile.widthMm >= 100) 832 else 576
            val bitmap = TextRasterizer.rasterizeText(text, widthPx = widthPx, textSizeSp = textSizeSp, isBold = isBold)
            val bytes = EscPosCommandBuilder().initialize().printBitmap(bitmap).lineFeed(2).build()
            transport.send(bytes)
        } else {
            val bytes = EscPosCommandBuilder().initialize().setBold(isBold).textLine(text).lineFeed(1).build()
            transport.send(bytes)
        }
    }

    override suspend fun printImage(bitmap: Bitmap): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().setAlignment(EscPosCommandBuilder.Alignment.CENTER).printBitmap(bitmap).lineFeed(2).build()
        return transport.send(bytes)
    }

    override suspend fun printBarcode(data: String): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().setAlignment(EscPosCommandBuilder.Alignment.CENTER).printBarcode(data).lineFeed(2).build()
        return transport.send(bytes)
    }

    override suspend fun printQRCode(data: String): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().setAlignment(EscPosCommandBuilder.Alignment.CENTER).printQRCode(data).lineFeed(2).build()
        return transport.send(bytes)
    }

    override suspend fun cutPaper(partial: Boolean): PrinterResult {
        val bytes = EscPosCommandBuilder().cut(partial).build()
        return transport.send(bytes)
    }

    override suspend fun openCashDrawer(): PrinterResult {
        val bytes = EscPosCommandBuilder().openCashDrawer(0).build()
        return transport.send(bytes)
    }

    override suspend fun testPrint(): PrinterResult {
        val sample = ReceiptData.sampleReceipt(isFourInch = profile.widthMm >= 100)
        return printReceipt(sample)
    }

    override fun getPrinterInfo(): JSONObject = JSONObject().apply {
        put("brand", brand.displayName)
        put("transport", transport.transportName)
        put("paperWidthMm", profile.widthMm)
        put("printableWidthMm", profile.printableWidthMm)
        put("charactersPerLine", profile.charactersPerLine)
        put("dpi", profile.dpi)
        put("supportsCut", profile.cutSupported)
        put("supportsCashDrawer", profile.cashDrawerSupported)
        put("supportsQr", profile.qrSupported)
        put("supportsBarcode", profile.barcodeSupported)
    }
}

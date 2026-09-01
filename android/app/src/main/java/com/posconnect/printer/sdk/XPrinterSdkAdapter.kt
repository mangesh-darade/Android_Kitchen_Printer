package com.posconnect.printer.sdk

import android.content.Context
import android.graphics.Bitmap
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.adapters.PrinterAdapter
import com.posconnect.printer.escpos.EscPosCommandBuilder
import com.posconnect.printer.escpos.KotTextFormatter
import com.posconnect.printer.escpos.ReceiptLayoutEngine
import com.posconnect.printer.escpos.TextRasterizer
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import com.posconnect.printer.model.ReceiptData
import com.posconnect.printer.transports.SdkManagedTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class XPrinterSdkAdapter(
    private val context: Context,
    private val config: PrinterConfig,
    override val profile: PrinterProfile,
) : PrinterAdapter {

    override val brand: PrinterBrand = PrinterBrand.XPRINTER
    override val transport = SdkManagedTransport("XPrinter SDK 3.2.0")

    override suspend fun initialize(): PrinterResult = connect()

    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        val ok = XPrinterSdkClient.connect(context, config)
        transport.markConnected(ok)
        if (ok) {
            DiagnosticLogger.i(LogCategory.SDK, "XPrinterSdk", "Connected via POSConnect")
            PrinterResult.success("XPrinter SDK connected")
        } else {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "XPrinter SDK connect failed")
        }
    }

    override suspend fun disconnect(): PrinterResult {
        XPrinterSdkClient.disconnect()
        transport.markConnected(false)
        return PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = XPrinterSdkClient.isConnected()

    override suspend fun getStatus(): PrinterStatus =
        if (isConnected()) PrinterStatus.onlineReady() else PrinterStatus.offline("XPrinter SDK not connected")

    override suspend fun print(rawBytes: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        val p = XPrinterSdkClient.posPrinter()
            ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            p.initializePrinter()
            p.sendData(rawBytes)
            PrinterResult.success("Sent ${rawBytes.size} bytes via XPrinter SDK")
        } catch (e: Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "XPrinter print failed: ${e.localizedMessage}")
        }
    }

    override suspend fun printReceipt(receipt: ReceiptData): PrinterResult {
        val bytes = ReceiptLayoutEngine.generateReceiptBytes(receipt, profile)
        return print(bytes)
    }

    override suspend fun printText(text: String, isBold: Boolean, textSizeSp: Float): PrinterResult {
        val formattedText = KotTextFormatter.format(text, profile.charactersPerLine)
        return if (TextRasterizer.containsComplexUnicode(text)) {
            val widthPx = if (profile.widthMm >= 100) 832 else 576
            val bitmap = TextRasterizer.rasterizeText(text, widthPx = widthPx, textSizeSp = textSizeSp, isBold = isBold)
            printImage(bitmap)
        } else {
            val p = XPrinterSdkClient.posPrinter()
                ?: return PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
            return withContext(Dispatchers.IO) {
                try {
                    p.initializePrinter()
                    p.printText(formattedText, 0, if (isBold) 1 else 0, 0)
                    p.feedLine(2)
                    PrinterResult.success("Text sent via XPrinter SDK")
                } catch (e: Exception) {
                    PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, e.localizedMessage ?: "Print failed")
                }
            }
        }
    }

    override suspend fun printImage(bitmap: Bitmap): PrinterResult = withContext(Dispatchers.IO) {
        val p = XPrinterSdkClient.posPrinter()
            ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            val widthDots = if (profile.widthMm >= 100) 832 else 576
            p.initializePrinter()
            p.printBitmap(bitmap, 1, widthDots)
            p.feedLine(2)
            PrinterResult.success("Image sent via XPrinter SDK")
        } catch (e: Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, e.localizedMessage ?: "Image failed")
        }
    }

    override suspend fun printBarcode(data: String): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().printBarcode(data).lineFeed(2).build()
        return print(bytes)
    }

    override suspend fun printQRCode(data: String): PrinterResult = withContext(Dispatchers.IO) {
        val p = XPrinterSdkClient.posPrinter()
            ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            p.initializePrinter()
            p.printQRCode(data, 4, 49, 1)
            p.feedLine(2)
            PrinterResult.success("QR sent via XPrinter SDK")
        } catch (e: Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, e.localizedMessage ?: "QR failed")
        }
    }

    override suspend fun cutPaper(partial: Boolean): PrinterResult = withContext(Dispatchers.IO) {
        val p = XPrinterSdkClient.posPrinter()
            ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            if (partial) p.cutHalfAndFeed(0) else p.cutPaper()
            PrinterResult.success("Cut sent via XPrinter SDK")
        } catch (e: Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, e.localizedMessage ?: "Cut failed")
        }
    }

    override suspend fun openCashDrawer(): PrinterResult = withContext(Dispatchers.IO) {
        val p = XPrinterSdkClient.posPrinter()
            ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            p.openCashBox(0)
            PrinterResult.success("Drawer opened via XPrinter SDK")
        } catch (e: Exception) {
            val bytes = EscPosCommandBuilder().openCashDrawer(0).build()
            print(bytes)
        }
    }

    override suspend fun testPrint(): PrinterResult =
        printReceipt(ReceiptData.sampleReceipt(isFourInch = profile.widthMm >= 100))

    override fun getPrinterInfo(): JSONObject = JSONObject().apply {
        put("brand", brand.displayName)
        put("transport", transport.transportName)
        put("sdkPrintPath", VendorSdkAvailability.printPath(config))
        put("sdkTechName", VendorSdkAvailability.sdkTechLabel(config))
    }
}

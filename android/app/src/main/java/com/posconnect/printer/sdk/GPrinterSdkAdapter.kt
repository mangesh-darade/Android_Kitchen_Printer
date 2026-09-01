package com.posconnect.printer.sdk

import android.content.Context
import android.graphics.Bitmap
import com.gprinter.sdk.command.print.EscCommand
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

class GPrinterSdkAdapter(
    private val context: Context,
    private val config: PrinterConfig,
    override val profile: PrinterProfile,
) : PrinterAdapter {

    override val brand: PrinterBrand = PrinterBrand.GPRINTER
    override val transport = SdkManagedTransport("GPrinter SDK 2.0")

    override suspend fun initialize(): PrinterResult = connect()

    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        val ok = GPrinterSdkClient.connect(context, config)
        transport.markConnected(ok)
        if (ok) {
            DiagnosticLogger.i(LogCategory.SDK, "GPrinterSdk", "Connected via Gprinter SDK")
            PrinterResult.success("GPrinter SDK connected")
        } else {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "GPrinter SDK connect failed")
        }
    }

    override suspend fun disconnect(): PrinterResult {
        GPrinterSdkClient.disconnect()
        transport.markConnected(false)
        return PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = GPrinterSdkClient.isConnected()

    override suspend fun getStatus(): PrinterStatus =
        if (isConnected()) PrinterStatus.onlineReady() else PrinterStatus.offline("GPrinter SDK not connected")

    override suspend fun print(rawBytes: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        val ok = GPrinterSdkClient.sendEsc(rawBytes)
        if (ok) {
            PrinterResult.success("Sent ${rawBytes.size} bytes via GPrinter SDK")
        } else {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "GPrinter send failed")
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
            withContext(Dispatchers.IO) {
                val cmd = EscCommand().apply {
                    addInitializePrinter()
                    if (isBold) {
                        addSelectPrintModes(
                            EscCommand.FONT.FONTA,
                            EscCommand.ENABLE.ON,
                            EscCommand.ENABLE.OFF,
                            EscCommand.ENABLE.OFF,
                            EscCommand.ENABLE.OFF,
                        )
                    }
                    addText("$formattedText\n")
                    addPrintAndFeedLines(2)
                }
                if (GPrinterSdkClient.sendEscCommand(cmd)) {
                    PrinterResult.success("Text sent via GPrinter SDK")
                } else {
                    PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "GPrinter text failed")
                }
            }
        }
    }

    override suspend fun printImage(bitmap: Bitmap): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().printBitmap(bitmap).lineFeed(2).build()
        return print(bytes)
    }

    override suspend fun printBarcode(data: String): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().printBarcode(data).lineFeed(2).build()
        return print(bytes)
    }

    override suspend fun printQRCode(data: String): PrinterResult {
        val bytes = EscPosCommandBuilder().initialize().printQRCode(data).lineFeed(2).build()
        return print(bytes)
    }

    override suspend fun cutPaper(partial: Boolean): PrinterResult = withContext(Dispatchers.IO) {
        val cmd = EscCommand().apply {
            if (partial) addCutAndFeedPaper(2) else addCutPaper()
        }
        if (GPrinterSdkClient.sendEscCommand(cmd)) {
            PrinterResult.success("Cut sent via GPrinter SDK")
        } else {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "GPrinter cut failed")
        }
    }

    override suspend fun openCashDrawer(): PrinterResult {
        val bytes = EscPosCommandBuilder().openCashDrawer(0).build()
        return print(bytes)
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

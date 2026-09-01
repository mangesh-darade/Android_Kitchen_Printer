package com.posconnect.printer.sdk

import android.content.Context
import android.graphics.Bitmap
import com.epson.epos2.Epos2Exception
import com.epson.epos2.printer.Printer
import com.posconnect.core.config.ConnectionType
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

class EpsonSdkAdapter(
    private val context: Context,
    private val config: PrinterConfig,
    override val profile: PrinterProfile,
) : PrinterAdapter {

    override val brand: PrinterBrand = PrinterBrand.EPSON
    override val transport = SdkManagedTransport("Epson ePOS SDK 2.32.0")

    private var printer: Printer? = null

    override suspend fun initialize(): PrinterResult = connect()

    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        try {
            disconnectInternal()
            val target = epsonTarget(config)
            val instance = Printer(Printer.TM_T88, Printer.MODEL_ANK, context.applicationContext)
            instance.connect(target, Printer.PARAM_DEFAULT)
            printer = instance
            transport.markConnected(true)
            DiagnosticLogger.i(LogCategory.SDK, "EpsonSdk", "Connected via ePOS2 to $target")
            PrinterResult.success("Epson ePOS SDK connected ($target)")
        } catch (e: Epos2Exception) {
            transport.markConnected(false)
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Epson SDK connect failed: ${e.errorStatus}")
        } catch (e: Exception) {
            transport.markConnected(false)
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Epson SDK connect failed: ${e.localizedMessage}")
        }
    }

    override suspend fun disconnect(): PrinterResult = withContext(Dispatchers.IO) {
        disconnectInternal()
        PrinterResult.success("Disconnected")
    }

    private fun disconnectInternal() {
        try {
            printer?.disconnect()
        } catch (_: Exception) {
        } finally {
            printer = null
            transport.markConnected(false)
        }
    }

    override suspend fun isConnected(): Boolean = printer != null

    override suspend fun getStatus(): PrinterStatus =
        if (printer != null) PrinterStatus.onlineReady() else PrinterStatus.offline("Epson SDK not connected")

    override suspend fun print(rawBytes: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        val p = printer ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            p.clearCommandBuffer()
            p.addCommand(rawBytes)
            p.sendData(Printer.PARAM_DEFAULT)
            PrinterResult.success("Sent ${rawBytes.size} bytes via Epson ePOS SDK")
        } catch (e: Epos2Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Epson print failed: ${e.errorStatus}")
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
            val bytes = EscPosCommandBuilder().initialize().setBold(isBold).textLine(formattedText).lineFeed(2).build()
            print(bytes)
        }
    }

    override suspend fun printImage(bitmap: Bitmap): PrinterResult = withContext(Dispatchers.IO) {
        val p = printer ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            p.clearCommandBuffer()
            p.addImage(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Printer.COLOR_1, Printer.MODE_MONO, Printer.HALFTONE_DITHER,
                Printer.PARAM_DEFAULT.toDouble(), Printer.COMPRESS_AUTO,
            )
            p.addFeedLine(2)
            p.sendData(Printer.PARAM_DEFAULT)
            PrinterResult.success("Image sent via Epson ePOS SDK")
        } catch (e: Epos2Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Epson image failed: ${e.errorStatus}")
        }
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
        val p = printer ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Not connected")
        try {
            p.clearCommandBuffer()
            p.addCut(if (partial) Printer.CUT_FEED else Printer.FULL_CUT_FEED)
            p.sendData(Printer.PARAM_DEFAULT)
            PrinterResult.success("Cut sent via Epson ePOS SDK")
        } catch (e: Epos2Exception) {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Epson cut failed: ${e.errorStatus}")
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
        put("paperWidthMm", profile.widthMm)
    }

    companion object {
        fun epsonTarget(config: PrinterConfig): String = when (config.connectionType) {
            ConnectionType.LAN -> "TCP:${config.ip}"
            ConnectionType.BLUETOOTH, ConnectionType.BLE -> "BT:${config.macAddress}"
            ConnectionType.USB -> "USB:${config.deviceName.ifBlank { "0" }}"
            else -> "TCP:${config.ip}"
        }
    }
}

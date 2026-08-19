package com.posconnect.printer.transports

import android.os.Build
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus

object BuiltInPrinterDetector {
    fun isAvailable(): Boolean {
        val manufacturer = Build.MANUFACTURER.uppercase()
        val brand = Build.BRAND.uppercase()
        val model = Build.MODEL.uppercase()
        return listOf(manufacturer, brand, model).any {
            it.contains("SUNMI") || it.contains("LANDI") || it.contains("NEXGO")
        }
    }

    fun vendorName(): String = Build.MANUFACTURER
}

class UnsupportedTransport(private val reason: String) : PrinterTransport {
    override val transportName: String = "Unsupported"

    override suspend fun connect(): PrinterResult =
        PrinterResult.error(PrinterErrorCodes.UNSUPPORTED_PRINTER, reason)

    override suspend fun disconnect(): PrinterResult = PrinterResult.success("Disconnected")

    override suspend fun isConnected(): Boolean = false

    override suspend fun send(data: ByteArray): PrinterResult =
        PrinterResult.error(PrinterErrorCodes.UNSUPPORTED_PRINTER, reason)

    override suspend fun readStatus(): PrinterStatus = PrinterStatus.offline(reason)
}

/**
 * Built-in POS printer transport.
 * Official SUNMI InnerPrinter AIDL is not bundled in this build (no unofficial SDK download).
 * When a SUNMI device is detected we report that clearly instead of pretending VirtualPrinter is hardware.
 */
class BuiltInPrinterTransport : PrinterTransport {
    override val transportName: String = "Built-in POS printer (${BuiltInPrinterDetector.vendorName()})"

    override suspend fun connect(): PrinterResult {
        if (!BuiltInPrinterDetector.isAvailable()) {
            return PrinterResult.error(
                PrinterErrorCodes.UNSUPPORTED_PRINTER,
                "This device does not expose a built-in thermal printer."
            )
        }
        return PrinterResult.error(
            PrinterErrorCodes.UNSUPPORTED_PRINTER,
            "Built-in printer hardware was detected (${BuiltInPrinterDetector.vendorName()}), but the official vendor printer SDK is not bundled yet. Add the manufacturer AAR to enable InnerPrinter."
        )
    }

    override suspend fun disconnect(): PrinterResult = PrinterResult.success("Disconnected")
    override suspend fun isConnected(): Boolean = false
    override suspend fun send(data: ByteArray): PrinterResult = connect()
    override suspend fun readStatus(): PrinterStatus = PrinterStatus.offline("Built-in SDK not bundled")
}

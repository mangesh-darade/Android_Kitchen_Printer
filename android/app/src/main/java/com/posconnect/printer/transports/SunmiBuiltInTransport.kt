package com.posconnect.printer.transports

import android.content.Context
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Built-in SUNMI thermal printer via official PrinterLibrary (Maven: com.sunmi:printerlibrary).
 */
class SunmiBuiltInTransport(private val context: Context) : PrinterTransport {
    override val transportName: String =
        "SUNMI InnerPrinter (${BuiltInPrinterDetector.vendorName()})"

    @Volatile
    private var printerService: SunmiPrinterService? = null

    override suspend fun connect(): PrinterResult {
        if (!BuiltInPrinterDetector.isAvailable()) {
            return PrinterResult.error(
                PrinterErrorCodes.UNSUPPORTED_PRINTER,
                "This device does not expose a built-in SUNMI thermal printer."
            )
        }

        if (printerService != null) {
            return PrinterResult.success("SUNMI printer already connected")
        }

        return withContext(Dispatchers.Main) {
            val bound = withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine { cont ->
                    try {
                        InnerPrinterManager.getInstance().bindService(
                            context.applicationContext,
                            object : InnerPrinterCallback() {
                                override fun onConnected(service: SunmiPrinterService?) {
                                    printerService = service
                                    if (service != null) {
                                        cont.resume(PrinterResult.success("SUNMI InnerPrinter connected"))
                                    } else {
                                        cont.resume(
                                            PrinterResult.error(
                                                PrinterErrorCodes.PRINTER_OFFLINE,
                                                "SUNMI printer service returned null"
                                            )
                                        )
                                    }
                                }

                                override fun onDisconnected() {
                                    printerService = null
                                }
                            }
                        )
                    } catch (e: Exception) {
                        cont.resume(
                            PrinterResult.error(
                                PrinterErrorCodes.UNSUPPORTED_PRINTER,
                                "SUNMI PrinterLibrary bind failed: ${e.localizedMessage}"
                            )
                        )
                    }
                }
            }

            bound ?: PrinterResult.error(
                PrinterErrorCodes.TIMEOUT,
                "Timed out connecting to SUNMI InnerPrinter"
            )
        }
    }

    override suspend fun disconnect(): PrinterResult {
        printerService = null
        return PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = printerService != null

    override suspend fun send(data: ByteArray): PrinterResult {
        val service = printerService ?: return connect().let { connectResult ->
            if (!connectResult.success) {
                connectResult
            } else {
                send(data)
            }
        }

        return try {
            withContext(Dispatchers.IO) {
                service.sendRAWData(data, null)
            }
            PrinterResult.success("Sent ${data.size} bytes to SUNMI InnerPrinter")
        } catch (e: Exception) {
            PrinterResult.error(
                PrinterErrorCodes.PRINTER_OFFLINE,
                "SUNMI print failed: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun readStatus(): PrinterStatus {
        val service = printerService
        return if (service == null) {
            PrinterStatus.offline("SUNMI InnerPrinter not connected")
        } else {
            try {
                val status = service.updatePrinterState()
                when (status) {
                    1 -> PrinterStatus(connected = true, ready = true)
                    2 -> PrinterStatus(connected = true, ready = false, paperOut = true, error = "Paper out")
                    3 -> PrinterStatus(connected = true, ready = false, coverOpen = true, error = "Cover open")
                    else -> PrinterStatus(connected = true, ready = true)
                }
            } catch (e: Exception) {
                PrinterStatus.offline(e.localizedMessage ?: "Status unavailable")
            }
        }
    }
}

package com.posconnect.printer.transports

import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus

/** Placeholder transport when the vendor SDK owns the connection. */
class SdkManagedTransport(private val label: String) : PrinterTransport {
    override val transportName: String = label

    @Volatile
    private var connected = false

    fun markConnected(value: Boolean) {
        connected = value
    }

    override suspend fun connect(): PrinterResult {
        connected = true
        return PrinterResult.success("SDK transport ready")
    }

    override suspend fun disconnect(): PrinterResult {
        connected = false
        return PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun send(data: ByteArray): PrinterResult =
        PrinterResult.error("UNSUPPORTED_PRINTER", "Use vendor SDK adapter for printing")

    override suspend fun readStatus(): PrinterStatus =
        if (connected) PrinterStatus.onlineReady() else PrinterStatus.offline("Not connected")
}

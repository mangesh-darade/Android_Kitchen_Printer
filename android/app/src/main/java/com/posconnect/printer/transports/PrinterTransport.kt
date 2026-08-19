package com.posconnect.printer.transports

import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus

interface PrinterTransport {
    val transportName: String

    suspend fun connect(): PrinterResult
    suspend fun disconnect(): PrinterResult
    suspend fun isConnected(): Boolean
    suspend fun send(data: ByteArray): PrinterResult
    suspend fun readStatus(): PrinterStatus
}

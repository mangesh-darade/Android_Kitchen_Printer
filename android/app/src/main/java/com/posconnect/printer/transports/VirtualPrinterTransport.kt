package com.posconnect.printer.transports

import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VirtualPrintJob(
    val id: String = "JOB-${System.currentTimeMillis() % 10000}",
    val timestamp: Long = System.currentTimeMillis(),
    val byteCount: Int,
    val hexPreview: String,
    val textExtract: String
) {
    fun formatTime(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
}

class VirtualPrinterTransport : PrinterTransport {
    override val transportName: String = "Virtual Simulator (Test Mode)"

    private var connected: Boolean = true

    companion object {
        private val _jobsFlow = MutableStateFlow<List<VirtualPrintJob>>(emptyList())
        val jobsFlow: StateFlow<List<VirtualPrintJob>> = _jobsFlow.asStateFlow()

        fun clearJobs() {
            _jobsFlow.value = emptyList()
        }
    }

    override suspend fun connect(): PrinterResult {
        connected = true
        DiagnosticLogger.i(LogCategory.PRINTER, "VirtualPrinter", "Virtual printer connected")
        return PrinterResult.success("Connected to Virtual Printer Simulator")
    }

    override suspend fun disconnect(): PrinterResult {
        connected = false
        DiagnosticLogger.d(LogCategory.PRINTER, "VirtualPrinter", "Virtual printer disconnected")
        return PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun send(data: ByteArray): PrinterResult {
        val hexPreview = data.take(32).joinToString(" ") { String.format("%02X", it) }
        val textExtract = String(data.filter { it in 32..126 || it == 10.toByte() }.toByteArray(), Charsets.US_ASCII)

        val job = VirtualPrintJob(
            byteCount = data.size,
            hexPreview = if (data.size > 32) "$hexPreview ... (${data.size} bytes)" else hexPreview,
            textExtract = textExtract.trim().take(300)
        )

        val current = _jobsFlow.value.toMutableList()
        current.add(0, job)
        if (current.size > 50) current.removeAt(current.size - 1)
        _jobsFlow.value = current

        DiagnosticLogger.i(LogCategory.PRINTER, "VirtualPrinter", "Virtual print job accepted: ${data.size} bytes")
        return PrinterResult.success("Virtual print completed (${data.size} bytes captured)")
    }

    override suspend fun readStatus(): PrinterStatus {
        return PrinterStatus.onlineReady()
    }
}

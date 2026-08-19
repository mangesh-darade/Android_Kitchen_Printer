package com.posconnect.printer.transports

import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TcpTransport(
    private val ipAddress: String,
    private val port: Int = 9100,
    private val timeoutMs: Int = 3500
) : PrinterTransport {

    override val transportName: String = "TCP/LAN ($ipAddress:$port)"

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        try {
            disconnect()
            DiagnosticLogger.i(LogCategory.NETWORK, "TcpTransport", "Connecting to $ipAddress:$port (timeout=${timeoutMs}ms)")
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(ipAddress, port), timeoutMs)
            newSocket.soTimeout = timeoutMs
            newSocket.tcpNoDelay = true
            outputStream = newSocket.getOutputStream()
            socket = newSocket
            DiagnosticLogger.i(LogCategory.NETWORK, "TcpTransport", "Successfully connected to $ipAddress:$port")
            PrinterResult.success("Connected to printer at $ipAddress:$port")
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.NETWORK, "TcpTransport", "Connection failed to $ipAddress:$port: ${e.message}")
            disconnect()
            PrinterResult.error(PrinterErrorCodes.NETWORK_ERROR, "Unable to connect to $ipAddress:$port: ${e.localizedMessage}")
        }
    }

    override suspend fun disconnect(): PrinterResult = withContext(Dispatchers.IO) {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
        DiagnosticLogger.d(LogCategory.NETWORK, "TcpTransport", "Disconnected from $ipAddress:$port")
        PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val s = socket
        s != null && s.isConnected && !s.isClosed && !s.isOutputShutdown
    }

    override suspend fun send(data: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        try {
            if (!isConnected()) {
                val connRes = connect()
                if (!connRes.success) return@withContext connRes
            }

            val stream = outputStream ?: return@withContext PrinterResult.error(
                PrinterErrorCodes.PRINTER_OFFLINE, "Printer output stream unavailable"
            )

            stream.write(data)
            stream.flush()
            DiagnosticLogger.i(LogCategory.PRINTER, "TcpTransport", "Sent ${data.size} bytes successfully to $ipAddress:$port")
            PrinterResult.success("Printed ${data.size} bytes successfully")
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.PRINTER, "TcpTransport", "Error sending bytes: ${e.message}")
            disconnect()
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Print failed: ${e.localizedMessage}")
        }
    }

    override suspend fun readStatus(): PrinterStatus = withContext(Dispatchers.IO) {
        if (isConnected()) {
            PrinterStatus.onlineReady()
        } else {
            val testConn = connect()
            if (testConn.success) {
                PrinterStatus.onlineReady()
            } else {
                PrinterStatus.offline(testConn.message)
            }
        }
    }
}

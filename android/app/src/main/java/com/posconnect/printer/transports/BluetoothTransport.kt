package com.posconnect.printer.transports

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class BluetoothTransport(
    private val context: Context,
    private val macAddress: String
) : PrinterTransport {

    override val transportName: String = "Bluetooth Classic ($macAddress)"

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPort SPP
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            DiagnosticLogger.e(LogCategory.BLUETOOTH, "BluetoothTransport", "Bluetooth permission not granted")
            return@withContext PrinterResult.error(PrinterErrorCodes.BLUETOOTH_PERMISSION_DENIED, "Bluetooth permission not granted")
        }

        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            DiagnosticLogger.e(LogCategory.BLUETOOTH, "BluetoothTransport", "Bluetooth adapter is disabled or unavailable")
            return@withContext PrinterResult.error(PrinterErrorCodes.BLUETOOTH_DISABLED, "Bluetooth is turned off on this device")
        }

        try {
            disconnect()
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            DiagnosticLogger.i(LogCategory.BLUETOOTH, "BluetoothTransport", "Connecting to Bluetooth printer ${device.name ?: macAddress}")

            adapter.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()

            outputStream = socket.outputStream
            bluetoothSocket = socket
            DiagnosticLogger.i(LogCategory.BLUETOOTH, "BluetoothTransport", "Connected to Bluetooth printer ${device.name ?: macAddress}")
            PrinterResult.success("Connected to ${device.name ?: macAddress}")
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.BLUETOOTH, "BluetoothTransport", "Failed to connect: ${e.message}")
            disconnect()
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Bluetooth connection failed: ${e.localizedMessage}")
        }
    }

    override suspend fun disconnect(): PrinterResult = withContext(Dispatchers.IO) {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        outputStream = null
        bluetoothSocket = null
        DiagnosticLogger.d(LogCategory.BLUETOOTH, "BluetoothTransport", "Disconnected Bluetooth socket")
        PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val s = bluetoothSocket
        s != null && s.isConnected
    }

    override suspend fun send(data: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        try {
            if (!isConnected()) {
                val connRes = connect()
                if (!connRes.success) return@withContext connRes
            }

            val stream = outputStream ?: return@withContext PrinterResult.error(
                PrinterErrorCodes.PRINTER_OFFLINE, "Bluetooth output stream not available"
            )

            stream.write(data)
            stream.flush()
            DiagnosticLogger.i(LogCategory.PRINTER, "BluetoothTransport", "Sent ${data.size} bytes over Bluetooth")
            PrinterResult.success("Printed ${data.size} bytes successfully")
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.PRINTER, "BluetoothTransport", "Print error: ${e.message}")
            disconnect()
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Bluetooth print failed: ${e.localizedMessage}")
        }
    }

    override suspend fun readStatus(): PrinterStatus = withContext(Dispatchers.IO) {
        if (isConnected()) {
            PrinterStatus.onlineReady()
        } else {
            val res = connect()
            if (res.success) PrinterStatus.onlineReady() else PrinterStatus.offline(res.message)
        }
    }
}

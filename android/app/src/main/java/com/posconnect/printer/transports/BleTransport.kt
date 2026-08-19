package com.posconnect.printer.transports

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class BleTransport(
    private val context: Context,
    private val deviceAddress: String
) : PrinterTransport {

    override val transportName: String = "BLE ($deviceAddress)"

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var connected: Boolean = false

    // Common BLE Thermal Printer Service & Characteristic UUIDs
    private val possibleServiceUuids = listOf(
        UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"), // ISSC Transparent TX/RX
        UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
        UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    )

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        if (!hasBlePermissions()) {
            return@withContext PrinterResult.error(PrinterErrorCodes.BLUETOOTH_PERMISSION_DENIED, "BLE Bluetooth permission not granted")
        }

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            return@withContext PrinterResult.error(PrinterErrorCodes.BLUETOOTH_DISABLED, "Bluetooth is disabled")
        }

        disconnect()

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "Invalid BLE address: ${e.message}")
        }

        val connectSuccess = withTimeoutOrNull(6000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            connected = true
                            gatt?.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            connected = false
                            if (cont.isActive) cont.resume(false)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                            for (service in gatt.services) {
                                for (ch in service.characteristics) {
                                    val props = ch.properties
                                    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                                    ) {
                                        writeCharacteristic = ch
                                        break
                                    }
                                }
                                if (writeCharacteristic != null) break
                            }
                            if (cont.isActive) cont.resume(writeCharacteristic != null)
                        } else {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                })
            }
        } ?: false

        if (connectSuccess) {
            DiagnosticLogger.i(LogCategory.BLUETOOTH, "BleTransport", "BLE printer connected: $deviceAddress")
            PrinterResult.success("Connected to BLE printer")
        } else {
            disconnect()
            DiagnosticLogger.e(LogCategory.BLUETOOTH, "BleTransport", "BLE printer connection timed out")
            PrinterResult.error(PrinterErrorCodes.TIMEOUT, "BLE printer connection timed out")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect(): PrinterResult = withContext(Dispatchers.IO) {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        writeCharacteristic = null
        connected = false
        PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = connected && writeCharacteristic != null

    @SuppressLint("MissingPermission")
    override suspend fun send(data: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            val conn = connect()
            if (!conn.success) return@withContext conn
        }

        val gatt = bluetoothGatt ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "GATT is null")
        val char = writeCharacteristic ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Write characteristic not found")

        // BLE MTU packet chunking (typically 20-byte chunks for legacy BLE, or up to MTU)
        val chunkSize = 20
        var offset = 0
        while (offset < data.size) {
            val len = (data.size - offset).coerceAtMost(chunkSize)
            val chunk = ByteArray(len)
            System.arraycopy(data, offset, chunk, 0, len)

            char.value = chunk
            gatt.writeCharacteristic(char)
            Thread.sleep(15) // small delay to prevent buffer overrun
            offset += len
        }

        DiagnosticLogger.i(LogCategory.PRINTER, "BleTransport", "Sent ${data.size} bytes over BLE")
        PrinterResult.success("Printed ${data.size} bytes successfully")
    }

    override suspend fun readStatus(): PrinterStatus = withContext(Dispatchers.IO) {
        if (isConnected()) PrinterStatus.onlineReady() else PrinterStatus.offline("BLE not connected")
    }
}

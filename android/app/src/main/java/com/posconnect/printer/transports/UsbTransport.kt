package com.posconnect.printer.transports

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbTransport(
    private val context: Context,
    private val vendorId: Int = 0,
    private val productId: Int = 0,
    private val deviceName: String = ""
) : PrinterTransport {

    override val transportName: String = "USB Direct (${if (deviceName.isNotEmpty()) deviceName else "VID:$vendorId PID:$productId"})"

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var bulkOutEndpoint: UsbEndpoint? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "com.posconnect.app.USB_PERMISSION"

        fun findAttachedUsbPrinters(context: Context): List<UsbDevice> {
            val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
            val result = mutableListOf<UsbDevice>()
            for ((_, device) in manager.deviceList) {
                if (isPrinterDevice(device)) {
                    result.add(device)
                }
            }
            return result
        }

        fun isPrinterDevice(device: UsbDevice): Boolean {
            if (device.deviceClass == UsbConstants.USB_CLASS_PRINTER) return true
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
            }
            // Common POS printer vendor IDs
            val posVendorIds = setOf(0x04b8, 0x0519, 0x0fe6, 0x1fc9, 0x6868, 0x20d1)
            if (posVendorIds.contains(device.vendorId)) return true
            return false
        }
    }

    override suspend fun connect(): PrinterResult = withContext(Dispatchers.IO) {
        val manager = usbManager ?: return@withContext PrinterResult.error(PrinterErrorCodes.USB_PERMISSION_DENIED, "USB service unavailable")
        val deviceList = manager.deviceList

        var targetDevice: UsbDevice? = null
        for ((_, dev) in deviceList) {
            if (deviceName.isNotEmpty() && dev.deviceName == deviceName) {
                targetDevice = dev
                break
            }
            if (vendorId != 0 && dev.vendorId == vendorId && (productId == 0 || dev.productId == productId)) {
                targetDevice = dev
                break
            }
            if (isPrinterDevice(dev)) {
                targetDevice = dev
                break
            }
        }

        val device = targetDevice ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No USB printer found")

        if (!manager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            manager.requestPermission(device, permissionIntent)
            DiagnosticLogger.w(LogCategory.USB, "UsbTransport", "Requested USB permission for ${device.deviceName}")
            return@withContext PrinterResult.error(PrinterErrorCodes.USB_PERMISSION_DENIED, "USB permission requested. Please accept the prompt and retry.")
        }

        disconnect()

        var foundInterface: UsbInterface? = null
        var foundEndpoint: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    foundInterface = iface
                    foundEndpoint = ep
                    break
                }
            }
            if (foundEndpoint != null) break
        }

        if (foundInterface == null || foundEndpoint == null) {
            return@withContext PrinterResult.error(PrinterErrorCodes.UNSUPPORTED_PRINTER, "No Bulk-Out USB endpoint found on printer")
        }

        val conn = manager.openDevice(device) ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Failed to open USB device")
        if (!conn.claimInterface(foundInterface, true)) {
            conn.close()
            return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_BUSY, "Failed to claim USB interface")
        }

        usbConnection = conn
        usbInterface = foundInterface
        bulkOutEndpoint = foundEndpoint

        DiagnosticLogger.i(LogCategory.USB, "UsbTransport", "Connected to USB printer: ${device.deviceName} (VID: ${device.vendorId})")
        PrinterResult.success("Connected to USB printer")
    }

    override suspend fun disconnect(): PrinterResult = withContext(Dispatchers.IO) {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        usbInterface = null
        bulkOutEndpoint = null
        PrinterResult.success("Disconnected")
    }

    override suspend fun isConnected(): Boolean = usbConnection != null && bulkOutEndpoint != null

    override suspend fun send(data: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            val conn = connect()
            if (!conn.success) return@withContext conn
        }

        val conn = usbConnection ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "USB connection lost")
        val ep = bulkOutEndpoint ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "USB endpoint lost")

        val timeoutMs = 5000
        val transferred = conn.bulkTransfer(ep, data, data.size, timeoutMs)
        if (transferred >= 0) {
            DiagnosticLogger.i(LogCategory.PRINTER, "UsbTransport", "Sent $transferred / ${data.size} bytes over USB")
            PrinterResult.success("Printed $transferred bytes")
        } else {
            DiagnosticLogger.e(LogCategory.PRINTER, "UsbTransport", "USB bulkTransfer failed with return code $transferred")
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "USB Transfer failed")
        }
    }

    override suspend fun readStatus(): PrinterStatus = withContext(Dispatchers.IO) {
        if (isConnected()) PrinterStatus.onlineReady() else PrinterStatus.offline("USB printer not connected")
    }
}

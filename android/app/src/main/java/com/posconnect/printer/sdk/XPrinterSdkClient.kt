package com.posconnect.printer.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import net.posprinter.IConnectListener
import net.posprinter.IDeviceConnection
import net.posprinter.POSConnect
import net.posprinter.POSPrinter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object XPrinterSdkClient {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false

    @Volatile
    private var connection: IDeviceConnection? = null

    @Volatile
    private var printer: POSPrinter? = null

    fun ensureInit(context: Context) {
        if (!VendorSdkAvailability.xprinter) return
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                POSConnect.init(context.applicationContext)
                initialized = true
                DiagnosticLogger.i(LogCategory.SDK, "XPrinterSdk", "POSConnect initialized")
            }
        }
    }

    fun connect(context: Context, config: PrinterConfig): Boolean {
        ensureInit(context)
        disconnect()
        val deviceType = when (config.connectionType) {
            ConnectionType.LAN -> POSConnect.DEVICE_TYPE_ETHERNET
            ConnectionType.BLUETOOTH, ConnectionType.BLE -> POSConnect.DEVICE_TYPE_BLUETOOTH
            ConnectionType.USB -> POSConnect.DEVICE_TYPE_USB
            else -> POSConnect.DEVICE_TYPE_ETHERNET
        }
        val address = when (config.connectionType) {
            ConnectionType.LAN -> {
                if (config.port > 0 && config.port != 9100) "${config.ip}:${config.port}" else config.ip
            }
            ConnectionType.BLUETOOTH, ConnectionType.BLE -> config.macAddress.uppercase()
            ConnectionType.USB -> config.deviceName.ifBlank { "${config.usbVendorId}:${config.usbProductId}" }
            else -> config.ip
        }
        val conn = POSConnect.createDevice(deviceType) ?: return false
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val replied = AtomicBoolean(false)
        val listener = IConnectListener { code, _, _ ->
            when (code) {
                POSConnect.CONNECT_SUCCESS -> {
                    if (replied.compareAndSet(false, true)) {
                        connection = conn
                        printer = POSPrinter(conn)
                        ok.set(true)
                        latch.countDown()
                    }
                }
                POSConnect.CONNECT_FAIL -> {
                    if (replied.compareAndSet(false, true)) {
                        try {
                            conn.close()
                        } catch (_: Exception) {
                        }
                        latch.countDown()
                    }
                }
            }
        }
        mainHandler.post {
            try {
                conn.connect(address, listener)
            } catch (e: Exception) {
                if (replied.compareAndSet(false, true)) {
                    latch.countDown()
                }
            }
        }
        latch.await(15, TimeUnit.SECONDS)
        return ok.get()
    }

    fun disconnect() {
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
        printer = null
    }

    fun isConnected(): Boolean = connection?.isConnect == true

    fun posPrinter(): POSPrinter? = printer
}

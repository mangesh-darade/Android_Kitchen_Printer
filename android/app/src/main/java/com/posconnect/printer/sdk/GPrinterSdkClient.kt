package com.posconnect.printer.sdk

import android.content.Context
import com.gprinter.sdk.ConnectMethod
import com.gprinter.sdk.Gprinter
import com.gprinter.sdk.command.print.EscCommand
import com.gprinter.sdk.model.Device
import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object GPrinterSdkClient {
    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (!VendorSdkAvailability.gprinter) return
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                Gprinter.init(context.applicationContext as android.app.Application)
                initialized = true
                DiagnosticLogger.i(LogCategory.SDK, "GPrinterSdk", "Gprinter SDK initialized")
            }
        }
    }

    fun connect(context: Context, config: PrinterConfig): Boolean {
        ensureInit(context)
        disconnect()
        val method = when (config.connectionType) {
            ConnectionType.LAN -> ConnectMethod.WIFI
            ConnectionType.BLUETOOTH, ConnectionType.BLE -> ConnectMethod.BLUETOOTH
            ConnectionType.USB -> ConnectMethod.USB
            else -> ConnectMethod.WIFI
        }
        val device = Device(method).apply {
            when (config.connectionType) {
                ConnectionType.LAN -> {
                    address = config.ip
                    port = if (config.port > 0) config.port else 9100
                }
                ConnectionType.BLUETOOTH, ConnectionType.BLE -> {
                    mac = config.macAddress
                    address = config.macAddress
                }
                ConnectionType.USB -> {
                    name = config.deviceName
                    address = config.deviceName
                }
                else -> {
                    address = config.ip
                    port = config.port
                }
            }
        }
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        try {
            val connection = Gprinter.connect(device)
            connection?.connect()
            // Gprinter.connect is async; poll briefly for isConnected
            repeat(30) {
                if (Gprinter.isConnected()) {
                    ok.set(true)
                    return@repeat
                }
                Thread.sleep(200)
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(LogCategory.SDK, "GPrinterSdk", "Connect error: ${e.localizedMessage}")
        } finally {
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
        return ok.get() || Gprinter.isConnected()
    }

    fun disconnect() {
        try {
            Gprinter.disconnect()
        } catch (_: Exception) {
        }
    }

    fun isConnected(): Boolean = try {
        Gprinter.isConnected()
    } catch (_: Exception) {
        false
    }

    fun sendEsc(bytes: ByteArray): Boolean = try {
        Gprinter.send(bytes)
    } catch (_: Exception) {
        false
    }

    fun sendEscCommand(command: EscCommand): Boolean = try {
        Gprinter.send(command)
    } catch (_: Exception) {
        false
    }
}

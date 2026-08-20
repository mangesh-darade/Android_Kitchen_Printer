package com.posconnect.printer.registry

import android.content.Context
import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.adapters.EpsonAdapter
import com.posconnect.printer.adapters.GPrinterAdapter
import com.posconnect.printer.adapters.GenericEscPosAdapter
import com.posconnect.printer.adapters.PrinterAdapter
import com.posconnect.printer.adapters.RongtaAdapter
import com.posconnect.printer.adapters.StarAdapter
import com.posconnect.printer.adapters.CustomVendorAdapter
import com.posconnect.printer.adapters.SunmiAdapter
import com.posconnect.printer.adapters.XPrinterAdapter
import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.transports.BleTransport
import com.posconnect.printer.transports.BluetoothTransport
import com.posconnect.printer.transports.BuiltInPrinterDetector
import com.posconnect.printer.transports.SunmiBuiltInTransport
import com.posconnect.printer.transports.PrinterTransport
import com.posconnect.printer.transports.TcpTransport
import com.posconnect.printer.transports.UnsupportedTransport
import com.posconnect.printer.transports.UsbTransport
import com.posconnect.printer.sdk.VendorAdapterFactory

object PrinterRegistry {
    private val factoryMap = mutableMapOf<PrinterBrand, (PrinterTransport, PrinterProfile) -> PrinterAdapter>()

    init {
        register(PrinterBrand.GENERIC_ESC_POS) { t, p -> GenericEscPosAdapter(t, p) }
        register(PrinterBrand.EPSON) { t, p -> EpsonAdapter(t, p) }
        register(PrinterBrand.STAR) { t, p -> StarAdapter(t, p) }
        register(PrinterBrand.SUNMI) { t, p -> SunmiAdapter(t, p) }
        register(PrinterBrand.XPRINTER) { t, p -> XPrinterAdapter(t, p) }
        register(PrinterBrand.RONGTA) { t, p -> RongtaAdapter(t, p) }
        register(PrinterBrand.GPRINTER) { t, p -> GPrinterAdapter(t, p) }
        register(PrinterBrand.CUSTOM) { t, p -> CustomVendorAdapter(t, p) }
    }

    fun register(brand: PrinterBrand, factory: (PrinterTransport, PrinterProfile) -> PrinterAdapter) {
        factoryMap[brand] = factory
    }

    fun createAdapter(brand: PrinterBrand, transport: PrinterTransport, profile: PrinterProfile): PrinterAdapter {
        val factory = factoryMap[brand] ?: factoryMap[PrinterBrand.GENERIC_ESC_POS]!!
        return factory(transport, profile)
    }
}

object PrinterFactory {

    fun createTransport(context: Context, config: PrinterConfig): PrinterTransport {
        DiagnosticLogger.i(
            LogCategory.PRINTER,
            "PrinterFactory",
            "Creating transport for ${config.connectionType} (${config.brand})"
        )

        return when (config.connectionType) {
            ConnectionType.LAN -> {
                TcpTransport(ipAddress = config.ip, port = config.port)
            }
            ConnectionType.BLUETOOTH -> {
                if (config.macAddress.isBlank()) {
                    UnsupportedTransport("No Bluetooth MAC configured")
                } else {
                    BluetoothTransport(context = context, macAddress = config.macAddress)
                }
            }
            ConnectionType.BLE -> {
                if (config.macAddress.isBlank()) {
                    UnsupportedTransport("No BLE address configured")
                } else {
                    BleTransport(context = context, deviceAddress = config.macAddress)
                }
            }
            ConnectionType.USB -> {
                UsbTransport(
                    context = context,
                    vendorId = config.usbVendorId,
                    productId = config.usbProductId,
                    deviceName = config.deviceName
                )
            }
            ConnectionType.VENDOR -> {
                TcpTransport(ipAddress = config.ip, port = config.port)
            }
            ConnectionType.BUILTIN -> {
                if (BuiltInPrinterDetector.isAvailable()) {
                    DiagnosticLogger.i(
                        LogCategory.SDK,
                        "PrinterFactory",
                        "Built-in SUNMI printer — using SUNMI PrinterLibrary 1.0.24"
                    )
                    SunmiBuiltInTransport(context)
                } else {
                    DiagnosticLogger.w(LogCategory.SDK, "PrinterFactory", "Built-in printer is not available on this device")
                    UnsupportedTransport("Built-in printer is not available on this device")
                }
            }
        }
    }

    fun createPrinter(context: Context, config: PrinterConfig): PrinterAdapter {
        val profile = PrinterProfile.forWidth(config.width)
        return VendorAdapterFactory.createWithFallback(context, config, profile) {
            createTransport(context, config)
        }
    }
}

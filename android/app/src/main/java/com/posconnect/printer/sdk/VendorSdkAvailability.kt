package com.posconnect.printer.sdk

import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrintEngine
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.config.PrinterConfig

object VendorSdkAvailability {
    fun classPresent(className: String): Boolean =
        try {
            Class.forName(className)
            true
        } catch (_: ClassNotFoundException) {
            false
        }

    val epson: Boolean get() = classPresent("com.epson.epos2.printer.Printer")
    val xprinter: Boolean get() = classPresent("net.posprinter.POSPrinter")
    val gprinter: Boolean get() = classPresent("com.gprinter.sdk.Gprinter")
    val sunmi: Boolean get() = classPresent("com.sunmi.peripheral.printer.SunmiPrinterService")
    val rongta: Boolean get() =
        classPresent("com.rt.printerlibrary.printer.RTPrinter") ||
            classPresent("com.rt.printerlibrary.factory.printer.ThermalPrinterFactory")

    fun usesVendorSdkForPrint(config: PrinterConfig): Boolean {
        if (!config.usesEscPosEngine()) return false
        return when (config.brand) {
            PrinterBrand.EPSON -> epson
            PrinterBrand.XPRINTER -> xprinter
            PrinterBrand.GPRINTER -> gprinter
            PrinterBrand.SUNMI -> config.connectionType == ConnectionType.BUILTIN && sunmi
            PrinterBrand.RONGTA -> rongta
            else -> false
        }
    }

    fun printPath(config: PrinterConfig): String {
        return when {
            config.printEngine.name == "STAR_IO10" -> "star_js"
            config.printEngine.name == "PASSPRNT" -> "passprnt"
            config.printEngine.name == "CLOUDPRNT" -> "cloudprnt"
            usesVendorSdkForPrint(config) -> "vendor_sdk"
            else -> "escpos_fallback"
        }
    }

    /** Matches JS printerSdkSettingsFields — vendor Kotlin SDK or StarIO10 RN SDK. */
    fun usesVendorApi(config: PrinterConfig): Boolean {
        val path = printPath(config)
        return path == "vendor_sdk" || path == "star_js"
    }

    fun sdkTechLabel(config: PrinterConfig): String = when (config.brand) {
        PrinterBrand.EPSON -> "Epson ePOS SDK for Android 2.32.0"
        PrinterBrand.XPRINTER -> "XPrinter Android SDK 3.2.0"
        PrinterBrand.GPRINTER -> "GPrinter Android SDK 2.0"
        PrinterBrand.SUNMI ->
            if (config.connectionType == ConnectionType.BUILTIN) {
                "SUNMI PrinterLibrary 1.0.24"
            } else {
                "POS Connect ESC/POS Engine (SUNMI external)"
            }
        PrinterBrand.STAR ->
            when (config.printEngine) {
                PrintEngine.STAR_IO10 -> "StarIO10 / StarXpand SDK (react-native-star-io10 1.13.0)"
                PrintEngine.PASSPRNT -> "Star PassPRNT URL Scheme"
                PrintEngine.CLOUDPRNT -> "Star CloudPRNT HTTP"
                PrintEngine.ESC_POS -> "Generic ESC/POS (Star LAN/BT fallback)"
            }
        PrinterBrand.RONGTA -> "Rongta RTPrinterSDK (not bundled)"
        PrinterBrand.CUSTOM -> "Custom vendor SDK (not supplied)"
        PrinterBrand.GENERIC_ESC_POS -> "POS Connect ESC/POS Engine 3.0"
    }
}

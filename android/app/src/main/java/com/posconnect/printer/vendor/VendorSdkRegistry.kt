package com.posconnect.printer.vendor

import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrintEngine
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.config.PrinterConfig
import com.posconnect.printer.sdk.VendorSdkAvailability

data class VendorSdkInfo(
    val brand: PrinterBrand,
    val sdkTechName: String,
    val officialSdkName: String,
    val version: String,
    val supply: String,
    val integrated: Boolean,
    val downloadUrl: String,
    val notes: String,
    val printPath: String,
) {
    fun toJsonMap(): Map<String, Any?> = mapOf(
        "brand" to brand.name,
        "sdkTechName" to sdkTechName,
        "sdkOfficialName" to officialSdkName,
        "sdkVersion" to version,
        "sdkSupply" to supply,
        "sdkIntegrated" to integrated,
        "sdkDownloadUrl" to downloadUrl,
        "sdkPrintPath" to printPath,
        "sdkUsesVendorApi" to (printPath == "vendor_sdk" || printPath == "star_js"),
        "notes" to notes,
    )
}

object VendorSdkRegistry {

    private data class CatalogRow(
        val brand: PrinterBrand,
        val sdkTechName: String,
        val officialSdkName: String,
        val version: String,
        val supply: String,
        val downloadUrl: String,
        val notes: String,
    )

    private val catalog = listOf(
        CatalogRow(
            PrinterBrand.GENERIC_ESC_POS,
            "POS Connect ESC/POS Engine",
            "Generic ESC/POS (no vendor SDK)",
            "3.0",
            "escpos_fallback",
            "",
            "Custom Kotlin ESC/POS over LAN / Bluetooth / BLE / USB.",
        ),
        CatalogRow(
            PrinterBrand.EPSON,
            "Epson ePOS SDK for Android",
            "ePOS2.jar + libepos2.so",
            "2.32.0",
            if (VendorSdkAvailability.epson) "bundled" else "manual_required",
            "https://download3.ebz.epson.net/dsc/f/03/00/17/07/34/7de19987ac4424b34b1ee708f254d7b825526beb/ePOS_SDK_Android_v2.32.0.zip",
            "EpsonSdkAdapter uses com.epson.epos2.printer.Printer API.",
        ),
        CatalogRow(
            PrinterBrand.STAR,
            "StarIO10 / StarXpand SDK",
            "Star Micronics StarIO10 (Android + iOS)",
            "1.13.0",
            "bundled",
            "https://starmicronics.com/support/developers/printer-sdks/",
            "STAR_IO10 via react-native-star-io10. ESC/POS mode uses generic engine.",
        ),
        CatalogRow(
            PrinterBrand.SUNMI,
            "SUNMI PrinterLibrary",
            "com.sunmi:printerlibrary (InnerPrinter)",
            "1.0.24",
            "maven",
            "https://central.sonatype.com/artifact/com.sunmi/printerlibrary",
            "Built-in SUNMI uses PrinterLibrary via SunmiBuiltInTransport.",
        ),
        CatalogRow(
            PrinterBrand.XPRINTER,
            "XPrinter Android SDK",
            "printer-lib AAR (net.posprinter)",
            "3.2.0",
            if (VendorSdkAvailability.xprinter) "bundled" else "manual_required",
            "https://www.xprintertech.com/sdk.html",
            "XPrinterSdkAdapter uses net.posprinter.POSPrinter API.",
        ),
        CatalogRow(
            PrinterBrand.RONGTA,
            "Rongta Thermal Printer Android SDK",
            "RTPrinterSDK printer_library.jar",
            "2025-11",
            if (VendorSdkAvailability.rongta) "bundled" else "manual_required",
            "https://www.rongtatech.com/sdk/",
            "Download Android SDK from Rongta OneDrive → android/app/libs/rongta/.",
        ),
        CatalogRow(
            PrinterBrand.GPRINTER,
            "GPrinter Android SDK",
            "com.gprinter:gprintersdk AAR",
            "2.0",
            if (VendorSdkAvailability.gprinter) "bundled" else "manual_required",
            "http://gprinter.net/kaifa01/",
            "GPrinterSdkAdapter uses com.gprinter.sdk.Gprinter API.",
        ),
        CatalogRow(
            PrinterBrand.CUSTOM,
            "Custom vendor SDK (not supplied)",
            "Place vendor AAR in android/app/libs/custom/",
            "—",
            "manual_required",
            "",
            "Uses ESC/POS fallback until a vendor AAR is added.",
        ),
    )

    private fun catalogForBrand(brand: PrinterBrand): CatalogRow =
        catalog.firstOrNull { it.brand == brand } ?: catalog.first()

    private fun sampleConfig(
        brand: PrinterBrand,
        printEngine: PrintEngine,
        connection: ConnectionType,
    ): PrinterConfig = PrinterConfig(
        brand = brand,
        printEngine = printEngine,
        connectionType = connection,
    )

    fun resolveActiveSdkTechName(
        brand: PrinterBrand,
        printEngine: PrintEngine,
        connection: ConnectionType,
    ): String = VendorSdkAvailability.sdkTechLabel(sampleConfig(brand, printEngine, connection))

    fun isIntegrated(brand: PrinterBrand, printEngine: PrintEngine, connection: ConnectionType): Boolean {
        val path = VendorSdkAvailability.printPath(sampleConfig(brand, printEngine, connection))
        return path == "vendor_sdk" || path == "star_js" || path == "passprnt" || path == "cloudprnt"
    }

    fun activeSdkInfo(
        brand: PrinterBrand,
        printEngine: PrintEngine,
        connection: ConnectionType,
    ): VendorSdkInfo {
        val row = catalogForBrand(brand)
        val config = sampleConfig(brand, printEngine, connection)
        val path = VendorSdkAvailability.printPath(config)
        return VendorSdkInfo(
            brand = brand,
            sdkTechName = resolveActiveSdkTechName(brand, printEngine, connection),
            officialSdkName = row.officialSdkName,
            version = row.version,
            supply = row.supply,
            integrated = isIntegrated(brand, printEngine, connection),
            downloadUrl = row.downloadUrl,
            notes = row.notes,
            printPath = path,
        )
    }

    fun allCatalogEntries(): List<VendorSdkInfo> =
        PrinterBrand.entries.map { brand ->
            activeSdkInfo(brand, PrintEngine.ESC_POS, ConnectionType.LAN)
        }
}

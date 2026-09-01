package com.posconnect.core.config

import org.json.JSONArray
import org.json.JSONObject

enum class PrinterWidth(val displayName: String, val paperWidthMm: Int, val printableWidthMm: Int, val defaultCpl: Int) {
    THREE_INCH("3 Inch (80mm)", 80, 72, 48),
    FOUR_INCH("4 Inch (110mm)", 110, 104, 64);

    companion object {
        fun fromString(value: String?): PrinterWidth {
            return when (value?.lowercase()) {
                "4inch", "4 inch", "4_inch", "110mm" -> FOUR_INCH
                else -> THREE_INCH
            }
        }
    }
}

enum class ConnectionType(val displayName: String) {
    BLUETOOTH("Bluetooth Classic"),
    BLE("Bluetooth Low Energy (BLE)"),
    LAN("LAN / Wi-Fi (TCP/IP)"),
    USB("USB Direct"),
    BUILTIN("Built-in POS Printer"),
    VENDOR("Vendor SDK");

    companion object {
        fun fromString(value: String?): ConnectionType {
            return when (value?.uppercase()) {
                "BLE" -> BLE
                "LAN", "WIFI", "TCP", "ETHERNET" -> LAN
                "USB" -> USB
                "BUILTIN", "SUNMI", "INTERNAL" -> BUILTIN
                "VENDOR" -> VENDOR
                else -> BLUETOOTH
            }
        }
    }
}

enum class PrinterBrand(val displayName: String) {
    GENERIC_ESC_POS("Generic ESC/POS"),
    EPSON("Epson"),
    STAR("Star Micronics"),
    SUNMI("SUNMI POS"),
    XPRINTER("XPrinter"),
    RONGTA("Rongta"),
    GPRINTER("GPrinter"),
    CUSTOM("Custom Vendor");

    companion object {
        fun fromString(value: String?): PrinterBrand {
            return when (value?.uppercase()) {
                "EPSON" -> EPSON
                "STAR" -> STAR
                "SUNMI" -> SUNMI
                "XPRINTER" -> XPRINTER
                "RONGTA" -> RONGTA
                "GPRINTER" -> GPRINTER
                "CUSTOM" -> CUSTOM
                else -> GENERIC_ESC_POS
            }
        }
    }
}

enum class PrintEngine {
    STAR_IO10,
    PASSPRNT,
    CLOUDPRNT,
    ESC_POS;

    companion object {
        fun fromString(value: String?, brand: PrinterBrand = PrinterBrand.GENERIC_ESC_POS): PrintEngine {
            return when (value?.uppercase()) {
                "STAR_IO10", "STARIO10", "STARXPAND" -> STAR_IO10
                "PASSPRNT" -> PASSPRNT
                "CLOUDPRNT" -> CLOUDPRNT
                "ESC_POS", "ESCPOS" -> ESC_POS
                else -> if (brand == PrinterBrand.STAR) STAR_IO10 else ESC_POS
            }
        }
    }
}

enum class PrinterRole {
    RECEIPT,
    KITCHEN,
    BAR,
    LABEL
}

data class DivisionConfig(
    val name: String = "",
    val code: String = "",
    val url: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("code", code)
        put("url", url)
    }

    companion object {
        fun fromJson(json: JSONObject?): DivisionConfig {
            if (json == null) return DivisionConfig()
            return DivisionConfig(
                name = json.optString("name", ""),
                code = json.optString("code", ""),
                url = json.optString("url", "")
            )
        }
    }
}

data class CustomerConfig(
    val name: String = "",
    val code: String = "",
    val store: String = "",
    val device: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("code", code)
        put("store", store)
        put("device", device)
    }

    companion object {
        fun fromJson(json: JSONObject?): CustomerConfig {
            if (json == null) return CustomerConfig()
            return CustomerConfig(
                name = json.optString("name", ""),
                code = json.optString("code", ""),
                store = json.optString("store", ""),
                device = json.optString("device", "")
            )
        }
    }
}

data class PrinterConfig(
    val id: String = "primary_receipt",
    val name: String = "Default Receipt Printer",
    val role: PrinterRole = PrinterRole.RECEIPT,
    val enabled: Boolean = true,
    val width: PrinterWidth = PrinterWidth.THREE_INCH,
    val brand: PrinterBrand = PrinterBrand.GENERIC_ESC_POS,
    val model: String = "Generic",
    val connectionType: ConnectionType = ConnectionType.LAN,
    val ip: String = "",
    val port: Int = 9100,
    val macAddress: String = "",
    val usbVendorId: Int = 0,
    val usbProductId: Int = 0,
    val deviceName: String = "",
    val autoReconnect: Boolean = true,
    val retryCount: Int = 3,
    val showPrintDialog: Boolean = false,
    val autoCut: Boolean = true,
    val cutMode: String = "partial",
    val printEngine: PrintEngine = PrintEngine.ESC_POS,
    val starIdentifier: String = "",
    val cashDrawer: Boolean = false,
    val cloudPrntUrl: String = "",
    val passPrntPort: String = "",
    val passPrntSettings: String = "",
    val feedLinesTop: Int = 0,
    val feedLinesBottom: Int = 2
) {
    fun usesEscPosEngine(): Boolean = printEngine == PrintEngine.ESC_POS

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("role", role.name)
        put("enabled", enabled)
        put("width", if (width == PrinterWidth.FOUR_INCH) "4inch" else "3inch")
        put("brand", brand.name)
        put("model", model)
        put("connectionType", connectionType.name)
        put("connection", connectionType.name)
        put("type", brand.name)
        put("ip", ip)
        put("port", port)
        put("macAddress", macAddress)
        put("usbVendorId", usbVendorId)
        put("usbProductId", usbProductId)
        put("deviceName", deviceName)
        put("autoReconnect", autoReconnect)
        put("retryCount", retryCount)
        put("showPrintDialog", showPrintDialog)
        put("autoCut", autoCut)
        put("cutMode", if (cutMode == "full") "full" else "partial")
        put("printEngine", printEngine.name)
        put("starIdentifier", starIdentifier)
        put("cashDrawer", cashDrawer)
        put("cloudPrntUrl", cloudPrntUrl)
        put("passPrntPort", passPrntPort)
        put("passPrntSettings", passPrntSettings)
        put("charactersPerLine", width.defaultCpl)
        put("widthMm", width.paperWidthMm)
        put("printableWidthMm", width.printableWidthMm)
        put("feedLinesTop", feedLinesTop)
        put("feedLinesBottom", feedLinesBottom)
    }

    companion object {
        fun fromJson(json: JSONObject?): PrinterConfig {
            if (json == null) return PrinterConfig()
            return PrinterConfig(
                id = json.optString("id", "primary_receipt"),
                name = json.optString("name", "Default Receipt Printer"),
                role = runCatching { PrinterRole.valueOf(json.optString("role", "RECEIPT")) }.getOrDefault(PrinterRole.RECEIPT),
                enabled = json.optBoolean("enabled", true),
                width = PrinterWidth.fromString(json.optString("width", "3inch")),
                brand = PrinterBrand.fromString(json.optString("brand", "GENERIC_ESC_POS")),
                model = json.optString("model", "Generic"),
                connectionType = ConnectionType.fromString(
                    json.optString("connection", json.optString("connectionType", "LAN"))
                ),
                ip = json.optString("ip", ""),
                port = json.optInt("port", 9100),
                macAddress = json.optString("macAddress", ""),
                usbVendorId = json.optInt("usbVendorId", 0),
                usbProductId = json.optInt("usbProductId", 0),
                deviceName = json.optString("deviceName", ""),
                autoReconnect = json.optBoolean("autoReconnect", true),
                retryCount = json.optInt("retryCount", 3),
                showPrintDialog = json.optBoolean("showPrintDialog", false),
                autoCut = json.optBoolean("autoCut", true),
                cutMode = if (json.optString("cutMode", "partial") == "full") "full" else "partial",
                printEngine = PrintEngine.fromString(
                    json.optString("printEngine", ""),
                    PrinterBrand.fromString(json.optString("brand", "GENERIC_ESC_POS"))
                ),
                starIdentifier = json.optString(
                    "starIdentifier",
                    json.optString("macAddress", json.optString("ip", ""))
                ),
                cashDrawer = json.optBoolean("cashDrawer", false),
                cloudPrntUrl = json.optString("cloudPrntUrl", ""),
                passPrntPort = json.optString("passPrntPort", ""),
                passPrntSettings = json.optString("passPrntSettings", ""),
                feedLinesTop = json.optInt("feedLinesTop", 0),
                feedLinesBottom = json.optInt("feedLinesBottom", 2)
            )
        }
    }
}

data class SecurityConfig(
    val allowedDomains: List<String> = emptyList(),
    val allowExternalNavigation: Boolean = false,
    val openExternalInSystemBrowser: Boolean = true,
    val requireHttps: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        val array = JSONArray()
        allowedDomains.forEach { array.put(it) }
        put("allowedDomains", array)
        put("allowExternalNavigation", allowExternalNavigation)
        put("openExternalInSystemBrowser", openExternalInSystemBrowser)
        put("requireHttps", requireHttps)
    }

    companion object {
        fun fromJson(json: JSONObject?): SecurityConfig {
            if (json == null) return SecurityConfig()
            val list = mutableListOf<String>()
            val array = json.optJSONArray("allowedDomains")
            if (array != null) {
                for (i in 0 until array.length()) {
                    list.add(array.optString(i))
                }
            }
            return SecurityConfig(
                allowedDomains = list,
                allowExternalNavigation = json.optBoolean("allowExternalNavigation", false),
                openExternalInSystemBrowser = json.optBoolean("openExternalInSystemBrowser", true),
                requireHttps = json.optBoolean("requireHttps", true)
            )
        }
    }
}

data class AppConfig(
    val configVersion: Int = 1,
    val setupCompleted: Boolean = false,
    val orientation: String = "portrait",
    val division: DivisionConfig = DivisionConfig(),
    val customer: CustomerConfig = CustomerConfig(),
    val printer: PrinterConfig = PrinterConfig(),
    val secondaryPrinters: List<PrinterConfig> = emptyList(),
    val security: SecurityConfig = SecurityConfig()
) {
    val isConfigured: Boolean
        get() = setupCompleted || (division.url.isNotBlank() && customer.name.isNotBlank())

    fun toJson(): JSONObject = JSONObject().apply {
        put("configVersion", configVersion)
        put("setupCompleted", setupCompleted)
        put("orientation", orientation)
        put("division", division.toJson())
        put("customer", customer.toJson())
        put("printer", printer.toJson())
        val printersArr = JSONArray()
        secondaryPrinters.forEach { printersArr.put(it.toJson()) }
        put("secondaryPrinters", printersArr)
        put("printers", printersArr)
        put("security", security.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject?): AppConfig {
            if (json == null) return AppConfig()
            val secondaryList = mutableListOf<PrinterConfig>()
            val printersArr = json.optJSONArray("secondaryPrinters") ?: json.optJSONArray("printers")
            if (printersArr != null) {
                for (i in 0 until printersArr.length()) {
                    secondaryList.add(PrinterConfig.fromJson(printersArr.optJSONObject(i)))
                }
            }
            return AppConfig(
                configVersion = json.optInt("configVersion", 1),
                setupCompleted = json.optBoolean("setupCompleted", false),
                orientation = json.optString("orientation", "portrait"),
                division = DivisionConfig.fromJson(json.optJSONObject("division")),
                customer = CustomerConfig.fromJson(json.optJSONObject("customer")),
                printer = PrinterConfig.fromJson(json.optJSONObject("printer")),
                secondaryPrinters = secondaryList,
                security = SecurityConfig.fromJson(json.optJSONObject("security"))
            )
        }
    }
}

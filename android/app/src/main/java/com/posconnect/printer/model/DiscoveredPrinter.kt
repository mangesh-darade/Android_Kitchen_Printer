package com.posconnect.printer.model

import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrinterBrand
import org.json.JSONObject

data class DiscoveredPrinter(
    val name: String,
    val identifier: String, // MAC, IP, USB Device ID
    val connectionType: ConnectionType,
    val brand: PrinterBrand = PrinterBrand.GENERIC_ESC_POS,
    val model: String = "",
    val signalStrength: Int? = null,
    val isConnected: Boolean = false,
    val details: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("identifier", identifier)
        put("connectionType", connectionType.name)
        put("brand", brand.name)
        put("model", model)
        if (signalStrength != null) put("signalStrength", signalStrength)
        put("isConnected", isConnected)
        put("details", details)
    }
}

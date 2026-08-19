package com.posconnect.printer.model

import org.json.JSONObject

data class PrinterStatus(
    val connected: Boolean = false,
    val ready: Boolean = false,
    val paperOut: Boolean = false,
    val coverOpen: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("connected", connected)
        put("ready", ready)
        put("paperOut", paperOut)
        put("coverOpen", coverOpen)
        put("offline", offline)
        if (error != null) put("error", error) else put("error", JSONObject.NULL)
    }

    companion object {
        fun onlineReady(): PrinterStatus = PrinterStatus(connected = true, ready = true, paperOut = false, coverOpen = false, offline = false, error = null)
        fun offline(reason: String): PrinterStatus = PrinterStatus(connected = false, ready = false, offline = true, error = reason)
    }
}

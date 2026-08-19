package com.posconnect.printer.model

import org.json.JSONObject

object PrinterErrorCodes {
    const val PRINTER_NOT_FOUND = "PRINTER_NOT_FOUND"
    const val PRINTER_OFFLINE = "PRINTER_OFFLINE"
    const val PRINTER_BUSY = "PRINTER_BUSY"
    const val PRINTER_PERMISSION_DENIED = "PRINTER_PERMISSION_DENIED"
    const val BLUETOOTH_DISABLED = "BLUETOOTH_DISABLED"
    const val BLUETOOTH_PERMISSION_DENIED = "BLUETOOTH_PERMISSION_DENIED"
    const val USB_PERMISSION_DENIED = "USB_PERMISSION_DENIED"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val TIMEOUT = "TIMEOUT"
    const val PAPER_OUT = "PAPER_OUT"
    const val COVER_OPEN = "COVER_OPEN"
    const val UNSUPPORTED_OPERATION = "UNSUPPORTED_OPERATION"
    const val UNSUPPORTED_PRINTER = "UNSUPPORTED_PRINTER"
    const val INVALID_RECEIPT = "INVALID_RECEIPT"
    const val UNAUTHORIZED_ORIGIN = "UNAUTHORIZED_ORIGIN"
}

data class PrinterResult(
    val success: Boolean,
    val data: JSONObject? = null,
    val errorCode: String? = null,
    val message: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("success", success)
        if (data != null) put("data", data) else put("data", JSONObject())
        if (errorCode != null) put("errorCode", errorCode)
        if (message.isNotEmpty()) put("message", message)
    }

    companion object {
        fun success(message: String = "Operation successful", data: JSONObject? = null): PrinterResult {
            return PrinterResult(success = true, data = data, message = message)
        }

        fun error(errorCode: String, message: String): PrinterResult {
            return PrinterResult(success = false, errorCode = errorCode, message = message)
        }
    }
}

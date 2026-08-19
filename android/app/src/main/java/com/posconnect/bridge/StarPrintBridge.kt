package com.posconnect.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.posconnect.core.config.PrinterConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StarPrintBridge {
    @Volatile
    var eventSink: ((String, WritableMap) -> Unit)? = null

    @Volatile
    var resultSink: ((String, Boolean, String) -> Unit)? = null

    private val pending = ConcurrentHashMap<String, String>()

    fun usesStarJsEngine(printer: PrinterConfig): Boolean = !printer.usesEscPosEngine()

    fun emit(
        action: String,
        printer: PrinterConfig,
        text: String = "",
        extra: Map<String, String> = emptyMap()
    ): String {
        val jobId = UUID.randomUUID().toString()
        pending[jobId] = action
        val params = Arguments.createMap().apply {
            putString("jobId", jobId)
            putString("action", action)
            putString("text", text)
            putString("engine", printer.printEngine.name)
            putString("identifier", printer.starIdentifier.ifBlank { printer.macAddress.ifBlank { printer.ip } })
            putString("connection", printer.connectionType.name)
            putString("cutMode", printer.cutMode)
            putBoolean("autoCut", printer.autoCut)
            putBoolean("cashDrawer", printer.cashDrawer)
            putString("width", if (printer.width.paperWidthMm >= 100) "4inch" else "3inch")
            extra.forEach { (key, value) -> putString(key, value) }
        }
        eventSink?.invoke("StarPrintRequest", params)
        return jobId
    }

    fun notifyResult(jobId: String, ok: Boolean, message: String) {
        pending.remove(jobId)
        resultSink?.invoke(jobId, ok, message)
    }
}

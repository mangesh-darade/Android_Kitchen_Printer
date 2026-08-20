package com.posconnect.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.posconnect.core.config.PrintEngine
import com.posconnect.core.config.PrinterConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StarPrintBridge {
    data class JobResult(val ok: Boolean, val message: String)

    @Volatile
    var eventSink: ((String, WritableMap) -> Unit)? = null

    @Volatile
    var resultSink: ((String, Boolean, String) -> Unit)? = null

    private val pending = ConcurrentHashMap<String, String>()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<JobResult>>()

    private const val DEFAULT_TIMEOUT_MS = 30_000L

    fun usesStarJsEngine(printer: PrinterConfig): Boolean = !printer.usesEscPosEngine()

    /** True when autoCut is handled inside the print job (StarIO10 commands / PassPRNT URI). */
    fun cutIncludedInPrintJob(printer: PrinterConfig): Boolean {
        if (!printer.autoCut) return false
        return when (printer.printEngine) {
            PrintEngine.STAR_IO10, PrintEngine.PASSPRNT -> true
            else -> false
        }
    }

    fun emit(
        action: String,
        printer: PrinterConfig,
        text: String = "",
        extra: Map<String, String> = emptyMap(),
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

    /**
     * Queue a Star/PassPRNT/CloudPRNT job and block until RN reports success/failure.
     * Kitchen WebView calls are synchronous — ElintOm must not mark delivered until print finishes.
     */
    suspend fun emitAndAwait(
        action: String,
        printer: PrinterConfig,
        text: String = "",
        extra: Map<String, String> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): JobResult {
        val jobId = emit(action, printer, text, extra)
        val deferred = CompletableDeferred<JobResult>()
        waiters[jobId] = deferred
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        waiters.remove(jobId)
        pending.remove(jobId)
        return result ?: JobResult(false, "Print timed out after ${timeoutMs / 1000}s")
    }

    fun notifyResult(jobId: String, ok: Boolean, message: String) {
        pending.remove(jobId)
        waiters.remove(jobId)?.complete(JobResult(ok, message))
        resultSink?.invoke(jobId, ok, message)
    }
}

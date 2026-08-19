package com.posconnect.printer.queue

import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.adapters.PrinterAdapter
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.ReceiptData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

enum class JobStatus {
    PENDING,
    PRINTING,
    COMPLETED,
    FAILED,
    RETRYING
}

data class PrintJob(
    val id: String,
    val title: String,
    val receiptData: ReceiptData? = null,
    val rawBytes: ByteArray? = null,
    val status: JobStatus = JobStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val maxAttempts: Int = 3,
    val error: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("status", status.name)
        put("timestamp", timestamp)
        put("attempts", attempts)
        if (error != null) put("error", error)
    }
}

class PrintQueueManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val jobIdCounter = AtomicInteger(1)

    private val queueList = mutableListOf<PrintJob>()
    private val _jobsState = MutableStateFlow<List<PrintJob>>(emptyList())
    val jobsState: StateFlow<List<PrintJob>> = _jobsState.asStateFlow()

    private var isProcessing = false

    fun enqueueReceipt(receipt: ReceiptData, title: String = "Receipt #${receipt.header.invoiceNumber}", adapterProvider: () -> PrinterAdapter?): String {
        val jobId = String.format("JOB-%03d", jobIdCounter.getAndIncrement())
        val job = PrintJob(
            id = jobId,
            title = title,
            receiptData = receipt
        )

        synchronized(queueList) {
            queueList.add(job)
            _jobsState.value = queueList.toList()
        }

        DiagnosticLogger.i(LogCategory.PRINTER, "PrintQueue", "Enqueued print job $jobId: $title")
        processNext(adapterProvider)
        return jobId
    }

    fun enqueueRaw(bytes: ByteArray, title: String = "Raw Print Job", adapterProvider: () -> PrinterAdapter?): String {
        val jobId = String.format("JOB-%03d", jobIdCounter.getAndIncrement())
        val job = PrintJob(
            id = jobId,
            title = title,
            rawBytes = bytes
        )

        synchronized(queueList) {
            queueList.add(job)
            _jobsState.value = queueList.toList()
        }

        DiagnosticLogger.i(LogCategory.PRINTER, "PrintQueue", "Enqueued raw print job $jobId")
        processNext(adapterProvider)
        return jobId
    }

    private fun processNext(adapterProvider: () -> PrinterAdapter?) {
        scope.launch {
            mutex.withLock {
                if (isProcessing) return@withLock
                isProcessing = true

                try {
                    while (true) {
                        val nextJob = synchronized(queueList) {
                            queueList.firstOrNull { it.status == JobStatus.PENDING || it.status == JobStatus.RETRYING }
                        } ?: break

                        updateJobStatus(nextJob.id, JobStatus.PRINTING, attempts = nextJob.attempts + 1)

                        val adapter = adapterProvider()
                        if (adapter == null) {
                            updateJobStatus(nextJob.id, JobStatus.FAILED, error = "No printer configured or adapter unavailable")
                            continue
                        }

                        DiagnosticLogger.i(LogCategory.PRINTER, "PrintQueue", "Processing ${nextJob.id}: ${nextJob.title} (Attempt ${nextJob.attempts + 1})")

                        val result: PrinterResult = try {
                            if (nextJob.receiptData != null) {
                                adapter.printReceipt(nextJob.receiptData)
                            } else if (nextJob.rawBytes != null) {
                                adapter.print(nextJob.rawBytes)
                            } else {
                                PrinterResult.error(PrinterErrorCodes.INVALID_RECEIPT, "Empty print job payload")
                            }
                        } catch (e: Exception) {
                            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, e.localizedMessage ?: "Unknown print error")
                        }

                        if (result.success) {
                            DiagnosticLogger.i(LogCategory.PRINTER, "PrintQueue", "Job ${nextJob.id} completed successfully")
                            updateJobStatus(nextJob.id, JobStatus.COMPLETED)
                        } else {
                            val attempts = nextJob.attempts + 1
                            if (attempts < nextJob.maxAttempts) {
                                DiagnosticLogger.w(LogCategory.PRINTER, "PrintQueue", "Job ${nextJob.id} failed, will retry ($attempts/${nextJob.maxAttempts})")
                                updateJobStatus(nextJob.id, JobStatus.RETRYING, error = result.message)
                                kotlinx.coroutines.delay(1500)
                            } else {
                                DiagnosticLogger.e(LogCategory.PRINTER, "PrintQueue", "Job ${nextJob.id} failed after $attempts attempts: ${result.message}")
                                updateJobStatus(nextJob.id, JobStatus.FAILED, error = result.message)
                            }
                        }
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    private fun updateJobStatus(jobId: String, status: JobStatus, attempts: Int? = null, error: String? = null) {
        synchronized(queueList) {
            val idx = queueList.indexOfFirst { it.id == jobId }
            if (idx >= 0) {
                val current = queueList[idx]
                queueList[idx] = current.copy(
                    status = status,
                    attempts = attempts ?: current.attempts,
                    error = error ?: current.error
                )
                _jobsState.value = queueList.toList()
            }
        }
    }

    fun clearQueue() {
        synchronized(queueList) {
            queueList.clear()
            _jobsState.value = emptyList()
        }
    }
}

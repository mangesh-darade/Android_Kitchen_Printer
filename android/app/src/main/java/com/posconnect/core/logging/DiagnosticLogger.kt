package com.posconnect.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogCategory {
    WEBVIEW,
    PRINTER,
    BLUETOOTH,
    USB,
    NETWORK,
    SDK,
    CONFIGURATION,
    SECURITY,
    SYSTEM
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String
) {
    fun format(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val time = dateFormat.format(Date(timestamp))
        return "[$time] [${level.name}] [${category.name}] [$tag] $message"
    }

    fun formatTime(): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        return dateFormat.format(Date(timestamp))
    }
}

object DiagnosticLogger {
    private const val MAX_LOGS = 500
    private val logList = mutableListOf<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()
    val logsState: StateFlow<List<LogEntry>> get() = logsFlow

    @Synchronized
    fun log(level: LogLevel, category: LogCategory, tag: String, message: String) {
        val sanitizedMessage = sanitize(message)
        val entry = LogEntry(
            level = level,
            category = category,
            tag = tag,
            message = sanitizedMessage
        )

        when (level) {
            LogLevel.DEBUG -> Log.d("POS_$tag", "[$category] $sanitizedMessage")
            LogLevel.INFO -> Log.i("POS_$tag", "[$category] $sanitizedMessage")
            LogLevel.WARN -> Log.w("POS_$tag", "[$category] $sanitizedMessage")
            LogLevel.ERROR -> Log.e("POS_$tag", "[$category] $sanitizedMessage")
        }

        if (logList.size >= MAX_LOGS) {
            logList.removeAt(0)
        }
        logList.add(entry)
        _logsFlow.value = logList.toList()
    }

    fun d(category: LogCategory, tag: String, message: String) = log(LogLevel.DEBUG, category, tag, message)
    fun i(category: LogCategory, tag: String, message: String) = log(LogLevel.INFO, category, tag, message)
    fun w(category: LogCategory, tag: String, message: String) = log(LogLevel.WARN, category, tag, message)
    fun e(category: LogCategory, tag: String, message: String) = log(LogLevel.ERROR, category, tag, message)

    @Synchronized
    fun clearLogs() {
        logList.clear()
        _logsFlow.value = emptyList()
    }

    fun clear() = clearLogs()

    @Synchronized
    fun exportLogs(): String {
        return logList.joinToString("\n") { it.format() }
    }

    fun exportAllLogs(): String = exportLogs()

    private fun sanitize(input: String): String {
        // Redact tokens, passwords, cookies, or secrets
        var result = input
        val sensitivePatterns = listOf(
            Regex("(?i)(password\\s*[:=]\\s*)[^,;\\s]+"),
            Regex("(?i)(token\\s*[:=]\\s*)[^,;\\s]+"),
            Regex("(?i)(cookie\\s*[:=]\\s*)[^,;\\s]+"),
            Regex("(?i)(secret\\s*[:=]\\s*)[^,;\\s]+")
        )
        for (pattern in sensitivePatterns) {
            result = pattern.replace(result, "$1***REDACTED***")
        }
        return result
    }
}

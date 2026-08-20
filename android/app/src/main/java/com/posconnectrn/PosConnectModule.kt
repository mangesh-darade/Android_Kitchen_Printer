package com.posconnectrn

import android.os.Build
import android.webkit.WebView
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.posconnect.bridge.StarPrintBridge
import com.posconnect.core.config.AppConfig
import com.posconnect.core.config.ConfigurationRepository
import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.manager.PrinterManager
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.transports.BuiltInPrinterDetector
import com.posconnect.printer.vendor.VendorSdkRegistry
import com.posconnect.printer.sdk.VendorSdkAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PosConnectModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo: ConfigurationRepository
        get() = ConfigurationRepository.getInstance(reactContext)

    private val printers: PrinterManager
        get() = PrinterManager.getInstance(reactContext)

    init {
        StarPrintBridge.eventSink = { name, params ->
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(name, params)
        }
    }

    override fun getName(): String = "PosConnect"

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for NativeEventEmitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for NativeEventEmitter
    }

    @ReactMethod
    fun notifyPrintResult(jobId: String, success: Boolean, message: String, promise: Promise) {
        StarPrintBridge.notifyResult(jobId, success, message)
        promise.resolve(okJson(if (success) "Printed" else message.ifBlank { "Failed" }))
    }

    @ReactMethod
    fun getConfiguration(promise: Promise) {
        try {
            promise.resolve(repo.configState.value.toJson().toString())
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun saveConfiguration(configJson: String, promise: Promise) {
        try {
            val config = AppConfig.fromJson(JSONObject(configJson))
            repo.saveConfiguration(config)
            promise.resolve(okJson("Saved"))
        } catch (e: Exception) {
            promise.resolve(errorJson(PrinterErrorCodes.INVALID_RECEIPT, e.message ?: "Save failed"))
        }
    }

    @ReactMethod
    fun resetApplication(promise: Promise) {
        repo.resetApplication()
        promise.resolve(okJson("Reset"))
    }

    @ReactMethod
    fun resetPrinter(promise: Promise) {
        repo.resetPrinterOnly()
        promise.resolve(okJson("Printer reset"))
    }

    @ReactMethod
    fun getDeviceInfo(promise: Promise) {
        try {
            val webViewVersion = try {
                WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            val metrics = reactContext.resources.displayMetrics
            val printer = repo.configState.value.printer
            val sdk = VendorSdkRegistry.activeSdkInfo(
                printer.brand,
                printer.printEngine,
                printer.connectionType
            )
            val data = JSONObject().apply {
                put("platform", "android")
                put("osVersion", Build.VERSION.RELEASE)
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("appVersion", "1.0.0-rn")
                put("screenSize", "${metrics.widthPixels}x${metrics.heightPixels}")
                put("webViewVersion", webViewVersion)
                put("printerEngineVersion", "3.1-VENDOR_SDK_CATALOG")
                put("printerCapability", printer.brand.name)
                put("printEngine", printer.printEngine.name)
                put("sdkTechName", sdk.sdkTechName)
                put("sdkIntegrated", sdk.integrated)
                put("sdkPrintPath", VendorSdkAvailability.printPath(printer))
                put("sdkUsesVendorApi", VendorSdkAvailability.usesVendorApi(printer))
            }
            promise.resolve(wrapData(data))
        } catch (e: Exception) {
            promise.reject("DEVICE_INFO", e.message, e)
        }
    }

    @ReactMethod
    fun discoverPrinters(connection: String, promise: Promise) {
        scope.launch {
            try {
                val type = ConnectionType.fromString(connection)
                val found = printers.discoverPrinters(type)
                val array = org.json.JSONArray()
                found.forEach { array.put(it.toJson()) }
                promise.resolve(wrapData(JSONObject().put("printers", array)))
            } catch (e: Exception) {
                promise.resolve(errorJson(PrinterErrorCodes.PRINTER_OFFLINE, e.message ?: "Discover failed"))
            }
        }
    }

    @ReactMethod
    fun connectPrinter(printerJson: String?, promise: Promise) {
        scope.launch {
            try {
                if (!printerJson.isNullOrBlank()) {
                    repo.updatePrinter(PrinterConfig.fromJson(JSONObject(printerJson)))
                }
                if (!repo.configState.value.printer.usesEscPosEngine()) {
                    promise.resolve(okJson("Star engine does not use ESC/POS transport"))
                    return@launch
                }
                promise.resolve(printers.connectActivePrinter().toJson().toString())
            } catch (e: Exception) {
                promise.resolve(errorJson(PrinterErrorCodes.PRINTER_OFFLINE, e.message ?: "Connect failed"))
            }
        }
    }

    @ReactMethod
    fun testPrinter(promise: Promise) {
        scope.launch {
            try {
                if (!repo.configState.value.printer.usesEscPosEngine()) {
                    promise.resolve(okJson("Use JS Star router for test print"))
                    return@launch
                }
                val adapter = printers.getActiveAdapter()
                    ?: return@launch promise.resolve(
                        errorJson(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured")
                    )
                promise.resolve(adapter.testPrint().toJson().toString())
            } catch (e: Exception) {
                promise.resolve(errorJson(PrinterErrorCodes.PRINTER_OFFLINE, e.message ?: "Test failed"))
            }
        }
    }

    @ReactMethod
    fun isBuiltInPrinterAvailable(promise: Promise) {
        val data = JSONObject().apply {
            put("available", BuiltInPrinterDetector.isAvailable())
            put("vendor", BuiltInPrinterDetector.vendorName())
        }
        promise.resolve(wrapData(data))
    }

    @ReactMethod
    fun checkUrlReachable(url: String, promise: Promise) {
        scope.launch {
            promise.resolve(wrapData(JSONObject().put("reachable", probeUrlReachable(url))))
        }
    }

    @ReactMethod
    fun exportLogs(promise: Promise) {
        promise.resolve(wrapData(JSONObject().put("text", DiagnosticLogger.exportLogs())))
    }

    @ReactMethod
    fun writeTempImage(base64: String, promise: Promise) {
        try {
            var payload = base64.trim()
            val comma = payload.indexOf(',')
            if (payload.startsWith("data:") && comma >= 0) {
                payload = payload.substring(comma + 1)
            }
            val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            val file = java.io.File(reactContext.cacheDir, "star-print-${java.util.UUID.randomUUID()}.png")
            file.writeBytes(bytes)
            promise.resolve(wrapData(JSONObject().put("path", file.absolutePath)))
        } catch (e: Exception) {
            promise.resolve(errorJson(PrinterErrorCodes.INVALID_RECEIPT, e.message ?: "Invalid image"))
        }
    }

    private fun probeUrlReachable(url: String): Boolean {
        for (method in listOf("HEAD", "GET")) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.requestMethod = method
                connection.setRequestProperty("User-Agent", "POSConnectRN/1.0 Android")
                connection.connect()
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..499) return true
            } catch (e: Exception) {
                DiagnosticLogger.w(LogCategory.NETWORK, "Reachability", "$method $url failed: ${e.message}")
            }
        }
        return false
    }

    private fun okJson(message: String): String =
        JSONObject().put("success", true).put("message", message).put("data", JSONObject()).toString()

    private fun wrapData(data: JSONObject): String =
        JSONObject().put("success", true).put("data", data).toString()

    private fun errorJson(code: String, message: String): String =
        JSONObject()
            .put("success", false)
            .put("errorCode", code)
            .put("message", message)
            .put("data", JSONObject())
            .toString()
}

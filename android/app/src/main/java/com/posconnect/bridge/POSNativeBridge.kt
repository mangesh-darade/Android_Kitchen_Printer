package com.posconnect.bridge

import android.app.Activity
import android.content.Context
import android.os.Build
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.posconnect.core.config.ConfigurationRepository
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.core.security.SecurityManager
import com.posconnect.printer.manager.PrinterManager
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.vendor.VendorSdkRegistry
import com.posconnect.printer.sdk.VendorSdkAvailability
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.ReceiptData
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class POSNativeBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?,
    private val activityProvider: (() -> Activity?)? = null,
) {
    private val printerManager = PrinterManager.getInstance(context)
    private val configRepo = ConfigurationRepository.getInstance(context)

    private fun resolveActivity(): Activity? =
        activityProvider?.invoke()
            ?: (context as? Activity)
            ?: (context as? android.app.Application)?.let { null }

    init {
        StarPrintBridge.resultSink = { _, ok, _ -> notifyJsPrintResult(ok) }
    }

    private fun validateOrigin(): Boolean {
        val wv = webViewProvider()
        val currentUrl = (wv as? com.posconnectrn.POSWebView)?.lastLoadedUrl
            ?.takeIf { it.isNotBlank() }
            ?: configRepo.configState.value.division.url
        val config = configRepo.configState.value
        val allowed = SecurityManager.isOriginAllowed(currentUrl, config)
        if (!allowed) {
            DiagnosticLogger.w(LogCategory.SECURITY, "POSNativeBridge", "Unauthorized native call from $currentUrl")
        }
        return allowed
    }

    private fun showToast(message: String) {
        val uiRunnable = Runnable {
            val act = resolveActivity() ?: context
            android.widget.Toast.makeText(act, message, android.widget.Toast.LENGTH_LONG).show()
        }
        val wv = webViewProvider()
        if (wv != null) {
            wv.post(uiRunnable)
        } else {
            resolveActivity()?.runOnUiThread(uiRunnable)
        }
    }

    private fun unauthorizedResponse(): String {
        showToast("POS Native: Unauthorized origin access")
        return PrinterResult.error(PrinterErrorCodes.UNAUTHORIZED_ORIGIN, "Unauthorized Origin").toJson().toString()
    }

    private fun printerDisabledResponse(): String {
        showToast("POS Native: Printer is disabled in Settings")
        return PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Printer is disabled").toJson().toString()
    }

    private fun ensurePrinterEnabled(): String? =
        if (!configRepo.configState.value.printer.enabled) printerDisabledResponse() else null

    /** Block WebView thread until RN Star router finishes (kitchen must not mark delivered early). */
    private fun starSync(
        action: String,
        printer: PrinterConfig,
        text: String = "",
        extra: Map<String, String> = emptyMap(),
    ): String {
        val result = runBlocking { StarPrintBridge.emitAndAwait(action, printer, text, extra) }
        return if (result.ok) {
            PrinterResult.success(result.message).toJson().toString()
        } else {
            PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, result.message).toJson().toString()
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        DiagnosticLogger.i(LogCategory.WEBVIEW, "POSNativeBridge", "JS invoked getDeviceInfo()")
        val info = JSONObject().apply {
            put("platform", "android")
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("appVersion", "1.0.0")
            put("printerEngineVersion", "3.0-STAR_IO10+ESC_POS")
        }
        return PrinterResult.success(data = info).toJson().toString()
    }

    @JavascriptInterface
    fun getConfiguration(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        val config = configRepo.configState.value
        val safeConfig = JSONObject().apply {
            put("division", config.division.toJson())
            put("customer", config.customer.toJson())
            put("printer", config.printer.toJson())
        }
        return PrinterResult.success(data = safeConfig).toJson().toString()
    }

    @JavascriptInterface
    fun getPrinterStatus(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return com.posconnect.printer.model.PrinterStatus(
                connected = true,
                ready = true,
                paperOut = false,
                coverOpen = false,
                offline = false,
                error = null
            ).toJson().toString()
        }
        val status = runBlocking { printerManager.checkStatus() }
        return status.toJson().toString()
    }

    @JavascriptInterface
    fun getPrinterCapabilities(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        val config = configRepo.configState.value.printer
        val caps = JSONObject().apply {
            put("paperWidths", JSONArray(listOf("3inch", "4inch")))
            put("currentWidth", if (config.width.paperWidthMm >= 100) "4inch" else "3inch")
            put("supportsBluetooth", true)
            put("supportsBLE", true)
            put("supportsLAN", true)
            put("supportsUSB", true)
            put("supportsQRCode", true)
            put("supportsBarcode", true)
            put("supportsCut", true)
            put("supportsCashDrawer", true)
            put("supportsUnicode", true)
            put("supportsIndianLanguages", true)
        }
        return PrinterResult.success(data = caps).toJson().toString()
    }

    @JavascriptInterface
    fun getPrinters(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        val discovered = printerManager.discoveredPrinters.value
        val array = JSONArray()
        discovered.forEach { array.put(it.toJson()) }
        val data = JSONObject().put("printers", array)
        return PrinterResult.success(data = data).toJson().toString()
    }

    @JavascriptInterface
    fun connectPrinter(configJsonStr: String?): String {
        if (!validateOrigin()) return unauthorizedResponse()
        return runBlocking {
            if (!configJsonStr.isNullOrBlank()) {
                try {
                    val json = JSONObject(configJsonStr)
                    val printerConfig = PrinterConfig.fromJson(json)
                    configRepo.updatePrinter(printerConfig)
                } catch (e: Exception) {
                    return@runBlocking PrinterResult.error(PrinterErrorCodes.INVALID_RECEIPT, "Invalid config JSON: ${e.message}").toJson().toString()
                }
            }
            val printer = configRepo.configState.value.printer
            if (StarPrintBridge.usesStarJsEngine(printer)) {
                return@runBlocking PrinterResult.success("Star engine handled in JS").toJson().toString()
            }
            val res = printerManager.connectActivePrinter()
            res.toJson().toString()
        }
    }

    @JavascriptInterface
    fun disconnectPrinter(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        return runBlocking {
            val res = printerManager.disconnectActivePrinter()
            res.toJson().toString()
        }
    }

    @JavascriptInterface
    fun printReceipt(receiptJsonStr: String): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        DiagnosticLogger.i(LogCategory.WEBVIEW, "POSNativeBridge", "JS invoked printReceipt()")
        val printer = configRepo.configState.value.printer

        // Extract text for dialog display
        val displayText = parsePrintText(receiptJsonStr).ifBlank { receiptJsonStr }

        // If showPrintDialog ON → show confirmation dialog first
        if (printer.showPrintDialog) {
            return showPrintDialogInternal(JSONObject().put("text", displayText).toString())
        }

        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("printText", printer, displayText)
        }
        return try {
            val json = JSONObject(receiptJsonStr)
            val receiptData = ReceiptData.fromJson(json)
            val jobId = printerManager.printReceiptQueued(receiptData, "Web POS Receipt #${receiptData.header.invoiceNumber}")
            val data = JSONObject().put("jobId", jobId).put("status", "QUEUED")
            PrinterResult.success("Receipt added to print queue", data).toJson().toString()
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.WEBVIEW, "POSNativeBridge", "Error parsing receipt JSON: ${e.message}")
            PrinterResult.error(PrinterErrorCodes.INVALID_RECEIPT, "Failed to parse receipt: ${e.localizedMessage}").toJson().toString()
        }
    }

    @JavascriptInterface
    fun printText(textDataJsonStr: String): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val printer = configRepo.configState.value.printer

        // If showPrintDialog ON → show confirmation dialog first
        val text = parsePrintText(textDataJsonStr)
        if (printer.showPrintDialog) {
            return showPrintDialogInternal(JSONObject().put("text", text).toString())
        }

        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("printText", printer, text)
        }
        val res = try {
            val json = JSONObject(textDataJsonStr)
            val isBold = json.optBoolean("isBold", false)
            runBlocking { printerManager.printTextDirect(text, isBold) }
        } catch (e: Exception) {
            runBlocking { printerManager.printTextDirect(textDataJsonStr, false) }
        }
        if (!res.success) {
            showToast("Printer Error: ${res.message}")
        }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun printImage(base64Image: String): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("printImage", printer, extra = mapOf("image" to base64Image))
        }
        val bitmap = com.posconnect.printer.escpos.TextRasterizer.decodeBase64Image(base64Image)
            ?: return PrinterResult.error(PrinterErrorCodes.INVALID_RECEIPT, "Invalid base64 image data").toJson().toString()
        val adapter = printerManager.getActiveAdapter()
            ?: return PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured").toJson().toString()
        val res = runBlocking { adapter.printImage(bitmap) }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun printQRCode(qrDataJsonStr: String): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val qrText = try {
            val json = JSONObject(qrDataJsonStr)
            json.optString("data", qrDataJsonStr)
        } catch (_: Exception) {
            qrDataJsonStr
        }
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("printQR", printer, extra = mapOf("qr" to qrText))
        }
        val adapter = printerManager.getActiveAdapter()
            ?: return PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured").toJson().toString()
        val res = runBlocking { adapter.printQRCode(qrText) }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun printBarcode(barcodeDataJsonStr: String): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val codeText = try {
            val json = JSONObject(barcodeDataJsonStr)
            json.optString("data", barcodeDataJsonStr)
        } catch (_: Exception) {
            barcodeDataJsonStr
        }
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("printBarcode", printer, extra = mapOf("barcode" to codeText))
        }
        val adapter = printerManager.getActiveAdapter()
            ?: return PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured").toJson().toString()
        val res = runBlocking { adapter.printBarcode(codeText) }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun testPrinter(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("testPrint", printer)
        }
        val adapter = printerManager.getActiveAdapter()
            ?: return PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured").toJson().toString()
        val res = runBlocking { adapter.testPrint() }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun openCashDrawer(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("openDrawer", printer)
        }
        val res = runBlocking { printerManager.openCashDrawer() }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun cutPaper(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.cutIncludedInPrintJob(printer)) {
            return PrinterResult.success("Cut already included in ${printer.printEngine.name} print job").toJson().toString()
        }
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return starSync("cutPaper", printer)
        }
        val res = runBlocking { printerManager.cutPaper() }
        return res.toJson().toString()
    }

    @JavascriptInterface
    fun beep(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        val res = runBlocking { printerManager.beep() }
        return res.toJson().toString()
    }

    /** Internal entry — skips origin check (caller already validated). */
    private fun showPrintDialogInternal(textDataJsonStr: String?): String =
        showPrintDialogImpl(textDataJsonStr)

    @JavascriptInterface
    fun showPrintDialog(textDataJsonStr: String?): String {
        if (!validateOrigin()) return unauthorizedResponse()
        ensurePrinterEnabled()?.let { return it }
        DiagnosticLogger.i(LogCategory.WEBVIEW, "POSNativeBridge", "JS invoked showPrintDialog()")
        return showPrintDialogImpl(textDataJsonStr)
    }

    private fun showPrintDialogImpl(textDataJsonStr: String?): String {

        val text = parsePrintText(textDataJsonStr)
        if (text.isBlank()) {
            return PrinterResult.error(PrinterErrorCodes.INVALID_RECEIPT, "Nothing to print").toJson().toString()
        }

        val printer = configRepo.configState.value.printer
        if (!printer.enabled) {
            return printerDisabledResponse()
        }

        // If showPrintDialog is OFF → auto-print silently without a dialog
        if (!printer.showPrintDialog) {
            Thread {
                runBlocking {
                    if (StarPrintBridge.usesStarJsEngine(printer)) {
                        StarPrintBridge.emit("printText", printer, text)
                        return@runBlocking
                    }
                    if (!printerManager.printerStatus.value.connected) {
                        printerManager.connectActivePrinter()
                    }
                    val res = printerManager.printTextDirect(text, false)
                    if (res.success && configRepo.configState.value.printer.autoCut) {
                        printerManager.cutPaper()
                    }
                    notifyJsPrintResult(res.success)
                }
            }.start()
            return PrinterResult.success("Auto-printing (dialog disabled)").toJson().toString()
        }

        val printerLabel = printerDisplayName(printer)

        val uiRunnable = Runnable {
            val activity = resolveActivity()
            if (activity == null || activity.isFinishing) {
                DiagnosticLogger.w(LogCategory.WEBVIEW, "POSNativeBridge", "Cannot show print dialog — no Activity")
                return@Runnable
            }
            showSystemPrintDialog(activity)
            notifyJsPrintResult(true)
        }
        // Post to UI thread via WebView if available, else via Activity handler
        val wv = webViewProvider()
        if (wv != null) {
            wv.post(uiRunnable)
        } else {
            resolveActivity()?.runOnUiThread(uiRunnable)
        }

        return PrinterResult.success("Print dialog opened").toJson().toString()
    }

    private fun notifyJsPrintResult(ok: Boolean) {
        webViewProvider()?.post {
            val js = "window.posNativeBridge && typeof window.posNativeBridge._printResult === 'function' && window.posNativeBridge._printResult(" + ok + ");"
            webViewProvider()?.evaluateJavascript(js, null)
        }
    }

    private fun parsePrintText(textDataJsonStr: String?): String {
        if (textDataJsonStr.isNullOrBlank()) {
            return ""
        }
        return try {
            val json = JSONObject(textDataJsonStr)
            json.optString("text", "").trim()
        } catch (_: Exception) {
            textDataJsonStr.trim()
        }
    }

    private fun printerDisplayName(printer: PrinterConfig): String {
        if (printer.name.isNotBlank() && printer.name != "Default Receipt Printer") {
            return printer.name
        }
        if (printer.deviceName.isNotBlank()) {
            return printer.deviceName
        }
        if (printer.ip.isNotBlank()) {
            return "${printer.ip}:${printer.port}"
        }
        if (printer.macAddress.isNotBlank()) {
            return printer.macAddress
        }
        return printer.name.ifBlank { "Configured Printer" }
    }

    private fun showSystemPrintDialog(activity: Activity) {
        val webView = webViewProvider() ?: return
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.createPrintDocumentAdapter("KOT")
        } else {
            @Suppress("DEPRECATION")
            webView.createPrintDocumentAdapter()
        }
        printManager.print("KOT Print", adapter, null)
    }

    @JavascriptInterface
    fun getPrinterSettings(): String {
        if (!validateOrigin()) return unauthorizedResponse()
        val printer = configRepo.configState.value.printer
        val data = JSONObject().apply {
            put("showPrintDialog", printer.showPrintDialog)
            put("autoCut", printer.autoCut)
            put("cutMode", if (printer.cutMode == "full") "full" else "partial")
            put("width", if (printer.width.paperWidthMm >= 100) "4inch" else "3inch")
            put("charactersPerLine", printer.width.defaultCpl)
            put("widthMm", printer.width.paperWidthMm)
            put("printableWidthMm", printer.width.printableWidthMm)
            put("cutSupported", true)
            put("printEngine", printer.printEngine.name)
            put("brand", printer.brand.name)
            put("connection", printer.connectionType.name)
            put("cashDrawer", printer.cashDrawer)
            put("starIdentifier", printer.starIdentifier)
            put("ip", printer.ip)
            put("port", printer.port)
            put("cloudPrntUrl", printer.cloudPrntUrl)
            put("macAddress", printer.macAddress)
            put("passPrntPort", printer.passPrntPort)
            put("passPrntSettings", printer.passPrntSettings)
            put("name", printer.name)
            put("role", printer.role.name)
            put("enabled", printer.enabled)
            put("deviceName", printer.deviceName)
            put("usbVendorId", printer.usbVendorId)
            put("usbProductId", printer.usbProductId)
            put("autoReconnect", printer.autoReconnect)
            put("retryCount", printer.retryCount)
            put("cutIncludedInPrint", StarPrintBridge.cutIncludedInPrintJob(printer))
            val sdk = VendorSdkRegistry.activeSdkInfo(
                printer.brand,
                printer.printEngine,
                printer.connectionType
            )
            put("sdkTechName", sdk.sdkTechName)
            put("sdkOfficialName", sdk.officialSdkName)
            put("sdkVersion", sdk.version)
            put("sdkSupply", sdk.supply)
            put("sdkIntegrated", sdk.integrated)
            put("sdkDownloadUrl", sdk.downloadUrl)
            put("sdkPrintPath", VendorSdkAvailability.printPath(printer))
            put("sdkUsesVendorApi", VendorSdkAvailability.usesVendorApi(printer))
        }
        return PrinterResult.success(data = data).toJson().toString()
    }

    @JavascriptInterface
    fun getPrinterWidth(): String {
        val config = configRepo.configState.value.printer
        val widthStr = if (config.width.paperWidthMm >= 100) "4inch" else "3inch"
        return JSONObject().put("paperWidth", widthStr).put("paperWidthMm", config.width.paperWidthMm).toString()
    }

    @JavascriptInterface
    fun getConnectionStatus(): String {
        val printer = configRepo.configState.value.printer
        if (StarPrintBridge.usesStarJsEngine(printer)) {
            return JSONObject().apply {
                put("connected", true)
                put("status", "STAR_ENGINE")
                put("engine", printer.printEngine.name)
            }.toString()
        }
        val status = printerManager.printerStatus.value
        return JSONObject().apply {
            put("connected", status.connected)
            put("status", if (status.connected) "CONNECTED" else "DISCONNECTED")
            if (status.error != null) put("error", status.error)
        }.toString()
    }
}

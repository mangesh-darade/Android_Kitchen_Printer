package com.posconnect.printer.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.posconnect.core.config.AppConfig
import com.posconnect.core.config.ConfigurationRepository
import com.posconnect.core.config.ConnectionType
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.adapters.PrinterAdapter
import com.posconnect.printer.model.DiscoveredPrinter
import com.posconnect.printer.model.PrinterErrorCodes
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import com.posconnect.printer.model.ReceiptData
import com.posconnect.printer.queue.PrintQueueManager
import com.posconnect.printer.registry.PrinterFactory
import com.posconnect.printer.transports.UsbTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

class PrinterManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configRepo = ConfigurationRepository.getInstance(context)

    val queueManager = PrintQueueManager()

    private var activeAdapter: PrinterAdapter? = null
    private val _printerStatus = MutableStateFlow(PrinterStatus.offline("Not connected"))
    val printerStatus: StateFlow<PrinterStatus> = _printerStatus.asStateFlow()

    private val _discoveredPrinters = MutableStateFlow<List<DiscoveredPrinter>>(emptyList())
    val discoveredPrinters: StateFlow<List<DiscoveredPrinter>> = _discoveredPrinters.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    companion object {
        @Volatile
        private var instance: PrinterManager? = null

        fun getInstance(context: Context): PrinterManager {
            return instance ?: synchronized(this) {
                instance ?: PrinterManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        scope.launch {
            configRepo.configState.collect { config ->
                if (config.printer.enabled && config.printer.usesEscPosEngine()) {
                    reconfigurePrinter(config.printer)
                } else {
                    activeAdapter?.disconnect()
                    activeAdapter = null
                    _printerStatus.value = PrinterStatus.offline(
                        if (config.printer.usesEscPosEngine()) "Not connected" else "Star engine handled in JS"
                    )
                }
            }
        }
    }

    fun getActiveAdapter(): PrinterAdapter? = activeAdapter

    private fun printerDisabledResult(): PrinterResult =
        PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Printer is disabled")

    private suspend fun reconfigurePrinter(config: PrinterConfig) {
        DiagnosticLogger.i(LogCategory.PRINTER, "PrinterManager", "Reconfiguring printer adapter for ${config.name} (${config.connectionType})")
        activeAdapter?.disconnect()
        val adapter = PrinterFactory.createPrinter(context, config)
        activeAdapter = adapter

        if (config.autoReconnect) {
            connectActivePrinter()
        }
    }

    suspend fun connectActivePrinter(): PrinterResult = withContext(Dispatchers.IO) {
        val config = configRepo.configState.value.printer
        if (!config.enabled) {
            _printerStatus.value = PrinterStatus.offline("Printer is disabled")
            return@withContext printerDisabledResult()
        }

        val adapter = activeAdapter ?: run {
            val newAdapter = PrinterFactory.createPrinter(context, config)
            activeAdapter = newAdapter
            newAdapter
        }

        val maxAttempts = (config.retryCount + 1).coerceAtLeast(1)
        var lastResult = PrinterResult.error(PrinterErrorCodes.PRINTER_OFFLINE, "Connect failed")

        for (attempt in 1..maxAttempts) {
            DiagnosticLogger.i(
                LogCategory.PRINTER,
                "PrinterManager",
                "Connecting active printer (attempt $attempt/$maxAttempts)...",
            )
            lastResult = adapter.connect()
            if (lastResult.success) {
                _printerStatus.value = PrinterStatus.onlineReady()
                DiagnosticLogger.i(LogCategory.PRINTER, "PrinterManager", "Active printer connected successfully")
                return@withContext lastResult
            }
            DiagnosticLogger.w(
                LogCategory.PRINTER,
                "PrinterManager",
                "Connect attempt $attempt failed: ${lastResult.message}",
            )
            if (attempt < maxAttempts) {
                delay(500L * attempt)
            }
        }

        _printerStatus.value = PrinterStatus.offline(lastResult.message)
        DiagnosticLogger.w(LogCategory.PRINTER, "PrinterManager", "Failed to connect active printer: ${lastResult.message}")
        lastResult
    }

    suspend fun disconnectActivePrinter(): PrinterResult = withContext(Dispatchers.IO) {
        val adapter = activeAdapter ?: return@withContext PrinterResult.success("No active printer")
        val res = adapter.disconnect()
        _printerStatus.value = PrinterStatus.offline("Disconnected")
        res
    }

    suspend fun checkStatus(): PrinterStatus = withContext(Dispatchers.IO) {
        val adapter = activeAdapter
        if (adapter == null) {
            val status = PrinterStatus.offline("No printer configured")
            _printerStatus.value = status
            return@withContext status
        }
        val status = adapter.getStatus()
        _printerStatus.value = status
        status
    }

    fun printReceiptQueued(receipt: ReceiptData, title: String = "Receipt Print"): String {
        return queueManager.enqueueReceipt(receipt, title) { activeAdapter }
    }

    fun printRawQueued(bytes: ByteArray, title: String = "Raw Bytes Print"): String {
        return queueManager.enqueueRaw(bytes, title) { activeAdapter }
    }

    suspend fun printReceiptDirect(receipt: ReceiptData): PrinterResult = withContext(Dispatchers.IO) {
        if (!configRepo.configState.value.printer.enabled) {
            return@withContext printerDisabledResult()
        }
        val adapter = activeAdapter ?: run {
            val config = configRepo.configState.value.printer
            val newAdapter = PrinterFactory.createPrinter(context, config)
            activeAdapter = newAdapter
            newAdapter
        }
        adapter.printReceipt(receipt)
    }

    suspend fun printTextDirect(text: String, isBold: Boolean = false): PrinterResult = withContext(Dispatchers.IO) {
        if (!configRepo.configState.value.printer.enabled) {
            return@withContext printerDisabledResult()
        }
        val adapter = activeAdapter ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured")
        adapter.printText(text, isBold)
    }

    suspend fun openCashDrawer(): PrinterResult = withContext(Dispatchers.IO) {
        if (!configRepo.configState.value.printer.enabled) {
            return@withContext printerDisabledResult()
        }
        val adapter = activeAdapter ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured")
        adapter.openCashDrawer()
    }

    suspend fun cutPaper(): PrinterResult = withContext(Dispatchers.IO) {
        if (!configRepo.configState.value.printer.enabled) {
            return@withContext printerDisabledResult()
        }
        val adapter = activeAdapter ?: return@withContext PrinterResult.error(PrinterErrorCodes.PRINTER_NOT_FOUND, "No printer configured")
        val printer = configRepo.configState.value.printer
        adapter.cutPaper(partial = printer.cutMode != "full")
    }

    @SuppressLint("MissingPermission")
    suspend fun discoverPrinters(type: ConnectionType): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val results = mutableListOf<DiscoveredPrinter>()

        try {
            when (type) {
                ConnectionType.BLUETOOTH, ConnectionType.BLE -> {
                    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                    if (adapter != null && adapter.isEnabled) {
                        val bonded = adapter.bondedDevices
                        for (dev in bonded) {
                            val name = dev.name ?: "Unknown Bluetooth Device"
                            val isPrinter = name.contains("print", ignoreCase = true) ||
                                    name.contains("pos", ignoreCase = true) ||
                                    name.contains("rpp", ignoreCase = true) ||
                                    name.contains("mpt", ignoreCase = true) ||
                                    name.contains("xp-", ignoreCase = true)

                            val brand = when {
                                name.contains("epson", ignoreCase = true) -> PrinterBrand.EPSON
                                name.contains("star", ignoreCase = true) -> PrinterBrand.STAR
                                name.contains("xprint", ignoreCase = true) -> PrinterBrand.XPRINTER
                                name.contains("rongta", ignoreCase = true) -> PrinterBrand.RONGTA
                                name.contains("gprint", ignoreCase = true) -> PrinterBrand.GPRINTER
                                else -> PrinterBrand.GENERIC_ESC_POS
                            }

                            results.add(
                                DiscoveredPrinter(
                                    name = name,
                                    identifier = dev.address,
                                    connectionType = ConnectionType.BLUETOOTH,
                                    brand = brand,
                                    details = if (isPrinter) "Paired Thermal Printer" else "Paired Bluetooth Device"
                                )
                            )
                        }
                    }
                }
                ConnectionType.USB -> {
                    val usbDevices = UsbTransport.findAttachedUsbPrinters(context)
                    for (dev in usbDevices) {
                        results.add(
                            DiscoveredPrinter(
                                name = dev.productName ?: "USB Thermal Printer",
                                identifier = "VID:${dev.vendorId} PID:${dev.productId}",
                                connectionType = ConnectionType.USB,
                                brand = PrinterBrand.GENERIC_ESC_POS,
                                details = "USB Host Attached (Device ID: ${dev.deviceId})"
                            )
                        )
                    }
                }
                ConnectionType.LAN -> {
                    // Check current saved LAN IP or default subnet candidate
                    val currentIp = configRepo.configState.value.printer.ip
                    val isOnline = testTcpPort(currentIp, 9100, 1000)
                    results.add(
                        DiscoveredPrinter(
                            name = "Configured LAN Printer ($currentIp)",
                            identifier = "$currentIp:9100",
                            connectionType = ConnectionType.LAN,
                            brand = configRepo.configState.value.printer.brand,
                            isConnected = isOnline,
                            details = if (isOnline) "Reachable on Port 9100" else "Configured IP"
                        )
                    )
                }
                ConnectionType.BUILTIN, ConnectionType.VENDOR -> {
                    results.add(
                        DiscoveredPrinter(
                            name = "Internal POS Terminal Printer",
                            identifier = "BUILTIN_01",
                            connectionType = ConnectionType.BUILTIN,
                            brand = PrinterBrand.SUNMI,
                            details = "Integrated High-Speed Thermal Hardware"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.PRINTER, "PrinterManager", "Discovery error for $type: ${e.message}")
        } finally {
            _discoveredPrinters.value = results
            _isScanning.value = false
        }

        results
    }

    private fun testTcpPort(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}

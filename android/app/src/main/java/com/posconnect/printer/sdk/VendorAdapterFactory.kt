package com.posconnect.printer.sdk

import android.content.Context
import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.config.PrinterConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.adapters.PrinterAdapter
import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.registry.PrinterRegistry

object VendorAdapterFactory {

    fun tryCreateSdkAdapter(
        context: Context,
        config: PrinterConfig,
        profile: PrinterProfile,
    ): PrinterAdapter? {
        if (!config.usesEscPosEngine()) return null
        if (!VendorSdkAvailability.usesVendorSdkForPrint(config)) return null

        return try {
            when (config.brand) {
                PrinterBrand.EPSON ->
                    if (VendorSdkAvailability.epson) EpsonSdkAdapter(context, config, profile) else null
                PrinterBrand.XPRINTER ->
                    if (VendorSdkAvailability.xprinter) XPrinterSdkAdapter(context, config, profile) else null
                PrinterBrand.GPRINTER ->
                    if (VendorSdkAvailability.gprinter) GPrinterSdkAdapter(context, config, profile) else null
                else -> null
            }?.also {
                DiagnosticLogger.i(
                    LogCategory.SDK,
                    "VendorAdapterFactory",
                    "Using vendor SDK adapter for ${config.brand}: ${VendorSdkAvailability.printPath(config)}",
                )
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(
                LogCategory.SDK,
                "VendorAdapterFactory",
                "SDK adapter unavailable for ${config.brand}: ${e.localizedMessage}",
            )
            null
        }
    }

    fun createWithFallback(
        context: Context,
        config: PrinterConfig,
        profile: PrinterProfile,
        transportFactory: () -> com.posconnect.printer.transports.PrinterTransport,
    ): PrinterAdapter {
        tryCreateSdkAdapter(context, config, profile)?.let { return it }
        val transport = transportFactory()
        return PrinterRegistry.createAdapter(config.brand, transport, profile)
    }
}

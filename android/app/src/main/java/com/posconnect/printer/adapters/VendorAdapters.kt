package com.posconnect.printer.adapters

import com.posconnect.core.config.PrinterBrand
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.transports.PrinterTransport

class EpsonAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.EPSON) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "EpsonAdapter", "Initialized Epson printer adapter")
    }
}

class StarAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.STAR) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "StarAdapter", "Initialized Star Micronics adapter")
    }
}

class SunmiAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.SUNMI) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "SunmiAdapter", "Initialized SUNMI built-in/external printer adapter")
    }
}

class XPrinterAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.XPRINTER) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "XPrinterAdapter", "Initialized XPrinter adapter")
    }
}

class RongtaAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.RONGTA) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "RongtaAdapter", "Initialized Rongta adapter")
    }
}

class GPrinterAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.GPRINTER) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "GPrinterAdapter", "Initialized GPrinter adapter")
    }
}

class CustomVendorAdapter(
    transport: PrinterTransport,
    profile: PrinterProfile
) : GenericEscPosAdapter(transport, profile, PrinterBrand.CUSTOM) {
    init {
        DiagnosticLogger.i(LogCategory.SDK, "CustomVendorAdapter", "Custom vendor adapter using Generic ESC/POS until an official SDK is supplied")
    }
}

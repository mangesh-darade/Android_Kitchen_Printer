package com.posconnect.printer.model

import com.posconnect.core.config.PrinterWidth

data class PrinterProfile(
    val widthMm: Int = 80,
    val printableWidthMm: Int = 72,
    val charactersPerLine: Int = 48,
    val dpi: Int = 203,
    val encoding: String = "UTF-8",
    val cutSupported: Boolean = true,
    val qrSupported: Boolean = true,
    val barcodeSupported: Boolean = true,
    val cashDrawerSupported: Boolean = true,
    val imageSupported: Boolean = true
) {
    companion object {
        fun forWidth(width: PrinterWidth): PrinterProfile {
            return when (width) {
                PrinterWidth.THREE_INCH -> PrinterProfile(
                    widthMm = 80,
                    printableWidthMm = 72,
                    charactersPerLine = 48,
                    dpi = 203,
                    encoding = "UTF-8",
                    cutSupported = true,
                    qrSupported = true,
                    barcodeSupported = true,
                    cashDrawerSupported = true,
                    imageSupported = true
                )
                PrinterWidth.FOUR_INCH -> PrinterProfile(
                    widthMm = 110,
                    printableWidthMm = 104,
                    charactersPerLine = 64,
                    dpi = 203,
                    encoding = "UTF-8",
                    cutSupported = true,
                    qrSupported = true,
                    barcodeSupported = true,
                    cashDrawerSupported = true,
                    imageSupported = true
                )
            }
        }
    }
}

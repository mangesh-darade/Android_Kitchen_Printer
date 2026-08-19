package com.posconnect.printer.adapters

import android.graphics.Bitmap
import com.posconnect.core.config.PrinterBrand
import com.posconnect.printer.model.PrinterProfile
import com.posconnect.printer.model.PrinterResult
import com.posconnect.printer.model.PrinterStatus
import com.posconnect.printer.model.ReceiptData
import com.posconnect.printer.transports.PrinterTransport
import org.json.JSONObject

interface PrinterAdapter {
    val brand: PrinterBrand
    val transport: PrinterTransport
    val profile: PrinterProfile

    suspend fun initialize(): PrinterResult
    suspend fun connect(): PrinterResult
    suspend fun disconnect(): PrinterResult
    suspend fun isConnected(): Boolean
    suspend fun getStatus(): PrinterStatus
    suspend fun print(rawBytes: ByteArray): PrinterResult
    suspend fun printReceipt(receipt: ReceiptData): PrinterResult
    suspend fun printText(text: String, isBold: Boolean = false, textSizeSp: Float = 22f): PrinterResult
    suspend fun printImage(bitmap: Bitmap): PrinterResult
    suspend fun printBarcode(data: String): PrinterResult
    suspend fun printQRCode(data: String): PrinterResult
    suspend fun cutPaper(partial: Boolean = false): PrinterResult
    suspend fun openCashDrawer(): PrinterResult
    suspend fun testPrint(): PrinterResult
    fun getPrinterInfo(): JSONObject
}

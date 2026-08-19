package com.posconnect.printer.model

import org.json.JSONArray
import org.json.JSONObject

data class ReceiptHeader(
    val logoBase64: String? = null,
    val businessName: String = "POS CONNECT STORE",
    val branchName: String? = null,
    val address: String = "Main Market Road",
    val phone: String = "+91 98765 43210",
    val email: String? = null,
    val gstNumber: String? = null,
    val invoiceNumber: String = "INV-00101",
    val orderNumber: String? = null,
    val tableNumber: String? = null,
    val dateTime: String = "",
    val cashier: String = "Admin"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("businessName", businessName)
        if (branchName != null) put("branchName", branchName)
        put("address", address)
        put("phone", phone)
        if (email != null) put("email", email)
        if (gstNumber != null) put("gstNumber", gstNumber)
        put("invoiceNumber", invoiceNumber)
        if (orderNumber != null) put("orderNumber", orderNumber)
        if (tableNumber != null) put("tableNumber", tableNumber)
        put("dateTime", dateTime)
        put("cashier", cashier)
    }

    companion object {
        fun fromJson(json: JSONObject?): ReceiptHeader {
            if (json == null) return ReceiptHeader()
            return ReceiptHeader(
                logoBase64 = json.optString("logo").takeIf { it.isNotBlank() },
                businessName = json.optString("businessName", "POS STORE"),
                branchName = json.optString("branchName").takeIf { it.isNotBlank() },
                address = json.optString("address", ""),
                phone = json.optString("phone", ""),
                email = json.optString("email").takeIf { it.isNotBlank() },
                gstNumber = json.optString("gstNumber").takeIf { it.isNotBlank() } ?: json.optString("taxNumber").takeIf { it.isNotBlank() },
                invoiceNumber = json.optString("invoiceNumber", "INV-001"),
                orderNumber = json.optString("orderNumber").takeIf { it.isNotBlank() },
                tableNumber = json.optString("tableNumber").takeIf { it.isNotBlank() },
                dateTime = json.optString("dateTime", ""),
                cashier = json.optString("cashier", "Cashier")
            )
        }
    }
}

data class ReceiptItem(
    val name: String,
    val qty: Double,
    val price: Double,
    val discount: Double = 0.0,
    val amount: Double = qty * price,
    val hsnCode: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("qty", qty)
        put("price", price)
        put("discount", discount)
        put("amount", amount)
        if (hsnCode != null) put("hsnCode", hsnCode)
    }

    companion object {
        fun fromJson(json: JSONObject): ReceiptItem {
            val qty = json.optDouble("qty", 1.0)
            val price = json.optDouble("price", 0.0)
            val amount = if (json.has("amount")) json.optDouble("amount") else qty * price
            return ReceiptItem(
                name = json.optString("name", "Item"),
                qty = qty,
                price = price,
                discount = json.optDouble("discount", 0.0),
                amount = amount,
                hsnCode = json.optString("hsnCode").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class ReceiptPayment(
    val mode: String = "Cash",
    val paid: Double = 0.0,
    val change: Double = 0.0,
    val transactionRef: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode)
        put("paid", paid)
        put("change", change)
        if (transactionRef != null) put("transactionRef", transactionRef)
    }

    companion object {
        fun fromJson(json: JSONObject?): ReceiptPayment {
            if (json == null) return ReceiptPayment()
            return ReceiptPayment(
                mode = json.optString("mode", "Cash"),
                paid = json.optDouble("paid", 0.0),
                change = json.optDouble("change", 0.0),
                transactionRef = json.optString("transactionRef").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class ReceiptData(
    val paperWidth: String = "3inch",
    val header: ReceiptHeader = ReceiptHeader(),
    val items: List<ReceiptItem> = emptyList(),
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val payment: ReceiptPayment = ReceiptPayment(),
    val qrCode: String? = null,
    val barcode: String? = null,
    val footer: String = "Thank you for your visit!",
    val customerName: String? = null,
    val customerPhone: String? = null,
    val isUnicode: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("paperWidth", paperWidth)
        put("header", header.toJson())
        val itemsArr = JSONArray()
        items.forEach { itemsArr.put(it.toJson()) }
        put("items", itemsArr)
        put("subtotal", subtotal)
        put("tax", tax)
        put("discount", discount)
        put("grandTotal", grandTotal)
        put("payment", payment.toJson())
        if (qrCode != null) put("qrCode", qrCode)
        if (barcode != null) put("barcode", barcode)
        put("footer", footer)
        if (customerName != null) put("customerName", customerName)
        if (customerPhone != null) put("customerPhone", customerPhone)
    }

    companion object {
        fun fromJson(json: JSONObject): ReceiptData {
            val itemsList = mutableListOf<ReceiptItem>()
            val itemsArr = json.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.optJSONObject(i)
                    if (obj != null) itemsList.add(ReceiptItem.fromJson(obj))
                }
            }

            val subtotal = json.optDouble("subtotal", itemsList.sumOf { it.amount })
            val tax = json.optDouble("tax", 0.0)
            val discount = json.optDouble("discount", 0.0)
            val grandTotal = if (json.has("grandTotal")) json.optDouble("grandTotal") else (subtotal + tax - discount)

            return ReceiptData(
                paperWidth = json.optString("paperWidth", "3inch"),
                header = ReceiptHeader.fromJson(json.optJSONObject("header")),
                items = itemsList,
                subtotal = subtotal,
                tax = tax,
                discount = discount,
                grandTotal = grandTotal,
                payment = ReceiptPayment.fromJson(json.optJSONObject("payment")),
                qrCode = json.optString("qrCode").takeIf { it.isNotBlank() } ?: json.optString("qr").takeIf { it.isNotBlank() },
                barcode = json.optString("barcode").takeIf { it.isNotBlank() },
                footer = json.optString("footer", "Thank You!"),
                customerName = json.optString("customerName").takeIf { it.isNotBlank() },
                customerPhone = json.optString("customerPhone").takeIf { it.isNotBlank() }
            )
        }

        fun sampleReceipt(isFourInch: Boolean = false): ReceiptData {
            return ReceiptData(
                paperWidth = if (isFourInch) "4inch" else "3inch",
                header = ReceiptHeader(
                    businessName = "SHREE SWAMI SAMARTH SWEETS",
                    branchName = "Pune Main Branch",
                    address = "104 F.C. Road, Shivajinagar, Pune 411005",
                    phone = "+91 20 2553 4400",
                    gstNumber = "27AAAAA0000A1Z5",
                    invoiceNumber = "POS-2026/0892",
                    dateTime = "17-Aug-2026 10:45 AM",
                    cashier = "Mangesh S."
                ),
                items = listOf(
                    ReceiptItem("Kaju Katli (काजू कतली) 500g", 1.0, 480.0, 0.0, 480.0),
                    ReceiptItem("Special Masala Chai (चहा)", 2.0, 30.0, 0.0, 60.0),
                    ReceiptItem("Kanda Poha (कांदा पोहे)", 2.0, 45.0, 0.0, 90.0),
                    ReceiptItem("Gulab Jamun (गुलाबजाम)", 4.0, 25.0, 0.0, 100.0)
                ),
                subtotal = 730.0,
                tax = 36.50,
                discount = 20.0,
                grandTotal = 746.50,
                payment = ReceiptPayment(mode = "UPI / PhonePe", paid = 746.50, change = 0.0, transactionRef = "UPI-982348123"),
                qrCode = "upi://pay?pa=posconnect@bank&pn=ShreeSwamiSamarth&am=746.50&cu=INR&tn=POS-2026/0892",
                barcode = "8901234567890",
                footer = "धन्यवाद! पुन्हा नक्की भेट द्या! / Thank you, visit again!",
                customerName = "Rahul Deshmukh",
                customerPhone = "9822011223"
            )
        }

        fun sampleUnicodeReceipt(isFourInch: Boolean = false): ReceiptData {
            return ReceiptData(
                paperWidth = if (isFourInch) "4inch" else "3inch",
                header = ReceiptHeader(
                    businessName = "पुणे स्पेशल उपहार गृह",
                    branchName = "डेक्कन जिमखाना शाखा",
                    address = "एफ.सी. रोड, शिवाजीनगर, पुणे ४११००४",
                    phone = "०२०-२५५३४४००",
                    gstNumber = "27ABCDE1234F1Z5",
                    invoiceNumber = "MR-2026-0042",
                    dateTime = "17-Aug-2026 11:30 AM",
                    cashier = "मंगेश सातरी"
                ),
                items = listOf(
                    ReceiptItem("झणझणीत मिसळ पाव", 2.0, 90.0, 0.0, 180.0),
                    ReceiptItem("गरमागरम कांदा भजी", 1.0, 60.0, 0.0, 60.0),
                    ReceiptItem("शाही गुलाबजाम (४ नग)", 1.0, 100.0, 0.0, 100.0),
                    ReceiptItem("मसाला ताक", 2.0, 25.0, 0.0, 50.0)
                ),
                subtotal = 390.0,
                tax = 19.50,
                discount = 10.0,
                grandTotal = 399.50,
                payment = ReceiptPayment(mode = "रोख (Cash)", paid = 500.0, change = 100.50),
                qrCode = "upi://pay?pa=punemisal@upi&pn=PuneSpecial&am=399.50&cu=INR",
                footer = "आपल्या सेवेत सदैव तत्पर! पुन्हा भेट द्या!",
                customerName = "अमित कुलकर्णी",
                customerPhone = "9822199001",
                isUnicode = true
            )
        }
    }
}

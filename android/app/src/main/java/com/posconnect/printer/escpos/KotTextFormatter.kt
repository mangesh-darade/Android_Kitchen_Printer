package com.posconnect.printer.escpos

import java.util.Locale

object KotTextFormatter {

    /**
     * Formats multi-line plain text or KOT slip so that:
     * 1. Divider lines (`---`, `===`) expand to fill the entire printer width (e.g. 48 cols for 3-inch, 64 for 4-inch).
     * 2. Header and metadata lines (KOT No, Date/Time, Order Type, Table) are centered across the full width.
     * 3. Item rows and table headers have the item name on the left and quantity/price aligned at the far right margin.
     * 4. Footer and table summary lines are centered and clearly visible.
     */
    fun format(rawText: String, targetCpl: Int = 48): String {
        if (rawText.isBlank()) return ""
        val lines = rawText.split(Regex("\r?\n"))
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
        val formattedLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                formattedLines.add("")
                continue
            }

            // 1. Divider Lines (e.g. "----------------------------", "=================")
            if (isDivider(trimmed)) {
                val char = if (trimmed.contains('=')) "=" else "-"
                formattedLines.add(char.repeat(targetCpl))
                continue
            }

            // 2. Table Header Line (e.g. "Items Qty", "ITEM QTY", "Item Rate Qty Total")
            if (isTableHeader(trimmed)) {
                formattedLines.add(formatTableHeader(trimmed, targetCpl))
                continue
            }

            // 3. Item Row with Quantity / Amount (e.g. "• American Cheese Burger (Small)     1.00")
            val itemRowMatch = parseItemRow(trimmed)
            if (itemRowMatch != null) {
                formattedLines.add(formatTwoColumnRow(itemRowMatch.first, itemRowMatch.second, targetCpl))
                continue
            }

            // 4. Header & Meta Lines (KOT No, Date, Order Type, Table number)
            if (isHeaderOrMeta(trimmed)) {
                formattedLines.add(centerText(trimmed, targetCpl))
                continue
            }

            // 5. Short lines with leading spaces in original text -> center them
            if (line.startsWith("   ") || trimmed.length <= (targetCpl * 0.6).toInt()) {
                formattedLines.add(centerText(trimmed, targetCpl))
                continue
            }

            // 6. Default Fallback -> Left aligned with proper trimming
            formattedLines.add(trimmed)
        }

        return formattedLines.joinToString("\n")
    }

    fun isDivider(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        val firstChar = trimmed[0]
        if (firstChar != '-' && firstChar != '=' && firstChar != '*' && firstChar != '_') return false
        return trimmed.all { it == firstChar || it == ' ' }
    }

    fun isTableHeader(trimmed: String): Boolean {
        val lower = trimmed.lowercase(Locale.ROOT)
        return (lower.startsWith("item") || lower.startsWith("items") || lower.startsWith("तपशील")) &&
                (lower.contains("qty") || lower.contains("quantity") || lower.contains("rate") || lower.contains("amt") || lower.contains("amount") || lower.contains("नग"))
    }

    fun formatTableHeader(trimmed: String, cols: Int): String {
        // Find left label (Items) and right label (Qty or Amount)
        val parts = trimmed.split(Regex("\\s{2,}"))
        if (parts.size >= 2) {
            val left = parts.first().trim()
            val right = parts.drop(1).joinToString(" ").trim()
            return formatTwoColumnRow(left, right, cols)
        }
        // Fallback: check standard keywords
        val lower = trimmed.lowercase(Locale.ROOT)
        val qtyIdx = lower.indexOf("qty").takeIf { it >= 0 } ?: lower.indexOf("quantity")
        if (qtyIdx > 0) {
            val left = trimmed.substring(0, qtyIdx).trim()
            val right = trimmed.substring(qtyIdx).trim()
            return formatTwoColumnRow(left, right, cols)
        }
        return formatTwoColumnRow(trimmed, "", cols)
    }

    /**
     * Parses lines containing an item and quantity/amount at the end.
     * Examples:
     * - "• American Cheese Burger (Small)     1.00" -> ("• American Cheese Burger (Small)", "1.00")
     * - "Veg Burger  2" -> ("Veg Burger", "2")
     * - "Masala Dosa  1.00  120.00" -> ("Masala Dosa", "1.00  120.00")
     */
    fun parseItemRow(trimmed: String): Pair<String, String>? {
        // Pattern 1: Multiple spaces separating name and quantity/price at the end
        val multiSpaceRegex = Regex("""^(.*?)\s{2,}(\d+(?:\.\d+)?(?:\s+[\d\.\,]+)?)\s*$""")
        val multiMatch = multiSpaceRegex.find(trimmed)
        if (multiMatch != null) {
            val item = multiMatch.groupValues[1].trim()
            val qty = multiMatch.groupValues[2].trim()
            if (item.isNotEmpty() && !isHeaderOrMeta(item)) {
                return Pair(item, qty)
            }
        }

        // Pattern 2: Lines starting with bullet, dash, or number followed by quantity at end
        val bulletRegex = Regex("""^([•\-\*]\s*.*?)\s+(\d+(?:\.\d+)?)\s*$""")
        val bulletMatch = bulletRegex.find(trimmed)
        if (bulletMatch != null) {
            val item = bulletMatch.groupValues[1].trim()
            val qty = bulletMatch.groupValues[2].trim()
            return Pair(item, qty)
        }

        return null
    }

    fun isHeaderOrMeta(trimmed: String): Boolean {
        val lower = trimmed.lowercase(Locale.ROOT)
        return lower.startsWith("kot no") ||
                lower.startsWith("token no") ||
                lower.startsWith("table") ||
                lower.startsWith("order type") ||
                lower.startsWith("date") ||
                lower.startsWith("time") ||
                lower.startsWith("cashier") ||
                lower.startsWith("waiter") ||
                lower.startsWith("customer") ||
                lower.startsWith("invoice") ||
                lower.startsWith("bill no") ||
                lower.startsWith("gst") ||
                lower.startsWith("thank you") ||
                (lower.contains("am") && lower.contains("/")) ||
                (lower.contains("pm") && lower.contains("/"))
    }

    fun formatTwoColumnRow(left: String, right: String, cols: Int): String {
        if (right.isEmpty()) return left
        val maxLeftLen = (cols - right.length - 1).coerceAtLeast(0)

        if (left.length <= maxLeftLen) {
            val spaces = (cols - left.length - right.length).coerceAtLeast(1)
            return left + " ".repeat(spaces) + right
        }

        // Wrap long left text so item name is not truncated
        val words = left.split(" ")
        val wrappedLines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= maxLeftLen) {
                currentLine.append(" ").append(word)
            } else {
                wrappedLines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            wrappedLines.add(currentLine.toString())
        }

        val firstLine = wrappedLines.firstOrNull() ?: left.take(maxLeftLen)
        val spaces = (cols - firstLine.length - right.length).coerceAtLeast(1)
        val sb = StringBuilder()
        sb.append(firstLine).append(" ".repeat(spaces)).append(right)

        for (i in 1 until wrappedLines.size) {
            sb.append("\n").append(wrappedLines[i])
        }

        return sb.toString()
    }

    fun centerText(text: String, cols: Int): String {
        val clean = text.trim()
        if (clean.length >= cols) return clean
        val pad = (cols - clean.length) / 2
        return " ".repeat(pad) + clean
    }
}

package com.hack.util

import java.util.Locale

object PriceFormatter {
    const val CURRENCY_SYMBOL = "₹"

    /**
     * Formats a given price with Indian numbering grouping (lakhs/crores) and ₹ symbol.
     * Examples:
     *   10.0 -> "₹10"
     *   999.0 -> "₹999"
     *   1499.0 -> "₹1,499"
     *   12999.0 -> "₹12,999"
     *   125000.0 -> "₹1,25,000"
     *   1000000.0 -> "₹10,00,000"
     *   99.50 -> "₹99.50"
     */
    fun format(price: Double): String {
        return "$CURRENCY_SYMBOL${formatNumber(price)}"
    }

    fun formatWithoutSymbol(price: Double): String {
        return formatNumber(price)
    }

    private fun formatNumber(price: Double): String {
        val isNegative = price < 0
        val absVal = kotlin.math.abs(price)
        val formatted = String.format(Locale.US, "%.2f", absVal)
        val parts = formatted.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else "00"

        val groupedInt = if (intPart.length <= 3) {
            intPart
        } else {
            val last3 = intPart.takeLast(3)
            var rest = intPart.dropLast(3)
            val groups = mutableListOf<String>()
            while (rest.length > 2) {
                groups.add(rest.takeLast(2))
                rest = rest.dropLast(2)
            }
            if (rest.isNotEmpty()) {
                groups.add(rest)
            }
            groups.reverse()
            groups.joinToString(",") + "," + last3
        }

        val result = if (decPart == "00") {
            groupedInt
        } else {
            var trimmedDec = decPart
            if (trimmedDec.endsWith("0")) {
                // e.g. 50 paise stays 50
                trimmedDec = decPart
            }
            "$groupedInt.$trimmedDec"
        }

        return if (isNegative) "-$result" else result
    }

    fun validatePrice(input: String): Double? {
        val cleaned = input.trim().removePrefix(CURRENCY_SYMBOL).trim()
        val parsed = cleaned.toDoubleOrNull() ?: return null
        return if (parsed > 0.0) parsed else null
    }
}

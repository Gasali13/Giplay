package com.personal.animeplayer.util

import java.util.Comparator
import java.util.regex.Pattern

/**
 * NaturalOrderComparator enables intuitive alphanumeric sorting.
 * E.g., "Episode 1", "Episode 2", "Episode 10" instead of "Episode 1", "Episode 10", "Episode 2".
 */
class NaturalOrderComparator : Comparator<String> {

    companion object {
        private val NUMERIC_PATTERN = Pattern.compile("(\\d+)|(\\D+)")
    }

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val matcher1 = NUMERIC_PATTERN.matcher(s1)
        val matcher2 = NUMERIC_PATTERN.matcher(s2)

        while (matcher1.find() && matcher2.find()) {
            val chunk1 = matcher1.group()
            val chunk2 = matcher2.group()

            val isDigit1 = Character.isDigit(chunk1[0])
            val isDigit2 = Character.isDigit(chunk2[0])

            val result: Int = if (isDigit1 && isDigit2) {
                // Compare numeric values
                try {
                    val num1 = chunk1.toLong()
                    val num2 = chunk2.toLong()
                    num1.compareTo(num2)
                } catch (e: NumberFormatException) {
                    // Fallback to BigInteger or string comparison if number is too large
                    chunk1.compareTo(chunk2)
                }
            } else {
                chunk1.compareTo(chunk2, ignoreCase = true)
            }

            if (result != 0) {
                return result
            }
        }

        // Check remaining lengths
        return s1.length.compareTo(s2.length)
    }
}

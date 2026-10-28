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

    private fun tokenize(s: String): List<String> {
        val tokens = ArrayList<String>()
        val matcher = NUMERIC_PATTERN.matcher(s)
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val tokens1 = tokenize(s1)
        val tokens2 = tokenize(s2)
        val size1 = tokens1.size
        val size2 = tokens2.size
        val minSize = minOf(size1, size2)

        for (i in 0 until minSize) {
            val chunk1 = tokens1[i]
            val chunk2 = tokens2[i]

            val isDigit1 = Character.isDigit(chunk1[0])
            val isDigit2 = Character.isDigit(chunk2[0])

            val result: Int = if (isDigit1 && isDigit2) {
                // Compare numeric values
                try {
                    val num1 = chunk1.toLong()
                    val num2 = chunk2.toLong()
                    num1.compareTo(num2)
                } catch (e: NumberFormatException) {
                    chunk1.compareTo(chunk2)
                }
            } else {
                chunk1.compareTo(chunk2, ignoreCase = true)
            }

            if (result != 0) {
                return result
            }
        }

        // If one string has more tokens (e.g. "Episode 1" vs "Episode 1 Special"), the shorter token list comes first
        if (size1 != size2) {
            return size1.compareTo(size2)
        }

        // If all chunks and token counts are equal, tie-break by string length (e.g. "Episode 1" before "Episode 01")
        if (s1.length != s2.length) {
            return s1.length.compareTo(s2.length)
        }

        return s1.compareTo(s2)
    }
}

package com.example.mpod.util

import java.util.Comparator
import java.util.regex.Pattern

/**
 * Natural order comparator for sorting human-readable filenames and chapter numbers
 * (e.g. "Chapter 1", "Chapter 2", "Chapter 10").
 */
class NaturalOrderComparator<T> private constructor(
    private val keySelector: ((T) -> String)? = null
) : Comparator<T> {

    override fun compare(o1: T?, o2: T?): Int {
        if (o1 === o2) return 0
        if (o1 == null) return -1
        if (o2 == null) return 1

        val s1 = if (keySelector != null) keySelector.invoke(o1) else o1.toString()
        val s2 = if (keySelector != null) keySelector.invoke(o2) else o2.toString()

        return compareStrings(s1, s2)
    }

    companion object {
        private val CHUNK_PATTERN = Pattern.compile("(\\d+|\\D+)")

        val INSTANCE: NaturalOrderComparator<String> = NaturalOrderComparator()

        fun <T> comparing(selector: (T) -> String): NaturalOrderComparator<T> {
            return NaturalOrderComparator(selector)
        }

        fun compareStrings(s1: String, s2: String): Int {
            if (s1 == s2) return 0
            val chunks1 = splitIntoChunks(s1)
            val chunks2 = splitIntoChunks(s2)

            val minSize = minOf(chunks1.size, chunks2.size)
            for (i in 0 until minSize) {
                val c1 = chunks1[i]
                val c2 = chunks2[i]

                val isC1Digit = c1.isNotEmpty() && c1[0].isDigit()
                val isC2Digit = c2.isNotEmpty() && c2[0].isDigit()

                if (isC1Digit && isC2Digit) {
                    val numCompare = compareNumericChunks(c1, c2)
                    if (numCompare != 0) return numCompare
                } else {
                    val textCompare = c1.compareTo(c2, ignoreCase = true)
                    if (textCompare != 0) return textCompare
                    val caseCompare = c1.compareTo(c2)
                    if (caseCompare != 0) return caseCompare
                }
            }

            return chunks1.size.compareTo(chunks2.size)
        }

        private fun splitIntoChunks(s: String): List<String> {
            val matcher = CHUNK_PATTERN.matcher(s)
            val chunks = mutableListOf<String>()
            while (matcher.find()) {
                chunks.add(matcher.group())
            }
            return chunks
        }

        private fun compareNumericChunks(c1: String, c2: String): Int {
            val val1 = c1.toBigIntegerOrNull()
            val val2 = c2.toBigIntegerOrNull()
            if (val1 != null && val2 != null) {
                val cmp = val1.compareTo(val2)
                if (cmp != 0) return cmp
            }
            return c1.length.compareTo(c2.length)
        }
    }
}

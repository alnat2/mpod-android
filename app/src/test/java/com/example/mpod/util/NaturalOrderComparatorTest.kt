package com.example.mpod.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun sortsNumericChunksInNaturalOrder() {
        val input = listOf("10.mp3", "1.mp3", "2.mp3", "20.mp3", "3.mp3")
        val sorted = input.sortedWith(NaturalOrderComparator.INSTANCE)
        assertEquals(listOf("1.mp3", "2.mp3", "3.mp3", "10.mp3", "20.mp3"), sorted)
    }

    @Test
    fun handlesLeadingZeros() {
        val input = listOf("track 10", "track 01", "track 2", "track 02")
        val sorted = input.sortedWith(NaturalOrderComparator.INSTANCE)
        // 01 comes before 2/02, 2 and 02 compare numerically with tie-breaker
        assertEquals("track 01", sorted[0])
        assertEquals("track 10", sorted.last())
    }

    @Test
    fun sortsBookChaptersCorrectly() {
        val input = listOf(
            "Chapter 10 - The Council of Elrond.mp3",
            "Chapter 1 - An Unexpected Party.mp3",
            "Chapter 2 - Roast Mutton.mp3",
            "Chapter 21 - The Last Stage.mp3",
            "Chapter 3 - A Short Rest.mp3"
        )
        val sorted = input.sortedWith(NaturalOrderComparator.INSTANCE)
        assertEquals("Chapter 1 - An Unexpected Party.mp3", sorted[0])
        assertEquals("Chapter 2 - Roast Mutton.mp3", sorted[1])
        assertEquals("Chapter 3 - A Short Rest.mp3", sorted[2])
        assertEquals("Chapter 10 - The Council of Elrond.mp3", sorted[3])
        assertEquals("Chapter 21 - The Last Stage.mp3", sorted[4])
    }

    @Test
    fun caseInsensitiveComparisonWithCaseSensitiveFallback() {
        val input = listOf("chapter 2", "Chapter 1", "CHAPTER 10")
        val sorted = input.sortedWith(NaturalOrderComparator.INSTANCE)
        assertEquals("Chapter 1", sorted[0])
        assertEquals("chapter 2", sorted[1])
        assertEquals("CHAPTER 10", sorted[2])
    }

    @Test
    fun worksWithCustomKeySelector() {
        data class Item(val name: String, val id: Long)
        val items = listOf(Item("ch 10", 1), Item("ch 2", 2), Item("ch 1", 3))
        val sorted = items.sortedWith(NaturalOrderComparator.comparing { it.name })
        assertEquals(listOf("ch 1", "ch 2", "ch 10"), sorted.map { it.name })
    }

    @Test
    fun handlesMixedDigitAndNonDigitChunksDeterministically() {
        val input = listOf("track-1", "track1", "track-2", "track2")
        val sorted = input.sortedWith(NaturalOrderComparator.INSTANCE)
        // Digits sort before symbols/letters
        assertEquals(listOf("track1", "track2", "track-1", "track-2"), sorted)
    }

    @Test
    fun handlesIdenticalAndEmptyStrings() {
        assertEquals(0, NaturalOrderComparator.compareStrings("same", "same"))
        assertEquals(0, NaturalOrderComparator.compareStrings("", ""))
        assertTrue(NaturalOrderComparator.compareStrings("", "a") < 0)
        assertTrue(NaturalOrderComparator.compareStrings("a", "") > 0)
    }
}

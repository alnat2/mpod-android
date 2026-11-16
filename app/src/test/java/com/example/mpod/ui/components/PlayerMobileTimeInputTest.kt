package com.example.mpod.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMobileTimeInputTest {

    @Test
    fun formatInputDigits_emptyReturnsZero() {
        assertEquals("0:00", formatInputDigits(""))
    }

    @Test
    fun formatInputDigits_singleDigit() {
        assertEquals("0:05", formatInputDigits("5"))
    }

    @Test
    fun formatInputDigits_twoDigits() {
        assertEquals("0:45", formatInputDigits("45"))
    }

    @Test
    fun formatInputDigits_threeDigits() {
        assertEquals("1:23", formatInputDigits("123"))
    }

    @Test
    fun formatInputDigits_fourDigits() {
        assertEquals("12:34", formatInputDigits("1234"))
    }

    @Test
    fun formatInputDigits_fiveDigits() {
        assertEquals("1:23:45", formatInputDigits("12345"))
    }

    @Test
    fun formatInputDigits_sixDigits() {
        assertEquals("12:34:56", formatInputDigits("123456"))
    }

    @Test
    fun parseDigitsToMs_empty() {
        assertEquals(0L, parseDigitsToMs(""))
    }

    @Test
    fun parseDigitsToMs_secondsOnly() {
        assertEquals(45_000L, parseDigitsToMs("45"))
    }

    @Test
    fun parseDigitsToMs_minutesAndSeconds() {
        // 1m 23s = 83s = 83000ms
        assertEquals(83_000L, parseDigitsToMs("123"))
        // 12m 34s = 754s = 754000ms
        assertEquals(754_000L, parseDigitsToMs("1234"))
    }

    @Test
    fun parseDigitsToMs_hoursMinutesAndSeconds() {
        // 1h 23m 45s = 3600 + 1380 + 45 = 5025s = 5025000ms
        assertEquals(5_025_000L, parseDigitsToMs("12345"))
        // 12h 34m 56s = 43200 + 2040 + 56 = 45296s = 45296000ms
        assertEquals(45_296_000L, parseDigitsToMs("123456"))
    }
}

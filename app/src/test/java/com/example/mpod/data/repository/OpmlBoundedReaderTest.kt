package com.example.mpod.data.repository

import com.example.mpod.data.repository.PodcastRepository.Companion.MAX_OPML_SIZE_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class OpmlBoundedReaderTest {

    private fun readBounded(inputStream: InputStream): ByteArray {
        return PodcastRepository.readLimited(inputStream, MAX_OPML_SIZE_BYTES + 1)
    }

    @Test
    fun streamUnderLimitIsFullyRead() {
        val data = ByteArray(1024) { it.toByte() }
        val bytes = readBounded(ByteArrayInputStream(data))
        assertEquals(1024, bytes.size)
        assertTrue(bytes.contentEquals(data))
    }

    @Test
    fun exactlyMaxSizeIsNotRejectedAsOversized() {
        val data = ByteArray(MAX_OPML_SIZE_BYTES) { it.toByte() }
        val bytes = readBounded(ByteArrayInputStream(data))
        assertEquals(MAX_OPML_SIZE_BYTES, bytes.size)
        assertFalse(bytes.size > MAX_OPML_SIZE_BYTES)
    }

    @Test
    fun oneByteOverLimitIsDetected() {
        val data = ByteArray(MAX_OPML_SIZE_BYTES + 1) { it.toByte() }
        val bytes = readBounded(ByteArrayInputStream(data))
        assertTrue(bytes.size > MAX_OPML_SIZE_BYTES)
    }

    @Test
    fun oversizedStreamIsNotReadFullyAfterLimitPlusOne() {
        val totalSize = MAX_OPML_SIZE_BYTES + 1000
        val data = ByteArray(totalSize) { it.toByte() }
        val countingStream = CountingInputStream(ByteArrayInputStream(data))
        val bytes = readBounded(countingStream)
        assertEquals(MAX_OPML_SIZE_BYTES + 1, bytes.size)
        assertEquals(MAX_OPML_SIZE_BYTES + 1, countingStream.bytesRead.get())
    }

    @Test(expected = IOException::class)
    fun readExceptionPropagates() {
        val failingStream = object : InputStream() {
            override fun read(): Int {
                throw IOException("Provider read error")
            }
        }
        readBounded(failingStream)
    }

    @Test
    fun readExceptionPreventsParserInvocation() {
        val failingStream = object : InputStream() {
            override fun read(): Int {
                throw IOException("Network timeout")
            }
        }
        var parserInvoked = false
        try {
            readBounded(failingStream)
            parserInvoked = true
        } catch (_: IOException) {
        }
        assertFalse("Parser should not be invoked when readLimited throws", parserInvoked)
    }

    @Test
    fun emptyStreamIsAccepted() {
        val bytes = readBounded(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0, bytes.size)
    }

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        val bytesRead = AtomicInteger(0)

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) bytesRead.incrementAndGet()
            return b
        }

        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) bytesRead.addAndGet(n)
            return n
        }
    }
}

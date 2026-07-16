package com.example.mpod.data.network

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class LimitedContentRequestBodyTest {

    @Test
    fun `known file over limit is rejected before stream opens`() {
        var opened = false

        assertThrows(OpmlTooLargeException::class.java) {
            LimitedContentRequestBody(null, MAX_OPML_BYTES + 1) {
                opened = true
                ByteArrayInputStream(ByteArray(0))
            }
        }

        assertEquals(false, opened)
    }

    @Test
    fun `file exactly at limit streams successfully`() {
        val bytes = ByteArray(MAX_OPML_BYTES.toInt()) { (it % 251).toByte() }
        val sink = Buffer()
        val body = LimitedContentRequestBody(null, MAX_OPML_BYTES) {
            ByteArrayInputStream(bytes)
        }

        body.writeTo(sink)

        assertEquals(MAX_OPML_BYTES, sink.size)
        assertEquals(bytes.toList(), sink.readByteArray().toList())
    }

    @Test
    fun `unknown file size is enforced while streaming`() {
        val body = LimitedContentRequestBody(null, null) {
            ByteArrayInputStream(ByteArray((MAX_OPML_BYTES + 1).toInt()))
        }

        assertThrows(OpmlTooLargeException::class.java) {
            body.writeTo(Buffer())
        }
    }

    @Test
    fun `provider open failure has a distinct error`() {
        val body = LimitedContentRequestBody(null, null) {
            throw SecurityException("permission revoked")
        }

        val error = assertThrows(OpmlReadException::class.java) {
            body.writeTo(Buffer())
        }

        assertEquals(SecurityException::class.java, error.cause?.javaClass)
    }

    @Test
    fun `provider read failure has a distinct error`() {
        val body = LimitedContentRequestBody(null, null) {
            object : ByteArrayInputStream(byteArrayOf(1)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    throw IOException("provider closed")
                }
            }
        }

        val error = assertThrows(OpmlReadException::class.java) {
            body.writeTo(Buffer())
        }

        assertEquals(IOException::class.java, error.cause?.javaClass)
    }
}

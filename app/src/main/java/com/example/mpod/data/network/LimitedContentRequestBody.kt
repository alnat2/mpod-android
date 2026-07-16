package com.example.mpod.data.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream

internal const val MAX_OPML_BYTES = 5_000_000L

internal class OpmlTooLargeException : IOException("OPML file is too large")

internal class OpmlReadException(cause: Throwable? = null) :
    IOException("Could not read selected OPML file", cause)

internal class LimitedContentRequestBody(
    private val mediaType: MediaType?,
    private val knownLength: Long?,
    private val maxBytes: Long = MAX_OPML_BYTES,
    private val openStream: () -> InputStream
) : RequestBody() {

    init {
        if (knownLength != null && knownLength > maxBytes) {
            throw OpmlTooLargeException()
        }
    }

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = knownLength?.takeIf { it >= 0L } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = try {
            openStream()
        } catch (error: OpmlTooLargeException) {
            throw error
        } catch (error: Exception) {
            throw OpmlReadException(error)
        }

        try {
            input.use {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = it.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) throw OpmlTooLargeException()
                    sink.write(buffer, 0, read)
                }
            }
        } catch (error: OpmlTooLargeException) {
            throw error
        } catch (error: Exception) {
            throw OpmlReadException(error)
        }
    }
}

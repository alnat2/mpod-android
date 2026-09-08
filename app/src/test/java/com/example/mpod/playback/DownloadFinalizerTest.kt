package com.example.mpod.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger

class DownloadFinalizerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var tempFile: File
    private lateinit var targetFile: File

    @Before
    fun setup() {
        tempFile = tempDir.newFile("download.tmp")
        targetFile = File(tempDir.root, "final.mp3")
    }

    @Test
    fun successfulDownload_tempFileRemoved_finalExistsNonEmpty_andDaoInvoked() = runBlocking {
        val data = "audio-data-content".toByteArray()
        val stream = ByteArrayInputStream(data)
        var callbackReceivedFile: File? = null

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = stream,
            onFinalized = { file ->
                callbackReceivedFile = file
            }
        )

        assertTrue(result.success)
        assertNotNull(result.finalFile)
        assertTrue(targetFile.exists())
        assertEquals(data.size.toLong(), targetFile.length())
        assertFalse(tempFile.exists())
        assertEquals(targetFile.absolutePath, callbackReceivedFile?.absolutePath)
    }

    @Test
    fun readException_tempAndFinalAbsent_daoNotInvoked() = runBlocking {
        val failingStream = object : InputStream() {
            override fun read(): Int {
                throw IOException("Network read error")
            }
        }
        var daoInvoked = false

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = failingStream,
            onFinalized = { daoInvoked = true }
        )

        assertFalse(result.success)
        assertFalse(tempFile.exists())
        assertFalse(targetFile.exists())
        assertFalse(daoInvoked)
    }

    @Test
    fun emptyStream_tempAndFinalAbsent_daoNotInvoked() = runBlocking {
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        var daoInvoked = false

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = emptyStream,
            onFinalized = { daoInvoked = true }
        )

        assertFalse(result.success)
        assertFalse(tempFile.exists())
        assertFalse(targetFile.exists())
        assertFalse(daoInvoked)
    }

    @Test
    fun unsuccessfulMove_reportsFailure_tempCleaned_daoNotInvoked() = runBlocking {
        val data = "audio-data".toByteArray()
        val stream = ByteArrayInputStream(data)
        val nonWritableTarget = File("/nonexistent/dir/final.mp3")
        var daoInvoked = false

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = nonWritableTarget,
            dataStream = stream,
            onFinalized = { daoInvoked = true }
        )

        assertFalse(result.success)
        assertFalse(tempFile.exists())
        assertFalse(nonWritableTarget.exists())
        assertFalse(daoInvoked)
    }

    @Test
    fun cancellationException_propagatesAsCancellation() = runBlocking {
        val cancellingStream = object : InputStream() {
            override fun read(): Int = throw CancellationException("cancelled during stream read")
            override fun read(b: ByteArray?, off: Int, len: Int): Int = throw CancellationException("cancelled during bulk read")
        }

        try {
            DownloadFinalizer.downloadAndFinalize(tempFile, targetFile, cancellingStream)
            fail("Expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // CancellationException caught as expected
        }
        assertFalse(tempFile.exists())
        assertFalse(targetFile.exists())
    }

    @Test
    fun cancellationException_duringWrite_propagatesAndCleansTemp() = runBlocking {
        var bytesWritten = 0
        val partialStream = object : InputStream() {
            private val delegate = ByteArrayInputStream("partial-data-here".toByteArray())
            override fun read(b: ByteArray?, off: Int, len: Int): Int {
                if (bytesWritten >= 7) throw CancellationException("cancelled mid-write")
                val n = delegate.read(b, off, len)
                if (n > 0) bytesWritten += n
                return n
            }
            override fun read(): Int {
                if (bytesWritten >= 7) throw CancellationException("cancelled mid-write")
                val byte = delegate.read()
                if (byte != -1) bytesWritten++
                return byte
            }
        }

        try {
            DownloadFinalizer.downloadAndFinalize(tempFile, targetFile, partialStream)
            fail("Expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // CancellationException caught as expected
        }
        assertFalse(tempFile.exists())
        assertFalse(targetFile.exists())
    }

    @Test
    fun ioException_duringWrite_returnsFailureAndCleansTemp() = runBlocking {
        val failingStream = object : InputStream() {
            override fun read(b: ByteArray?, off: Int, len: Int): Int {
                throw IOException("disk full")
            }
            override fun read(): Int {
                throw IOException("disk full")
            }
        }

        val result = DownloadFinalizer.downloadAndFinalize(tempFile, targetFile, failingStream)

        assertFalse(result.success)
        assertFalse(tempFile.exists())
        assertFalse(targetFile.exists())
    }

    @Test
    fun daoExceptionAfterSuccessfulMove_deletesTargetFile() = runBlocking {
        val data = "audio-data-content".toByteArray()
        val stream = ByteArrayInputStream(data)

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = stream,
            onFinalized = {
                // Simulate real database failure after move
                throw SQLException("Database disk image is malformed / SQLite constraint failed")
            }
        )

        assertFalse("Operation should report failure when DAO throws", result.success)
        assertFalse("Target file must be deleted when DAO fails after move", targetFile.exists())
        assertFalse("Temp file must not remain on disk", tempFile.exists())
    }

    @Test
    fun afterFailure_retryCanSucceed() = runBlocking {
        val failingStream = object : InputStream() {
            override fun read(b: ByteArray?, off: Int, len: Int): Int = throw IOException("Temporary failure")
            override fun read(): Int = throw IOException("Temporary failure")
        }

        val result1 = DownloadFinalizer.downloadAndFinalize(tempFile, targetFile, failingStream)
        assertFalse(result1.success)

        val newTempFile = File(tempDir.root, "retry.tmp")
        val newTargetFile = File(tempDir.root, "retry.mp3")
        val successStream = ByteArrayInputStream("successful-data".toByteArray())
        var daoInvoked = false

        val result2 = DownloadFinalizer.downloadAndFinalize(
            tempFile = newTempFile,
            targetFile = newTargetFile,
            dataStream = successStream,
            onFinalized = { daoInvoked = true }
        )
        assertTrue(result2.success)
        assertTrue(newTargetFile.exists())
        assertFalse(newTempFile.exists())
        assertTrue(daoInvoked)
    }

    @Test
    fun largeGeneratedStream_isCopiedInChunksWithoutMemoryAccumulation() = runBlocking {
        val totalBytes = 5 * 1024 * 1024 // 5MB stream
        val bytesReadCount = AtomicInteger(0)

        val largeStream = object : InputStream() {
            private var remaining = totalBytes
            override fun read(): Int {
                if (remaining <= 0) return -1
                remaining--
                bytesReadCount.incrementAndGet()
                return 42
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val toRead = minOf(len, remaining)
                for (i in 0 until toRead) {
                    b[off + i] = 42
                }
                remaining -= toRead
                bytesReadCount.addAndGet(toRead)
                return toRead
            }
        }

        val result = DownloadFinalizer.downloadAndFinalize(tempFile, targetFile, largeStream)

        assertTrue(result.success)
        assertTrue(targetFile.exists())
        assertEquals(totalBytes.toLong(), targetFile.length())
        assertEquals(totalBytes, bytesReadCount.get())
        assertFalse(tempFile.exists())
    }

    @Test
    fun coroutineCancellation_stopsFileWritingAndCleansUp() = runBlocking {
        val streamStarted = CompletableDeferred<Unit>()
        val unblockStream = CompletableDeferred<Unit>()

        val infiniteStream = object : InputStream() {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!streamStarted.isCompleted) {
                    streamStarted.complete(Unit)
                }
                while (!unblockStream.isCompleted) {
                    Thread.sleep(10)
                }
                return -1
            }

            override fun read(): Int {
                return -1
            }
        }

        val downloadJob = launch(Dispatchers.IO) {
            DownloadFinalizer.downloadAndFinalize(tempFile, targetFile, infiniteStream)
        }

        streamStarted.await()
        downloadJob.cancel()
        unblockStream.complete(Unit)
        downloadJob.join()

        assertFalse("Temp file must be cleaned on coroutine cancellation", tempFile.exists())
        assertFalse("Target file must not exist on cancellation", targetFile.exists())
    }

    @Test
    fun unsuccessfulTempCleanup_isObservedAndReportedInResult() = runBlocking {
        val failingFileOps = object : FileOperations {
            override fun exists(file: File): Boolean = true
            override fun delete(file: File): Boolean = false // Delete fails
        }

        val failingStream = object : InputStream() {
            override fun read(): Int = throw IOException("Simulated network drop")
        }

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = failingStream,
            fileOps = failingFileOps
        )

        assertFalse("Download must fail", result.success)
        assertTrue("Cleanup failure must be explicitly reported", result.cleanupFailed)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun moveTempToTarget_cleanupFailure_isPreservedInDownloadAndFinalize() = runBlocking {
        val failingMoveAndDeleteOps = object : FileOperations {
            override fun exists(file: File): Boolean = true
            override fun length(file: File): Long = 100L
            override fun rename(source: File, target: File): Boolean = false // Rename fails
            override fun delete(file: File): Boolean = false // Delete also fails
        }

        val stream = ByteArrayInputStream("valid data".toByteArray())

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = stream,
            fileOps = failingMoveAndDeleteOps
        )

        assertFalse("Download must fail", result.success)
        assertTrue("Cleanup failure from moveTempToTarget must not be lost", result.cleanupFailed)
        assertEquals("Rename failed", result.errorMessage)
    }

    @Test
    fun unsuccessfulTargetCompensationAfterDaoFailure_isObservedAndReportedInResult() = runBlocking {
        val failingDeleteFileOps = object : FileOperations {
            override fun exists(file: File): Boolean = true
            override fun length(file: File): Long = 100L
            override fun rename(source: File, target: File): Boolean = true
            override fun delete(file: File): Boolean = false // Compensation delete fails
        }

        val stream = ByteArrayInputStream("valid data".toByteArray())

        val result = DownloadFinalizer.downloadAndFinalize(
            tempFile = tempFile,
            targetFile = targetFile,
            dataStream = stream,
            fileOps = failingDeleteFileOps,
            onFinalized = {
                throw SQLException("Simulated Room constraint failure")
            }
        )

        assertFalse("Download must fail", result.success)
        assertTrue("Compensation cleanup failure must be reported", result.cleanupFailed)
        assertTrue(result.errorMessage?.contains("failed to delete target file") == true)
    }
}

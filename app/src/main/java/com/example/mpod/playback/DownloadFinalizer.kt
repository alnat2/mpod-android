package com.example.mpod.playback

import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface FileOperations {
    fun exists(file: File): Boolean = file.exists()
    fun length(file: File): Long = file.length()
    fun delete(file: File): Boolean = if (file.exists()) file.delete() else true
    fun rename(source: File, target: File): Boolean = source.renameTo(target)
    fun openOutputStream(file: File): OutputStream = file.outputStream()
}

object DefaultFileOperations : FileOperations

object DownloadFinalizer {

    data class Result(
        val success: Boolean,
        val finalFile: File? = null,
        val cleanupFailed: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * Streams data from [dataStream] into [tempFile] in chunks, moves [tempFile] to [targetFile],
     * and executes [onFinalized].
     *
     * If [onFinalized] throws an exception (e.g. database error), [targetFile] is deleted to prevent
     * orphan files on disk and failure is returned.
     *
     * If temp or target cleanup fails (delete returns false), [Result.cleanupFailed] is true.
     *
     * In case of [CancellationException], both [tempFile] and [targetFile] are cleaned up and
     * the exception is re-thrown.
     */
    fun downloadAndFinalize(
        tempFile: File,
        targetFile: File,
        dataStream: InputStream,
        isCancelled: (() -> Boolean)? = null,
        fileOps: FileOperations = DefaultFileOperations,
        onFinalized: ((File) -> Unit)? = null
    ): Result {
        try {
            fileOps.openOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                while (dataStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled?.invoke() == true) {
                        throw CancellationException("Download cancelled during streaming")
                    }
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                if (totalBytes == 0L) {
                    val clean = cleanupFile(tempFile, fileOps)
                    if (!clean) {
                        android.util.Log.e("DownloadFinalizer", "Failed to clean up empty temp file: ${tempFile.absolutePath}")
                    }
                    return Result(
                        success = false,
                        cleanupFailed = !clean,
                        errorMessage = if (!clean) "Empty stream and failed to clean temp file" else "Empty stream"
                    )
                }
            }
        } catch (e: CancellationException) {
            val tempClean = cleanupFile(tempFile, fileOps)
            val targetClean = cleanupFile(targetFile, fileOps)
            if (!tempClean || !targetClean) {
                android.util.Log.e("DownloadFinalizer", "Cleanup failed during cancellation: tempClean=$tempClean, targetClean=$targetClean")
            }
            throw e
        } catch (e: Exception) {
            val clean = cleanupFile(tempFile, fileOps)
            if (!clean) {
                android.util.Log.e("DownloadFinalizer", "Failed to clean up temp file after write error: ${tempFile.absolutePath}", e)
            }
            return Result(
                success = false,
                cleanupFailed = !clean,
                errorMessage = if (!clean) "Stream write failed (${e.message}) and failed to clean temp file" else "Stream write failed: ${e.message}"
            )
        }

        val moveResult = moveTempToTarget(tempFile, targetFile, fileOps)
        if (!moveResult.success || moveResult.finalFile == null) {
            if (moveResult.cleanupFailed) {
                android.util.Log.e("DownloadFinalizer", "Temp cleanup failed after move failure: ${tempFile.absolutePath}")
            }
            return moveResult
        }

        if (onFinalized != null) {
            try {
                onFinalized(moveResult.finalFile)
            } catch (e: CancellationException) {
                val clean = cleanupFile(targetFile, fileOps)
                if (!clean) {
                    android.util.Log.e("DownloadFinalizer", "Failed to delete target file on cancellation: ${targetFile.absolutePath}")
                }
                throw e
            } catch (e: Exception) {
                val clean = cleanupFile(targetFile, fileOps)
                if (!clean) {
                    android.util.Log.e("DownloadFinalizer", "Failed to delete target file on onFinalized failure: ${targetFile.absolutePath}", e)
                }
                return Result(
                    success = false,
                    cleanupFailed = !clean,
                    errorMessage = if (!clean) "onFinalized failed (${e.message}) and failed to delete target file" else "onFinalized failed: ${e.message}"
                )
            }
        }

        return moveResult
    }

    fun moveTempToTarget(
        tempFile: File,
        targetFile: File,
        fileOps: FileOperations = DefaultFileOperations
    ): Result {
        if (!fileOps.exists(tempFile) || fileOps.length(tempFile) == 0L) {
            val clean = cleanupFile(tempFile, fileOps)
            return Result(success = false, cleanupFailed = !clean, errorMessage = "Temp file missing or empty")
        }

        val renamed = fileOps.rename(tempFile, targetFile)
        if (!renamed || !fileOps.exists(targetFile) || fileOps.length(targetFile) == 0L) {
            val clean = cleanupFile(tempFile, fileOps)
            return Result(success = false, cleanupFailed = !clean, errorMessage = "Rename failed")
        }

        return Result(success = true, finalFile = targetFile)
    }

    fun cleanupFile(file: File, fileOps: FileOperations = DefaultFileOperations): Boolean {
        return try {
            if (fileOps.exists(file)) {
                fileOps.delete(file)
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun cleanupTempFile(tempFile: File, fileOps: FileOperations = DefaultFileOperations): Boolean =
        cleanupFile(tempFile, fileOps)

    fun cleanupTargetFile(targetFile: File, fileOps: FileOperations = DefaultFileOperations): Boolean =
        cleanupFile(targetFile, fileOps)
}

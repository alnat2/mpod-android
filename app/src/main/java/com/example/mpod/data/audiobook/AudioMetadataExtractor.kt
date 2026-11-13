package com.example.mpod.data.audiobook

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedAudioMetadata(
    val durationSeconds: Long = 0L,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val embeddedPicture: ByteArray? = null
)

interface AudioMetadataExtractor {
    fun extract(uri: Uri): ExtractedAudioMetadata?
}

@Singleton
class DefaultAudioMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioMetadataExtractor {

    override fun extract(uri: Uri): ExtractedAudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))?.takeIf { it.isNotBlank() }
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            val picture = retriever.embeddedPicture

            ExtractedAudioMetadata(
                durationSeconds = durationMs / 1000L,
                title = title,
                artist = artist,
                album = album,
                embeddedPicture = picture
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors on older platforms
            }
        }
    }
}

package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class PickedVideoMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val thumbnail: Bitmap?,
)

object StudyVideoStorage {
    private const val DIR_NAME = "study_videos"
    private const val BUFFER_SIZE = 256 * 1024 // 256 KB buffered stream for fast copy

    private fun videosDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun resolveVideoFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val safeName = File(fileName).name
        val file = File(videosDir(context), safeName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun deleteVideoFile(context: Context, fileName: String) {
        if (fileName.isBlank()) return
        val safeName = File(fileName).name
        val file = File(videosDir(context), safeName)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun inspectVideo(context: Context, uri: Uri): PickedVideoMetadata = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        var displayName = ""
        var sizeBytes = 0L

        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx).orEmpty()
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                }
            }
        }

        val cleanTitle = displayName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .trim()
            .ifBlank { "Study Video Lesson" }
            .take(100)

        var durationMs = 0L
        var thumbnail: Bitmap? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceIn(0L, 14_400_000L)
                ?: 0L
            thumbnail = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        PickedVideoMetadata(
            displayName = cleanTitle,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            thumbnail = thumbnail,
        )
    }

    suspend fun importVideoFast(
        context: Context,
        uri: Uri,
        targetFileName: String,
        totalBytesHint: Long,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val safeName = File(targetFileName).name
        val destFile = File(videosDir(appContext), safeName)
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L

        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open selected video.")

        inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (totalBytesHint > 0L) {
                        onProgress((copied.toFloat() / totalBytesHint.toFloat()).coerceIn(0.05f, 0.92f))
                    } else {
                        onProgress(0.5f)
                    }
                }
                output.flush()
            }
        }
        onProgress(0.95f)
        destFile
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "Video"
        val totalSec = durationMs / 1000L
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = (bytes / 1024.0).coerceAtLeast(1.0)
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}

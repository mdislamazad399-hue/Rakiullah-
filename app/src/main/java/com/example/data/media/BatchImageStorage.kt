package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class PickedBatchImage(
    val previewBitmap: ImageBitmap,
    val base64Data: String,
    val byteSize: Int,
)

object BatchImageStorage {
    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"
    private const val MAX_DIMENSION_PX = 640
    private const val MAX_BASE64_LENGTH = 140_000

    suspend fun processPickedImage(context: Context, uri: Uri): PickedBatchImage = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: throw IllegalStateException("Could not open selected image.")

        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION_PX, MAX_DIMENSION_PX)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Could not decode selected image.")

        val orientation = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            try {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL

        val oriented = rotateIfNeeded(decoded, orientation)
        val scaled = scaleDownIfNeeded(oriented, MAX_DIMENSION_PX)

        val baos = ByteArrayOutputStream()
        var quality = 80
        var encodedDataUri = ""
        var rawBytes = ByteArray(0)
        while (quality >= 40) {
            baos.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            rawBytes = baos.toByteArray()
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            val candidate = "$DATA_URI_PREFIX$b64"
            if (candidate.length <= MAX_BASE64_LENGTH) {
                encodedDataUri = candidate
                break
            }
            quality -= 10
        }

        if (encodedDataUri.isEmpty()) {
            throw IllegalStateException("Selected image is too large. Please choose a smaller photo.")
        }

        PickedBatchImage(
            previewBitmap = scaled.asImageBitmap(),
            base64Data = encodedDataUri,
            byteSize = rawBytes.size,
        )
    }

    fun decodeToImageBitmap(coverImageData: String): ImageBitmap? {
        if (coverImageData.isBlank()) return null
        return try {
            val rawB64 = coverImageData.substringAfter("base64,", coverImageData)
            val bytes = Base64.decode(rawB64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}

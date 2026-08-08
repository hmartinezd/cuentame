package com.miara.cuentame.core.common.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

object SafeImageDecoder {

    /**
     * Decodes an image safely, limiting dimensions to [maxDimension].
     * Handles EXIF orientation.
     */
    suspend fun decode(streamProvider: suspend () -> InputStream, maxDimension: Int): Bitmap? {
        // Check dimensions first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        streamProvider().use { BitmapFactory.decodeStream(it, null, options) }

        var width = options.outWidth
        var height = options.outHeight

        if (width <= 0 || height <= 0) return null

        // Calculate inSampleSize
        var inSampleSize = 1
        while (width / inSampleSize > maxDimension || height / inSampleSize > maxDimension) {
            inSampleSize *= 2
        }

        val decodedOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val bitmap = streamProvider().use { BitmapFactory.decodeStream(it, null, decodedOptions) } ?: return null

        // Handle Orientation
        val exif = try {
            streamProvider().use { ExifInterface(it) }
        } catch (e: Exception) {
            null
        }

        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        return if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }
}

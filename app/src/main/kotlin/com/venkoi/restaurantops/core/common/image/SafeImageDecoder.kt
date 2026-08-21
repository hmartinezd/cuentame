package com.venkoi.restaurantops.core.common.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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

        val matrix = Matrix()
        var needsTransform = true
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> needsTransform = false
        }

        val oriented = if (needsTransform) {
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed != bitmap) {
                bitmap.recycle()
            }
            transformed
        } else {
            bitmap
        }

        // Avoid scanning every full-resolution pixel. Alpha-capable input can be
        // composited directly; callers invoke this decoder on a worker dispatcher.
        if (!oriented.hasAlpha()) return oriented
        val opaque = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(oriented, 0f, 0f, null)
        }
        opaque.setHasAlpha(false)
        oriented.recycle()
        return opaque
    }

}

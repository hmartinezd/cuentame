package com.miara.cuentame.core.ocr.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/** Final bitmap invariant immediately before an image is handed to ML Kit. */
internal object OcrInputBitmapPreparer {
    fun prepare(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source

        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { opaque ->
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(source, 0f, 0f, null)
            }
            opaque.setHasAlpha(false)
        }
    }
}

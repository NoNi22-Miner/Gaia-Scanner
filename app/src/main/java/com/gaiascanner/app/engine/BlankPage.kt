package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.gaiascanner.app.core.ImageFormat
import java.io.File

/** Writes a plain white JPEG page. */
object BlankPage {
    fun write(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            PdfToImages.writeImage(bitmap, file, ImageFormat.JPG, jpegQuality = 90)
        } finally {
            bitmap.recycle()
        }
    }
}

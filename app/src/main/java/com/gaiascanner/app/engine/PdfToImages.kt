package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.PageMath
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Renders every page of a PDF to a JPG or PNG file, one page at a time. */
class PdfToImages(
    private val renderMode: Int = PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
    private val maxPixels: Long = ConvertDefaults.MAX_PAGE_PIXELS,
) {
    data class RenderedPage(
        val file: File,
        val width: Int,
        val height: Int,
        /** DPI actually used. Lower than asked when [capped]. */
        val dpi: Float,
        val capped: Boolean,
    )

    suspend fun convert(
        pdf: File,
        outputDir: File,
        baseName: String,
        format: ImageFormat,
        dpi: Int,
        jpegQuality: Int,
        checkCancelled: suspend () -> Unit = {},
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<RenderedPage> {
        outputDir.mkdirs()
        val renderer = PdfFiles.open(pdf)
        try {
            val total = renderer.pageCount
            if (total <= 0) throw ConvertException(FailureReason.DAMAGED)
            val pages = ArrayList<RenderedPage>(total)
            for (index in 0 until total) {
                currentCoroutineContext().ensureActive()
                checkCancelled()
                val page = renderer.openPage(index)
                try {
                    val size = PageMath.renderSize(page.width, page.height, dpi, maxPixels)
                    val bitmap = createBitmap(size.width, size.height)
                    try {
                        // JPG has no transparency. Paint the page white first.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, renderMode)
                        val file = File(outputDir, FileNames.pageImageName(baseName, index, total, format.extension))
                        writeImage(bitmap, file, format, jpegQuality)
                        pages += RenderedPage(file, size.width, size.height, size.dpi, size.capped)
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
                onProgress(index + 1, total)
            }
            return pages
        } finally {
            renderer.close()
        }
    }

    private fun createBitmap(width: Int, height: Int): Bitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        throw ConvertException(FailureReason.TOO_LARGE, e)
    }

    companion object {
        fun writeImage(bitmap: Bitmap, file: File, format: ImageFormat, jpegQuality: Int) {
            val compressFormat = when (format) {
                ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            }
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(compressFormat, jpegQuality.coerceIn(0, 100), out)) {
                    throw IOException("Bitmap.compress returned false for ${file.name}")
                }
            }
        }
    }
}

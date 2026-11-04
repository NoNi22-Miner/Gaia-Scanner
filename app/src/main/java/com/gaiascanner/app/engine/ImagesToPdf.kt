package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.JpegPdfWriter
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageMath
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.PageEdit
import com.gaiascanner.app.core.Quad
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Turns images into a PDF, one image per page, with [JpegPdfWriter].
 * Each image is turned upright, scaled to at most [imageDpi] on the page,
 * encoded as JPEG and written before the next one is decoded.
 */
class ImagesToPdf(
    private val imageDpi: Int = ConvertDefaults.PDF_IMAGE_DPI,
    private val jpegQuality: Int = ConvertDefaults.JPEG_QUALITY,
) {

    data class Input(val file: File, val edit: PageEdit = PageEdit()) {
        constructor(file: File, userRotation: Int, crop: Quad? = null) : this(file, PageEdit(crop = crop, rotation = userRotation))
    }

    suspend fun convert(
        inputs: List<Input>,
        output: File,
        pageSize: PageSizeOption,
        margin: MarginOption,
        checkCancelled: suspend () -> Unit = {},
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        require(inputs.isNotEmpty()) { "No images" }
        output.parentFile?.mkdirs()
        try {
            FileOutputStream(output).buffered(BUFFER).use { stream ->
                val writer = JpegPdfWriter(stream)
                inputs.forEachIndexed { index, input ->
                    currentCoroutineContext().ensureActive()
                    checkCancelled()
                    addPage(writer, input, pageSize, margin)
                    onProgress(index + 1, inputs.size)
                }
                writer.finish()
            }
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
    }

    private fun addPage(writer: JpegPdfWriter, input: Input, pageSize: PageSizeOption, margin: MarginOption) {
        val info = ImageDecoding.readInfo(input.file) ?: throw ConvertException(
            if (input.file.exists()) FailureReason.UNSUPPORTED_FORMAT else FailureReason.MISSING_FILE,
        )
        val (finalW, finalH) = PageRenderer.finalSize(info, input.edit)
        val page = PageMath.pageSizeFor(finalW, finalH, pageSize)
        val box = PageMath.fitInside(PageMath.contentBox(page, margin), finalW, finalH)
        val (targetW, targetH) = PageMath.targetPixels(box.width, box.height, imageDpi, finalW, finalH)

        val bitmap = onWhite(PageRenderer.render(input.file, info, input.edit, targetW, targetH))
        val pixelW = bitmap.width
        val pixelH = bitmap.height
        val jpeg = try {
            ByteArrayOutputStream(bitmap.width * bitmap.height / 4).also { bytes ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(0, 100), bytes)) {
                    throw ConvertException(FailureReason.UNKNOWN)
                }
            }.toByteArray()
        } catch (e: OutOfMemoryError) {
            throw ConvertException(FailureReason.TOO_LARGE, e)
        } finally {
            bitmap.recycle()
        }
        // Android's JPEG encoder writes 3-component (colour) JPEGs.
        writer.addPage(page.width, page.height, jpeg, pixelW, pixelH, box)
    }

    /** JPEG has no transparency, so transparent pixels (PNG, WebP) become white, not black. */
    private fun onWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = try {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            bitmap.recycle()
            throw ConvertException(FailureReason.TOO_LARGE, e)
        }
        flat.eraseColor(Color.WHITE)
        Canvas(flat).drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return flat
    }

    private companion object {
        const val BUFFER = 256 * 1024
    }
}

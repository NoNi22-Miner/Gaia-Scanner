package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import com.gaiascanner.app.core.Box
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.JpegPdfWriter
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageEdit
import com.gaiascanner.app.core.PageMath
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.WatermarkLayout
import com.gaiascanner.app.core.WatermarkStyle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * PDF tools (phase 3): reduce file size and text watermark. Both write a new
 * PDF with our JpegPdfWriter; every page becomes a JPEG image, so text in the
 * copy can no longer be selected or searched. The original is not changed.
 */
object PdfTools {

    /** One page of an image document, as the user edited it. */
    data class ImagePage(val file: File, val edit: PageEdit)

    /**
     * Renders every page of [pdf] at [dpi], optionally draws [watermark], and
     * writes [output] with the same page sizes (in points).
     */
    suspend fun rewritePdf(
        pdf: File,
        output: File,
        dpi: Int,
        jpegQuality: Int,
        watermark: WatermarkStyle? = null,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        output.parentFile?.mkdirs()
        try {
            val renderer = PdfFiles.open(pdf)
            try {
                val total = renderer.pageCount
                if (total <= 0) throw ConvertException(FailureReason.DAMAGED)
                FileOutputStream(output).buffered(BUFFER).use { stream ->
                    val writer = JpegPdfWriter(stream)
                    for (i in 0 until total) {
                        currentCoroutineContext().ensureActive()
                        val page = renderer.openPage(i)
                        try {
                            val size = PageMath.renderSize(page.width, page.height, dpi, ConvertDefaults.MAX_PAGE_PIXELS)
                            val bitmap = newBitmap(size.width, size.height)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                watermark?.let { draw(Canvas(bitmap), bitmap.width, bitmap.height, it) }
                                val jpeg = encode(bitmap, jpegQuality)
                                writer.addPage(
                                    page.width, page.height, jpeg, bitmap.width, bitmap.height,
                                    Box(0f, 0f, page.width.toFloat(), page.height.toFloat()),
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }
                        onProgress(i + 1, total)
                    }
                    writer.finish()
                }
            } finally {
                renderer.close()
            }
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
    }

    /** Image document to a PDF with a watermark on each image. Page size and margins as in Make PDF. */
    suspend fun imagesWithWatermark(
        pages: List<ImagePage>,
        output: File,
        pageSize: PageSizeOption,
        margin: MarginOption,
        watermark: WatermarkStyle,
        dpi: Int = ConvertDefaults.PDF_IMAGE_DPI,
        jpegQuality: Int = ConvertDefaults.JPEG_QUALITY,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        require(pages.isNotEmpty()) { "No pages" }
        output.parentFile?.mkdirs()
        try {
            FileOutputStream(output).buffered(BUFFER).use { stream ->
                val writer = JpegPdfWriter(stream)
                pages.forEachIndexed { index, input ->
                    currentCoroutineContext().ensureActive()
                    val info = ImageDecoding.readInfo(input.file) ?: throw ConvertException(
                        if (input.file.exists()) FailureReason.UNSUPPORTED_FORMAT else FailureReason.MISSING_FILE,
                    )
                    val (finalW, finalH) = PageRenderer.finalSize(info, input.edit)
                    val page = PageMath.pageSizeFor(finalW, finalH, pageSize)
                    val box = PageMath.fitInside(PageMath.contentBox(page, margin), finalW, finalH)
                    val (targetW, targetH) = PageMath.targetPixels(box.width, box.height, dpi, finalW, finalH)
                    val bitmap = onWhite(PageRenderer.render(input.file, info, input.edit, targetW, targetH))
                    try {
                        draw(Canvas(bitmap), bitmap.width, bitmap.height, watermark)
                        writer.addPage(page.width, page.height, encode(bitmap, jpegQuality), bitmap.width, bitmap.height, box)
                    } finally {
                        bitmap.recycle()
                    }
                    onProgress(index + 1, pages.size)
                }
                writer.finish()
            }
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
    }

    /** Small preview: the first page with the watermark, longest side about [maxSide] px. */
    fun previewPdf(pdf: File, maxSide: Int, watermark: WatermarkStyle?): Bitmap? = try {
        PdfFiles.renderFirstPage(pdf, maxSide)?.let { b ->
            val copy = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
            watermark?.let { draw(Canvas(copy), copy.width, copy.height, it) }
            copy
        }
    } catch (e: Exception) {
        null
    }

    fun previewImage(page: ImagePage, maxSide: Int, watermark: WatermarkStyle?): Bitmap? =
        PageRenderer.thumbnail(page.file, page.edit, maxSide)?.let { b ->
            val copy = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
            watermark?.let { draw(Canvas(copy), copy.width, copy.height, it) }
            copy
        }

    /** Draws the watermark over a [width] x [height] page. Text height is a share of the width. */
    fun draw(canvas: Canvas, width: Int, height: Int, style: WatermarkStyle) {
        val s = style.normalized()
        if (s.text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.argb
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = max(1f, width * s.sizePercent / 100f)
        }
        // Keep a single line inside the page width.
        val textWidth = paint.measureText(s.text)
        if (!s.tiled && textWidth > width * 0.95f) paint.textSize *= width * 0.95f / textWidth
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val baselineShift = -(metrics.ascent + metrics.descent) / 2f
        val cx = width / 2f
        val cy = height / 2f
        canvas.save()
        // Canvas turns clockwise for positive degrees; the style's angle is counter-clockwise.
        canvas.rotate(-s.angle.toFloat(), cx, cy)
        if (s.tiled) {
            for ((dx, dy) in WatermarkLayout.tiles(width.toFloat(), height.toFloat(), paint.measureText(s.text), textHeight)) {
                canvas.drawText(s.text, cx + dx, cy + dy + baselineShift, paint)
            }
        } else {
            canvas.drawText(s.text, cx, cy + baselineShift, paint)
        }
        canvas.restore()
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray = try {
        ByteArrayOutputStream(max(1024, bitmap.width * bitmap.height / 6)).also { bytes ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), bytes)) {
                throw ConvertException(FailureReason.UNKNOWN)
            }
        }.toByteArray()
    } catch (e: OutOfMemoryError) {
        throw ConvertException(FailureReason.TOO_LARGE, e)
    }

    private fun newBitmap(width: Int, height: Int): Bitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        throw ConvertException(FailureReason.TOO_LARGE, e)
    }

    /** JPEG has no transparency; draw on white, and return a mutable bitmap for the watermark. */
    private fun onWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha() && bitmap.isMutable) return bitmap
        val flat = newBitmap(bitmap.width, bitmap.height)
        flat.eraseColor(Color.WHITE)
        Canvas(flat).drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return flat
    }

    private const val BUFFER = 256 * 1024
}

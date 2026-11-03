package com.gaiascanner.app.engine

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageMath
import com.gaiascanner.app.core.PageSizeOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream

/** Draws images onto PDF pages with Android's built-in PdfDocument. */
class ImagesToPdf(private val imageDpi: Int = ConvertDefaults.PDF_IMAGE_DPI) {

    data class Input(val file: File, val userRotation: Int = 0)

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
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val document = PdfDocument()
        try {
            inputs.forEachIndexed { index, input ->
                currentCoroutineContext().ensureActive()
                checkCancelled()
                val info = ImageDecoding.readInfo(input.file) ?: throw ConvertException(
                    if (input.file.exists()) FailureReason.UNSUPPORTED_FORMAT else FailureReason.MISSING_FILE,
                )
                val (uprightW, uprightH) = info.uprightSize(input.userRotation)
                val page = PageMath.pageSizeFor(uprightW, uprightH, pageSize)
                val box = PageMath.fitInside(PageMath.contentBox(page, margin), uprightW, uprightH)
                val (targetW, targetH) = PageMath.targetPixels(box.width, box.height, imageDpi, uprightW, uprightH)
                val bitmap = ImageDecoding.decodeUpright(input.file, info, input.userRotation, targetW, targetH)

                val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create())
                pdfPage.canvas.drawBitmap(bitmap, null, RectF(box.left, box.top, box.right, box.bottom), paint)
                document.finishPage(pdfPage)
                // Not recycled on purpose: whether PdfDocument still reads the pixels
                // until writeTo() is not documented. The garbage collector frees it.
                onProgress(index + 1, inputs.size)
            }
            try {
                FileOutputStream(output).use { document.writeTo(it) }
            } catch (e: OutOfMemoryError) {
                throw ConvertException(FailureReason.TOO_LARGE, e)
            }
        } finally {
            document.close()
        }
    }
}

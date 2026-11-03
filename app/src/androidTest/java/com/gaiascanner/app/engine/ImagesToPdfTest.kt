package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.Measure
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImagesToPdfTest {

    @Test
    fun pagesKeepOrderAndColors() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("order")
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val inputs = colors.mapIndexed { i, c ->
            File(dir, "$i.jpg").also { TestFiles.solidImage(it, 400, 300, c) }.let { ImagesToPdf.Input(it) }
        }
        val out = File(dir, "out.pdf")
        ImagesToPdf().convert(inputs, out, PageSizeOption.FIT, MarginOption.NONE)

        val renderer = PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY))
        assertEquals(3, renderer.pageCount)
        colors.forEachIndexed { i, expected ->
            val page = renderer.openPage(i)
            assertEquals(842, page.width)
            assertEquals(632, page.height)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val px = bmp.getPixel(page.width / 2, page.height / 2)
            assertTrue(
                "page ${i + 1} center ${Integer.toHexString(px)}",
                dominant(px) == dominant(expected),
            )
            page.close()
        }
        renderer.close()
        dir.deleteRecursively()
    }

    @Test
    fun userRotationTurnsLandscapeIntoPortrait() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("rot")
        val img = File(dir, "wide.jpg").also { TestFiles.solidImage(it, 400, 200, Color.GRAY) }
        val out = File(dir, "out.pdf")
        ImagesToPdf().convert(listOf(ImagesToPdf.Input(img, 90)), out, PageSizeOption.FIT, MarginOption.NONE)
        val (w, h) = firstPageSize(out)
        assertTrue("page $w x $h should be portrait", h > w)
        dir.deleteRecursively()
    }

    @Test
    fun exifOrientationIsApplied() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("exif")
        val img = File(dir, "tagged.jpg").also { TestFiles.solidImage(it, 400, 200, Color.GRAY) }
        ExifInterface(img).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val info = ImageDecoding.readInfo(img)!!
        assertEquals(200 to 400, info.uprightSize())
        val out = File(dir, "out.pdf")
        ImagesToPdf().convert(listOf(ImagesToPdf.Input(img)), out, PageSizeOption.A4, MarginOption.SMALL)
        assertEquals(595 to 842, firstPageSize(out))
        dir.deleteRecursively()
    }

    /** [test] item: how big PdfDocument makes a PDF from photos. Synthetic photos, so a rough guide. */
    @Test
    fun measurePdfSizeFromPhotos() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("size")
        val photos = (0 until 10).map { File(dir, "photo$it.jpg").also { f -> TestFiles.photoLikeJpeg(f, 4000, 3000, it) } }
        val inputBytes = photos.sumOf { it.length() }
        for (dpi in listOf(300, 200)) {
            val out = File(dir, "out$dpi.pdf")
            val start = System.currentTimeMillis()
            ImagesToPdf(imageDpi = dpi).convert(photos.map { ImagesToPdf.Input(it) }, out, PageSizeOption.FIT, MarginOption.NONE)
            val ms = System.currentTimeMillis() - start
            Measure.log(
                "photos10_to_pdf_dpi$dpi",
                "input_bytes=$inputBytes pdf_bytes=${out.length()} ratio=${"%.2f".format(out.length().toDouble() / inputBytes)} ms=$ms",
            )
            val renderer = PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY))
            assertEquals(10, renderer.pageCount)
            renderer.close()
        }
        dir.deleteRecursively()
    }

    /** [test] item: does PdfDocument keep every page in memory until writeTo()? */
    @Test
    fun measureMemoryWithManyPages() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("mem")
        val photo = File(dir, "photo.jpg").also { TestFiles.photoLikeJpeg(it, 2000, 1500, 7) }
        val inputs = List(100) { ImagesToPdf.Input(photo) }
        val out = File(dir, "out.pdf")
        System.gc()
        val before = Debug.getNativeHeapAllocatedSize()
        val samples = mutableMapOf<Int, Long>()
        ImagesToPdf().convert(inputs, out, PageSizeOption.FIT, MarginOption.NONE) { done, _ ->
            if (done == 1 || done == 50 || done == 100) samples[done] = Debug.getNativeHeapAllocatedSize()
        }
        System.gc()
        val after = Debug.getNativeHeapAllocatedSize()
        val mb = { v: Long -> v / (1024 * 1024) }
        Measure.log(
            "pdf100_native_heap_mb",
            "before=${mb(before)} p1=${mb(samples[1] ?: 0)} p50=${mb(samples[50] ?: 0)} p100=${mb(samples[100] ?: 0)} after_write=${mb(after)} pdf_bytes=${out.length()}",
        )
        dir.deleteRecursively()
    }

    private fun firstPageSize(pdf: File): Pair<Int, Int> {
        val renderer = PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))
        val page = renderer.openPage(0)
        val size = page.width to page.height
        page.close()
        renderer.close()
        return size
    }

    private fun dominant(color: Int): Char {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return when {
            r > g && r > b -> 'r'
            g > r && g > b -> 'g'
            else -> 'b'
        }
    }
}

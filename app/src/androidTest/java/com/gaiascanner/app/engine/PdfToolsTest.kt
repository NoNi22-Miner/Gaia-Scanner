package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.Measure
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageEdit
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.ReducePreset
import com.gaiascanner.app.core.WatermarkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfToolsTest {

    private fun pageSizes(pdf: File): List<Pair<Int, Int>> {
        val fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd).use { r ->
            return (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
        }
    }

    /** Pixels that are near black (the red test square is excluded by its red channel). */
    private fun darkPixels(pdf: File): Int {
        val fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd).use { r ->
            r.openPage(0).use { page ->
                val b = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val px = IntArray(b.width * b.height)
                b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
                b.recycle()
                return px.count { Color.red(it) < 80 && Color.green(it) < 80 && Color.blue(it) < 80 }
            }
        }
    }

    @Test
    fun reduceKeepsPageCountAndSizes() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("reduce")
        val pdf = File(dir, "in.pdf").also { TestFiles.makePdf(it, listOf(595 to 842, 842 to 595, 612 to 792)) }
        for (preset in ReducePreset.entries) {
            val out = File(dir, "out-${preset.name}.pdf")
            val t0 = System.nanoTime()
            PdfTools.rewritePdf(pdf, out, preset.dpi, preset.jpegQuality)
            Measure.log("reduce_${preset.name}_ms", (System.nanoTime() - t0) / 1_000_000)
            Measure.log("reduce_${preset.name}_bytes", "${pdf.length()}->${out.length()}")
            assertEquals(listOf(595 to 842, 842 to 595, 612 to 792), pageSizes(out))
            assertEquals(0, darkPixels(out))
        }
        dir.deleteRecursively()
    }

    @Test
    fun watermarkDrawsOnEveryPdfPage() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("wm")
        val pdf = File(dir, "in.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(2)) }
        val style = WatermarkStyle(text = "OWNER", sizePercent = 12, color = 0x000000, opacity = 100, angle = 45, tiled = false)
        val out = File(dir, "wm.pdf")
        PdfTools.rewritePdf(pdf, out, dpi = 150, jpegQuality = 90, watermark = style)
        assertEquals(2, pageSizes(out).size)
        val dark = darkPixels(out)
        Measure.log("watermark_dark_pixels", dark)
        assertTrue("watermark text is visible ($dark dark pixels)", dark > 500)
        dir.deleteRecursively()
    }

    @Test
    fun watermarkOnImageDocumentMakesAPdf() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("wm-img")
        val pages = listOf(800 to 600, 600 to 800).mapIndexed { i, (w, h) ->
            val f = File(dir, "p$i.jpg").also { TestFiles.solidImage(it, w, h, Color.WHITE) }
            PdfTools.ImagePage(f, PageEdit())
        }
        val out = File(dir, "wm.pdf")
        val style = WatermarkStyle(text = "OWNER", color = 0x000000, opacity = 100, tiled = true)
        PdfTools.imagesWithWatermark(pages, out, PageSizeOption.FIT, MarginOption.NONE, style)
        assertEquals(2, pageSizes(out).size)
        assertTrue(darkPixels(out) > 500)
        dir.deleteRecursively()
    }
}

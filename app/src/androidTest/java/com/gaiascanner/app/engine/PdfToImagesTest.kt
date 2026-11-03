package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.Measure
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.ImageFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PdfToImagesTest {

    private val password = "secret123" // Same as scripts/make-test-fixtures.py.

    @Test
    fun pageSizesFollowDpi() = runBlocking {
        val dir = TestFiles.tempDir("dpi")
        val pdf = File(dir, "a4.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(2)) }

        val at72 = PdfToImages().convert(pdf, File(dir, "72"), "a4", ImageFormat.JPG, 72, 90)
        assertEquals(2, at72.size)
        assertEquals(595, at72[0].width)
        assertEquals(842, at72[0].height)
        assertEquals("a4_p001.jpg", at72[0].file.name)

        val at150 = PdfToImages().convert(pdf, File(dir, "150"), "a4", ImageFormat.PNG, 150, 90)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(at150[1].file.path, bounds)
        assertEquals(1240, bounds.outWidth)
        assertEquals(1754, bounds.outHeight)
        dir.deleteRecursively()
    }

    @Test
    fun blankAreaIsWhiteInJpg() = runBlocking {
        val dir = TestFiles.tempDir("white")
        val pdf = File(dir, "p.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(1)) }
        val page = PdfToImages().convert(pdf, dir, "p", ImageFormat.JPG, 72, 95).single()
        val bmp = BitmapFactory.decodeFile(page.file.path)
        val blank = bmp.getPixel(500, 700)
        assertTrue("blank pixel ${Integer.toHexString(blank)}", Color.red(blank) > 245 && Color.green(blank) > 245 && Color.blue(blank) > 245)
        val square = bmp.getPixel(150, 150)
        assertTrue("square pixel ${Integer.toHexString(square)}", Color.red(square) > 150 && Color.green(square) < 90)
        dir.deleteRecursively()
    }

    /** [test] item: what PdfRenderer leaves in blank areas if the bitmap is not painted first. */
    @Test
    fun measureBackgroundWithoutWhiteFill() {
        val dir = TestFiles.tempDir("bg")
        val pdf = File(dir, "p.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(1)) }
        val renderer = PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))
        val page = renderer.openPage(0)
        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888) // starts transparent
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        val px = bmp.getPixel(500, 700)
        Measure.log("render_unfilled_blank_pixel_argb", Integer.toHexString(px))
        Measure.log("render_unfilled_blank_is_transparent", Color.alpha(px) == 0)
        page.close()
        renderer.close()
        dir.deleteRecursively()
    }

    /** [test] item: does RENDER_MODE_FOR_PRINT differ from RENDER_MODE_FOR_DISPLAY? */
    @Test
    fun measureRenderModes() = runBlocking {
        val dir = TestFiles.tempDir("modes")
        val pdf = File(dir, "p.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(1)) }
        val display = PdfToImages(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY).convert(pdf, File(dir, "d"), "p", ImageFormat.PNG, 150, 90).single()
        val print = PdfToImages(PdfRenderer.Page.RENDER_MODE_FOR_PRINT).convert(pdf, File(dir, "p"), "p", ImageFormat.PNG, 150, 90).single()
        val a = BitmapFactory.decodeFile(display.file.path)
        val b = BitmapFactory.decodeFile(print.file.path)
        var diff = 0
        for (y in 0 until a.height step 4) for (x in 0 until a.width step 4) if (a.getPixel(x, y) != b.getPixel(x, y)) diff++
        Measure.log("render_mode_print_vs_display_differing_samples", diff)
        dir.deleteRecursively()
    }

    @Test
    fun hugePageIsCappedAt16Megapixels() = runBlocking {
        val dir = TestFiles.tempDir("huge")
        val pdf = File(dir, "poster.pdf").also { TestFiles.makePdf(it, listOf(2000 to 2000)) }
        val start = System.currentTimeMillis()
        val page = PdfToImages().convert(pdf, dir, "poster", ImageFormat.JPG, 600, 90).single()
        assertTrue(page.capped)
        assertTrue(page.width.toLong() * page.height <= ConvertDefaults.MAX_PAGE_PIXELS)
        Measure.log("huge_page_px", "${page.width}x${page.height} dpi=${page.dpi} ms=${System.currentTimeMillis() - start}")
        dir.deleteRecursively()
    }

    @Test
    fun passwordPdfIsReported() {
        val dir = TestFiles.tempDir("pw")
        val pdf = TestFiles.asset("encrypted.pdf", dir)
        try {
            PdfFiles.open(pdf).close()
            Measure.log("encrypted_pdf_opened_without_password", true)
            fail("Expected a password error")
        } catch (e: ConvertException) {
            Measure.log("encrypted_pdf_reason_without_password", e.reason)
            assertEquals(FailureReason.PASSWORD, e.reason)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun unlockWithPasswordOnApi35() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        val dir = TestFiles.tempDir("unlock")
        val pdf = TestFiles.asset("encrypted.pdf", dir)
        val out = File(dir, "unlocked.pdf")
        PdfFiles.writeUnlockedCopy(pdf, password, out)
        assertEquals(1, PdfFiles.pageCount(out))
        Measure.log("unlock_with_password", "ok size=${out.length()}")
        try {
            PdfFiles.writeUnlockedCopy(pdf, "wrong", File(dir, "x.pdf"))
            fail("Expected wrong password")
        } catch (e: ConvertException) {
            assertEquals(FailureReason.WRONG_PASSWORD, e.reason)
        }
        dir.deleteRecursively()
    }

    @Test
    fun damagedEmptyAndMissingFiles() {
        val dir = TestFiles.tempDir("bad")
        val garbage = File(dir, "garbage.pdf").apply { writeBytes(Random(1).nextBytes(4096)) }
        val empty = File(dir, "empty.pdf").apply { writeBytes(ByteArray(0)) }
        val missing = File(dir, "missing.pdf")
        assertEquals(FailureReason.DAMAGED, reasonOf { PdfFiles.pageCount(garbage) })
        assertEquals(FailureReason.DAMAGED, reasonOf { PdfFiles.pageCount(empty) })
        assertEquals(FailureReason.MISSING_FILE, reasonOf { PdfFiles.pageCount(missing) })
        dir.deleteRecursively()
    }

    @Test
    fun hundredPagesAsJpgAt150AndPngAt300() = runBlocking {
        val dir = TestFiles.tempDir("hundred")
        val pdf = File(dir, "big.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(100)) }

        var start = System.currentTimeMillis()
        val jpg = PdfToImages().convert(pdf, File(dir, "jpg"), "big", ImageFormat.JPG, 150, 90)
        assertEquals(100, jpg.size)
        Measure.log("pdf100_jpg150", "ms=${System.currentTimeMillis() - start} bytes=${jpg.sumOf { it.file.length() }}")

        start = System.currentTimeMillis()
        val png = PdfToImages().convert(pdf, File(dir, "png"), "big", ImageFormat.PNG, 300, 90)
        assertEquals(100, png.size)
        Measure.log("pdf100_png300", "ms=${System.currentTimeMillis() - start} bytes=${png.sumOf { it.file.length() }}")
        dir.deleteRecursively()
    }

    private fun reasonOf(block: () -> Unit): FailureReason? = try {
        block()
        null
    } catch (e: ConvertException) {
        e.reason
    }
}

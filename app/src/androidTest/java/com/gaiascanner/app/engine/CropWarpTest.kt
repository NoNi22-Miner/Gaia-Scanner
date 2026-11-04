package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.Point
import com.gaiascanner.app.core.Quad
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CropWarpTest {

    /** 400 x 300 blue image with a red rectangle in the middle half (100,75)-(300,225). */
    private fun makeImage(dir: File): File {
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        Canvas(bmp).drawRect(100f, 75f, 300f, 225f, Paint().apply { color = Color.RED })
        val file = File(dir, "target.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private val middle = Quad(Point(0.25f, 0.25f), Point(0.75f, 0.25f), Point(0.75f, 0.75f), Point(0.25f, 0.75f))

    private fun isRed(px: Int) = Color.red(px) > 200 && Color.blue(px) < 60

    @Test
    fun cropKeepsOnlyTheMarkedArea() {
        val dir = TestFiles.tempDir("warp")
        val file = makeImage(dir)
        val info = ImageDecoding.readInfo(file)!!
        assertEquals(200 to 150, ImageDecoding.finalSize(info, middle, 0))
        val out = ImageDecoding.decodeFinal(file, info, middle, 0, 200, 150)
        assertEquals(200, out.width)
        assertEquals(150, out.height)
        // Every sampled point, including near the edges, is inside the red area.
        for ((x, y) in listOf(4 to 4, 195 to 4, 100 to 75, 4 to 145, 195 to 145)) {
            assertTrue("pixel $x,$y ${Integer.toHexString(out.getPixel(x, y))}", isRed(out.getPixel(x, y)))
        }
        dir.deleteRecursively()
    }

    @Test
    fun cropThenRotateTurnsTheResult() {
        val dir = TestFiles.tempDir("warp-rot")
        val file = makeImage(dir)
        val info = ImageDecoding.readInfo(file)!!
        assertEquals(150 to 200, ImageDecoding.finalSize(info, middle, 90))
        val out = ImageDecoding.decodeFinal(file, info, middle, 90, 150, 200)
        assertEquals(150, out.width)
        assertEquals(200, out.height)
        assertTrue(isRed(out.getPixel(75, 100)))
        dir.deleteRecursively()
    }

    @Test
    fun perspectiveQuadIsStraightened() {
        val dir = TestFiles.tempDir("warp-persp")
        val file = makeImage(dir)
        val info = ImageDecoding.readInfo(file)!!
        // A trapezoid inside the red area: narrower at the top.
        val trapezoid = Quad(Point(0.35f, 0.3f), Point(0.65f, 0.3f), Point(0.7f, 0.7f), Point(0.3f, 0.7f))
        val (w, h) = ImageDecoding.finalSize(info, trapezoid, 0)
        val out = ImageDecoding.decodeFinal(file, info, trapezoid, 0, w, h)
        assertEquals(w, out.width)
        assertEquals(h, out.height)
        for ((x, y) in listOf(2 to 2, w - 3 to 2, w / 2 to h / 2, 2 to h - 3, w - 3 to h - 3)) {
            assertTrue("pixel $x,$y", isRed(out.getPixel(x, y)))
        }
        dir.deleteRecursively()
    }

    @Test
    fun croppedPagePdfUsesCroppedShape() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("warp-pdf")
        val file = makeImage(dir)
        val pdf = File(dir, "out.pdf")
        ImagesToPdf().convert(listOf(ImagesToPdf.Input(file, 90, middle)), pdf, PageSizeOption.FIT, MarginOption.NONE)
        val renderer = PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))
        val page = renderer.openPage(0)
        // Cropped 200 x 150, turned 90 degrees: portrait, long side 842.
        assertEquals(632, page.width)
        assertEquals(842, page.height)
        val shown = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        shown.eraseColor(Color.WHITE)
        page.render(shown, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        assertTrue(isRed(shown.getPixel(page.width / 2, page.height / 2)))
        page.close()
        renderer.close()
        dir.deleteRecursively()
    }
}

package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.Measure
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.EraseMode
import com.gaiascanner.app.core.EraseStroke
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageEdit
import com.gaiascanner.app.core.PageFilter
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.Point
import com.gaiascanner.app.core.Tone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class EnhanceDeviceTest {

    private val truth = listOf(Point(0.18f, 0.12f), Point(0.80f, 0.08f), Point(0.86f, 0.90f), Point(0.12f, 0.85f))

    /** A sheet with text lines, placed in perspective on a background, with uneven light. */
    private fun scene(background: Int, noise: Int, w: Int = 1200, h: Int = 900): Bitmap {
        val rnd = Random(5)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(background)
        val speck = Paint()
        repeat(4000) {
            val d = rnd.nextInt(-noise, noise + 1)
            speck.color = Color.rgb(
                (Color.red(background) + d).coerceIn(0, 255),
                (Color.green(background) + d).coerceIn(0, 255),
                (Color.blue(background) + d).coerceIn(0, 255),
            )
            c.drawRect(rnd.nextInt(w).toFloat(), rnd.nextInt(h).toFloat(), rnd.nextInt(w).toFloat() + 6, rnd.nextInt(h).toFloat() + 3, speck)
        }
        val paper = Bitmap.createBitmap(850, 1100, Bitmap.Config.ARGB_8888)
        val pc = Canvas(paper)
        pc.drawColor(Color.rgb(245, 245, 245))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); textSize = 26f }
        for (i in 0 until 28) pc.drawText("Invoice line $i  qty 10  amount 1000", 40f, 60f + i * 36, text)
        val m = Matrix()
        val src = floatArrayOf(0f, 0f, 850f, 0f, 850f, 1100f, 0f, 1100f)
        val dst = truth.flatMap { listOf(it.x * w, it.y * h) }.toFloatArray()
        m.setPolyToPoly(src, 0, dst, 0, 4)
        c.drawBitmap(paper, m, Paint(Paint.FILTER_BITMAP_FLAG))
        // Darker toward the bottom right.
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.argb(0, 0, 0, 0), Color.argb(80, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        return out
    }

    private fun maxCornerError(q: com.gaiascanner.app.core.Quad): Float =
        q.corners.zip(truth).maxOf { (a, b) -> maxOf(abs(a.x - b.x), abs(a.y - b.y)) }

    @Test
    fun openCvLoads() {
        assertTrue(OpenCv.ready)
    }

    @Test
    fun detectsPageOnDarkAndLightBackgrounds() {
        for ((name, bg, noise) in listOf(Triple("wood", Color.rgb(130, 90, 60), 25), Triple("light", Color.rgb(190, 190, 190), 12), Triple("dark", Color.rgb(20, 20, 20), 5))) {
            val img = scene(bg, noise)
            val start = System.currentTimeMillis()
            val q = EdgeDetector.detect(img)
            val ms = System.currentTimeMillis() - start
            Measure.log("edge_detect_$name", if (q == null) "none ms=$ms" else "error=${"%.4f".format(maxCornerError(q))} ms=$ms")
            assertNotNull("no page found on $name", q)
            assertTrue("$name corners off by ${maxCornerError(q!!)}", maxCornerError(q) < 0.02f)
        }
    }

    @Test
    fun plainBackgroundHasNoPage() {
        val img = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 120, 120)) }
        assertEquals(null, EdgeDetector.detect(img))
    }

    /** Shaded page: gray paper with text, a blue stamp, darker to the right. */
    private fun shadedPage(w: Int = 850, h: Int = 1100): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(238, 238, 238))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 24f * w / 850 }
        for (i in 0 until 22) c.drawText("PT. PRATAMA STEEL  line $i  1,000.00 KG", 40f * w / 850, (70f + i * 40) * h / 1100, text)
        val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 90, 200); style = Paint.Style.STROKE; strokeWidth = 8f * w / 850 }
        c.drawCircle(650f * w / 850, 850f * h / 1100, 80f * w / 850, stamp)
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.argb(0, 0, 0, 0), Color.argb(100, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        return b
    }

    private fun gray(px: Int) = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3

    @Test
    fun clearWhitensShadedPaperAndKeepsText() {
        val out = Enhancer.filter(shadedPage(), PageFilter.CLEAR)
        val paper = gray(out.getPixel(800, 300)) // blank, darkest side
        val textRow = (40 until 300).minOf { x -> gray(out.getPixel(x, 64)) }
        Measure.log("filter_clear", "paper=$paper text_min=$textRow")
        assertTrue("paper $paper", paper > 225)
        assertTrue("text $textRow", textRow < 90)
    }

    @Test
    fun docsIsBlackAndWhiteWithoutSpeckles() {
        val out = Enhancer.filter(shadedPage(), PageFilter.DOCS)
        var specks = 0
        for (y in 100 until 600 step 3) for (x in 700 until 840 step 3) if (gray(out.getPixel(x, y)) < 128) specks++
        val values = (0 until out.width step 7).flatMap { x -> (0 until out.height step 7).map { y -> gray(out.getPixel(x, y)) } }
        val inBetween = values.count { it in 41..214 }
        Measure.log("filter_docs", "specks=$specks mid_tones=$inBetween")
        assertEquals(0, specks)
        assertEquals(0, inBetween)
    }

    @Test
    fun colorKeepsTheBlueStamp() {
        val out = Enhancer.filter(shadedPage(), PageFilter.COLOR)
        // Darkest pixel along a line through the stamp ring.
        val ring = (565 until 740).map { out.getPixel(it, 850) }.minBy { gray(it) }
        assertTrue("stamp ${Integer.toHexString(ring)}", Color.blue(ring) > Color.red(ring) + 40)
    }

    @Test
    fun photoFilterAndToneChangeThePage() {
        // Brightness +50 adds 64 to mid gray (128 -> 192); contrast keeps mid gray.
        val mid = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(128, 128, 128)) }
        val brighter = Enhancer.tone(mid.copy(Bitmap.Config.ARGB_8888, false), Tone(brightness = 50))
        assertEquals(192.0, gray(brighter.getPixel(50, 50)).toDouble(), 3.0)
        val contrast = Enhancer.tone(mid, Tone(contrast = 60))
        assertEquals(128.0, gray(contrast.getPixel(50, 50)).toDouble(), 3.0)
        val photo = Enhancer.filter(shadedPage(), PageFilter.PHOTO)
        assertEquals(850, photo.width)
    }

    @Test
    fun whiteEraserPaintsWhite() {
        val b = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val stroke = EraseStroke(EraseMode.WHITE, 0.1f, listOf(Point(0.1f, 0.5f), Point(0.9f, 0.5f)))
        val out = Enhancer.erase(b, listOf(stroke))
        assertTrue(gray(out.getPixel(200, 150)) > 250)
        assertTrue(gray(out.getPixel(200, 20)) < 5)
    }

    @Test
    fun smartFillRemovesAMarkOnColoredPaper() {
        val b = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(180, 200, 240)) }
        Canvas(b).drawText("Scanned with CamScanner", 60f, 160f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f })
        val stroke = EraseStroke(EraseMode.FILL, 0.06f, listOf(Point(0.12f, 0.52f), Point(0.85f, 0.52f)))
        val start = System.currentTimeMillis()
        val out = Enhancer.erase(b, listOf(stroke))
        Measure.log("erase_fill_400x300_ms", System.currentTimeMillis() - start)
        val darkest = (60 until 330).minOf { x -> gray(out.getPixel(x, 155)) }
        assertTrue("left a mark of gray $darkest", darkest > 160)
    }

    @Test
    fun timeFiltersAtFullPageSize() {
        val big = shadedPage(2480, 3208)
        for (f in listOf(PageFilter.CLEAR, PageFilter.DOCS, PageFilter.COLOR, PageFilter.PHOTO)) {
            val start = System.currentTimeMillis()
            Enhancer.filter(big.copy(Bitmap.Config.ARGB_8888, false), f)
            Measure.log("filter_${f.name.lowercase()}_2480x3208_ms", System.currentTimeMillis() - start)
        }
    }

    @Test
    fun pdfPageUsesTheFilter() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("enhance-pdf")
        val file = File(dir, "page.png")
        FileOutputStream(file).use { shadedPage().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pdf = File(dir, "out.pdf")
        ImagesToPdf().convert(listOf(ImagesToPdf.Input(file, PageEdit(filter = PageFilter.DOCS))), pdf, PageSizeOption.FIT, MarginOption.NONE)
        val renderer = PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))
        val page = renderer.openPage(0)
        val shown = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        shown.eraseColor(Color.RED)
        page.render(shown, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        // The shaded right side of the paper comes out white.
        val px = shown.getPixel((page.width * 0.95).toInt(), (page.height * 0.3).toInt())
        assertTrue("pixel ${Integer.toHexString(px)}", gray(px) > 230 && Color.red(px) - Color.blue(px) < 20)
        page.close()
        renderer.close()
        dir.deleteRecursively()
    }
}

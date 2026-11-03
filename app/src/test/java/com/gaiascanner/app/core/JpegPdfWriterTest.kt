package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class JpegPdfWriterTest {

    private fun jpeg(width: Int, height: Int, color: Color): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    private fun write(pages: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        JpegPdfWriter(bytes).use { w ->
            repeat(pages) { i ->
                val data = jpeg(40, 30, if (i % 2 == 0) Color.RED else Color.BLUE)
                w.addPage(842, 632, data, 40, 30, Box(0f, 0f, 842f, 632f))
            }
            w.finish()
        }
        return bytes.toByteArray()
    }

    @Test
    fun hasHeaderTrailerAndPageCount() {
        val pdf = write(3)
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4\n"))
        assertTrue(text.endsWith("%%EOF\n"))
        assertTrue(text.contains("/Type /Pages /Kids [5 0 R 8 0 R 11 0 R] /Count 3"))
        assertEquals(3, Regex("/Filter /DCTDecode").findAll(text).count())
    }

    @Test
    fun xrefOffsetsPointAtObjects() {
        val pdf = write(2)
        val text = String(pdf, Charsets.ISO_8859_1)
        val startxref = Regex("startxref\n(\\d+)\n").find(text)!!.groupValues[1].toInt()
        assertTrue(text.startsWith("xref\n", startxref))
        val lines = text.substring(startxref).lines()
        val size = lines[1].split(" ")[1].toInt()
        for (n in 1 until size) {
            val entry = lines[2 + n]
            assertEquals("entry $n is 18 chars + space", 18, entry.trimEnd().length)
            val offset = entry.substring(0, 10).toInt()
            assertTrue("object $n at $offset", text.startsWith("$n 0 obj\n", offset))
        }
    }

    @Test
    fun streamLengthsMatchData() {
        val data = jpeg(10, 10, Color.GREEN)
        val bytes = ByteArrayOutputStream()
        JpegPdfWriter(bytes).use { w ->
            w.addPage(100, 100, data, 10, 10, Box(10f, 20f, 50f, 50f))
            w.finish()
        }
        val text = String(bytes.toByteArray(), Charsets.ISO_8859_1)
        val declared = Regex("/DCTDecode /Length (\\d+) >>\nstream\n").find(text)!!
        assertEquals(data.size, declared.groupValues[1].toInt())
        val start = declared.range.last + 1
        assertTrue(text.startsWith("\nendstream", start + data.size))
        // Box top 20 on a 100 pt page, 50 tall: bottom-left y = 100 - 20 - 50 = 30.
        assertTrue(text.contains("q 50 0 0 50 10 30 cm /Im0 Do Q\n"))
    }

    @Test
    fun numbersHaveNoExponent() {
        assertEquals("842", JpegPdfWriter.num(842f))
        assertEquals("446.25", JpegPdfWriter.num(446.25f))
        assertEquals("0.001", JpegPdfWriter.num(0.0012f))
        assertEquals("197.875", JpegPdfWriter.num(197.875f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonJpeg() {
        JpegPdfWriter(ByteArrayOutputStream()).addPage(10, 10, byteArrayOf(1, 2, 3), 1, 1, Box(0f, 0f, 10f, 10f))
    }

    @Test(expected = IllegalStateException::class)
    fun needsAPage() {
        JpegPdfWriter(ByteArrayOutputStream()).finish()
    }
}

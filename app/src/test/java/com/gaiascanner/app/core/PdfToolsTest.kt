package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PdfToolsTest {

    @Test
    fun presetsMatchTheOwnerNumbers() {
        assertEquals(150 to 85, ReducePreset.HIGH.dpi to ReducePreset.HIGH.jpegQuality)
        assertEquals(120 to 70, ReducePreset.MEDIUM.dpi to ReducePreset.MEDIUM.jpegQuality)
        assertEquals(90 to 55, ReducePreset.SMALL.dpi to ReducePreset.SMALL.jpegQuality)
    }

    @Test
    fun styleIsClampedAndOpacityBecomesAlpha() {
        val s = WatermarkStyle(text = "  me  ", sizePercent = 99, opacity = 0, angle = 200, color = 0x7F123456).normalized()
        assertEquals("me", s.text)
        assertEquals(WatermarkStyle.MAX_SIZE, s.sizePercent)
        assertEquals(WatermarkStyle.MIN_OPACITY, s.opacity)
        assertEquals(90, s.angle)
        assertEquals(0x123456, s.color)
        assertEquals(0xFF000000.toInt(), WatermarkStyle(color = 0, opacity = 100).argb)
        assertEquals(0x80, WatermarkStyle(opacity = 50).argb ushr 24)
    }

    @Test
    fun tilesCoverEveryCornerAtAnyAngle() {
        val w = 595f
        val h = 842f
        val tw = 200f
        val th = 40f
        val tiles = WatermarkLayout.tiles(w, h, tw, th)
        for (angle in listOf(0, 30, 45, 90, -60)) {
            val rad = Math.toRadians(angle.toDouble())
            // Corners of the page, relative to the centre, turned into the rotated frame.
            for ((cx, cy) in listOf(-w / 2 to -h / 2, w / 2 to -h / 2, w / 2 to h / 2, -w / 2 to h / 2)) {
                val x = (cx * cos(rad) - cy * sin(rad)).toFloat()
                val y = (cx * sin(rad) + cy * cos(rad)).toFloat()
                val near = tiles.any { (tx, ty) -> kotlin.math.abs(tx - x) <= tw && kotlin.math.abs(ty - y) <= th * 3 }
                assertTrue("corner ($cx,$cy) at $angle° has a tile nearby", near)
            }
        }
        assertTrue("bounded", tiles.size < 500)
    }

    @Test
    fun savedFraction() {
        assertEquals(0.75, WatermarkLayout.savedFraction(400, 100), 1e-9)
        assertEquals(-0.5, WatermarkLayout.savedFraction(100, 150), 1e-9)
        assertEquals(0.0, WatermarkLayout.savedFraction(0, 150), 1e-9)
    }
}

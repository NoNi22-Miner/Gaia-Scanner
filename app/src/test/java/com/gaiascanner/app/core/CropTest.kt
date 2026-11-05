package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropTest {

    @Test
    fun fullFrameIsDetected() {
        assertTrue(Quad.FULL.isFullFrame)
        assertFalse(Quad.FULL.withCorner(0, Point(0.1f, 0f)).isFullFrame)
    }

    @Test
    fun serializeRoundTrips() {
        val q = Quad(Point(0.1f, 0.05f), Point(0.9f, 0.1f), Point(0.95f, 0.9f), Point(0.05f, 0.85f))
        assertEquals("0.1,0.05,0.9,0.1,0.95,0.9,0.05,0.85", q.serialize())
        assertEquals(q, Quad.parse(q.serialize()))
    }

    @Test
    fun parseRejectsBadText() {
        assertNull(Quad.parse(null))
        assertNull(Quad.parse(""))
        assertNull(Quad.parse("1,2,3"))
        assertNull(Quad.parse("a,b,c,d,e,f,g,h"))
        // Out-of-range values are clamped, not rejected.
        assertEquals(Point(1f, 0f), Quad.parse("2,-1,1,0,1,1,0,1")!!.tl)
    }

    @Test
    fun orderedFixesSwappedCorners() {
        val messy = Quad(Point(0.9f, 0.1f), Point(0.1f, 0.1f), Point(0.1f, 0.9f), Point(0.9f, 0.9f))
        val q = messy.ordered()
        assertEquals(Point(0.1f, 0.1f), q.tl)
        assertEquals(Point(0.9f, 0.1f), q.tr)
        assertEquals(Point(0.9f, 0.9f), q.br)
        assertEquals(Point(0.1f, 0.9f), q.bl)
        assertEquals(Quad.FULL, Quad.FULL.ordered())
    }

    @Test
    fun outputSizeUsesLongerEdges() {
        assertEquals(4000 to 3000, Quad.FULL.outputSize(4000, 3000))
        // A trapezoid: top edge narrower than bottom. Width comes from the bottom edge.
        val q = Quad(Point(0.25f, 0f), Point(0.75f, 0f), Point(1f, 1f), Point(0f, 1f))
        val (w, h) = q.outputSize(1000, 1000)
        assertEquals(1000, w)
        assertEquals(1031, h) // side edge: hypot(250, 1000) = 1030.8
    }

    @Test
    fun rotationMappingRoundTrips() {
        val p = Point(0.2f, 0.7f)
        for (deg in listOf(0, 90, 180, 270)) {
            val back = CropRotation.toStored(CropRotation.toDisplay(p, deg), deg)
            assertEquals(p.x, back.x, 1e-6f)
            assertEquals(p.y, back.y, 1e-6f)
        }
    }

    @Test
    fun clockwiseTurnMovesTopLeftToTopRight() {
        assertEquals(Point(1f, 0f), CropRotation.toDisplay(Point(0f, 0f), 90))
        assertEquals(Point(1f, 1f), CropRotation.toDisplay(Point(1f, 0f), 90))
        assertEquals(Point(0f, 1f), CropRotation.toDisplay(Point(0f, 0f), 270))
        assertEquals(Point(1f, 1f), CropRotation.toDisplay(Point(0f, 0f), 180))
    }

    @Test
    fun finalSizeAppliesCropThenRotation() {
        val half = Quad(Point(0f, 0f), Point(0.5f, 0f), Point(0.5f, 1f), Point(0f, 1f))
        assertEquals(2000 to 3000, CropRotation.finalSize(4000, 3000, half, 0))
        assertEquals(3000 to 2000, CropRotation.finalSize(4000, 3000, half, 90))
        assertEquals(3000 to 4000, CropRotation.finalSize(4000, 3000, null, 270))
        assertEquals(4000 to 3000, CropRotation.finalSize(4000, 3000, Quad.FULL, 0))
    }
}

package com.gaiascanner.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhanceTest {

    @Test
    fun filterParseFallsBackToOriginal() {
        assertEquals(PageFilter.DOCS, PageFilter.parse("DOCS"))
        assertEquals(PageFilter.ORIGINAL, PageFilter.parse(null))
        assertEquals(PageFilter.ORIGINAL, PageFilter.parse("SEPIA"))
    }

    @Test
    fun strokesRoundTrip() {
        val strokes = listOf(
            EraseStroke(EraseMode.WHITE, 0.03f, listOf(Point(0.1f, 0.2f), Point(0.15f, 0.25f))),
            EraseStroke(EraseMode.FILL, 0.05f, listOf(Point(0.8f, 0.9f))),
        )
        val text = EraseStrokes.serialize(strokes)
        assertEquals("W|0.03|0.1,0.2;0.15,0.25\nF|0.05|0.8,0.9", text)
        assertEquals(strokes, EraseStrokes.parse(text))
    }

    @Test
    fun noStrokesIsNull() {
        assertNull(EraseStrokes.serialize(emptyList()))
        assertNull(EraseStrokes.serialize(listOf(EraseStroke(EraseMode.WHITE, 0.1f, emptyList()))))
        assertTrue(EraseStrokes.parse(null).isEmpty())
    }

    @Test
    fun malformedStrokesAreSkipped() {
        val parsed = EraseStrokes.parse("X|0.1|0,0\nW|abc|0,0\nW|0.1|\nW|0.1|0.5,0.5;bad\nF|0.2|2,-1")
        assertEquals(2, parsed.size)
        assertEquals(listOf(Point(0.5f, 0.5f)), parsed[0].points)
        assertEquals(Point(1f, 0f), parsed[1].points[0]) // clamped
    }

    @Test
    fun neutralToneIsIdentity() {
        val m = Tone().matrix()
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
            m,
            1e-6f,
        )
        assertTrue(Tone().isNeutral)
    }

    @Test
    fun toneMovesMidGray() {
        // Mid gray 128 stays 128 under contrast alone; brightness +50 adds 64.
        val c = Tone(contrast = 50).matrix()
        assertEquals(128f, 128f * c[0] + c[4], 1e-3f)
        val b = Tone(brightness = 50).matrix()
        assertEquals(192f, 128f * b[0] + b[4], 1e-3f)
    }

    @Test
    fun editKeyChangesWithSettings() {
        val a = PageEdit()
        val b = a.copy(filter = PageFilter.CLEAR)
        val c = a.copy(tone = Tone(brightness = 10))
        assertNotEquals(a.key, b.key)
        assertNotEquals(a.key, c.key)
        assertEquals(a.key, PageEdit().key)
    }

    @Test
    fun moveEdgeMovesBothCorners() {
        val q = Quad.FULL.moveEdge(0, 0f, 0.1f, 0) // top side down
        assertEquals(Point(0f, 0.1f), q.tl)
        assertEquals(Point(1f, 0.1f), q.tr)
        assertEquals(Point(1f, 1f), q.br)
        // Turned 90 degrees, stored side bl->tl is shown as the top. Dragging it down moves both corners right in the stored image.
        val r = Quad.FULL.moveEdge(3, 0f, 0.1f, 90) // side bl->tl, shown as the top after a 90 degree turn
        assertEquals(Point(0.1f, 0f), r.tl)
        assertEquals(Point(0.1f, 1f), r.bl)
    }
}

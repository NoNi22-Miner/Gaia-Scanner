package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMathTest {

    @Test
    fun a4At72DpiIsOnePixelPerPoint() {
        val size = PageMath.renderSize(595, 842, 72)
        assertEquals(595, size.width)
        assertEquals(842, size.height)
        assertFalse(size.capped)
    }

    @Test
    fun a4At150And300Dpi() {
        val s150 = PageMath.renderSize(595, 842, 150)
        assertEquals(1240, s150.width) // 595 * 150 / 72 = 1239.6
        assertEquals(1754, s150.height) // 842 * 150 / 72 = 1754.2
        val s300 = PageMath.renderSize(595, 842, 300)
        assertEquals(2479, s300.width) // 2479.2
        assertEquals(3508, s300.height) // 3508.3
        assertFalse(s300.capped)
    }

    @Test
    fun a4At600DpiIsCappedTo16Megapixels() {
        val size = PageMath.renderSize(595, 842, 600)
        assertTrue(size.capped)
        assertTrue(size.width.toLong() * size.height <= ConvertDefaults.MAX_PAGE_PIXELS)
        // Aspect ratio is kept within one pixel.
        val expectedHeight = size.width * 842.0 / 595.0
        assertEquals(expectedHeight, size.height.toDouble(), 1.5)
        assertTrue(size.dpi < 600f && size.dpi > 400f)
    }

    @Test
    fun hugePosterPageIsCapped() {
        val size = PageMath.renderSize(2384, 3370, 300) // A0 in points
        assertTrue(size.capped)
        assertTrue(size.width.toLong() * size.height <= ConvertDefaults.MAX_PAGE_PIXELS)
    }

    @Test
    fun fitPageFollowsImageShape() {
        assertEquals(PagePoints(842, 632), PageMath.pageSizeFor(4000, 3000, PageSizeOption.FIT))
        assertEquals(PagePoints(632, 842), PageMath.pageSizeFor(3000, 4000, PageSizeOption.FIT))
        assertEquals(PagePoints(842, 842), PageMath.pageSizeFor(500, 500, PageSizeOption.FIT))
    }

    @Test
    fun fixedPagesTurnLandscapeForWideImages() {
        assertEquals(PagePoints(595, 842), PageMath.pageSizeFor(3000, 4000, PageSizeOption.A4))
        assertEquals(PagePoints(842, 595), PageMath.pageSizeFor(4000, 3000, PageSizeOption.A4))
        assertEquals(PagePoints(612, 792), PageMath.pageSizeFor(10, 20, PageSizeOption.LETTER))
        assertEquals(PagePoints(792, 612), PageMath.pageSizeFor(20, 10, PageSizeOption.LETTER))
    }

    @Test
    fun contentBoxAppliesMargins() {
        val box = PageMath.contentBox(PageMath.A4, MarginOption.LARGE)
        assertEquals(Box(36f, 36f, 523f, 770f), box)
        assertEquals(Box(0f, 0f, 595f, 842f), PageMath.contentBox(PageMath.A4, MarginOption.NONE))
    }

    @Test
    fun marginsShrinkOnTinyPages() {
        val box = PageMath.contentBox(PagePoints(40, 40), MarginOption.LARGE)
        assertEquals(10f, box.left)
        assertEquals(20f, box.width)
    }

    @Test
    fun fitInsideCentersAndKeepsAspect() {
        val area = Box(0f, 0f, 595f, 842f)
        val fit = PageMath.fitInside(area, 4000, 3000)
        assertEquals(595f, fit.width, 0.01f)
        assertEquals(446.25f, fit.height, 0.01f)
        assertEquals((842f - 446.25f) / 2f, fit.top, 0.01f)
        assertEquals(0f, fit.left, 0.01f)
    }

    @Test
    fun targetPixelsNeverUpscale() {
        // A small image stays at its own size.
        assertEquals(800 to 600, PageMath.targetPixels(842f, 632f, 300, 800, 600))
        // A large photo is reduced to 300 DPI on the page.
        assertEquals(3508 to 2633, PageMath.targetPixels(842f, 632f, 300, 4000, 3000))
    }

    @Test
    fun sampleSizeIsPowerOfTwoAndKeepsTarget() {
        assertEquals(1, PageMath.sampleSize(4000, 3000, 3508, 2633))
        assertEquals(2, PageMath.sampleSize(4000, 3000, 2000, 1500))
        assertEquals(4, PageMath.sampleSize(4000, 3000, 1000, 700))
        assertEquals(1, PageMath.sampleSize(100, 100, 200, 200))
    }
}

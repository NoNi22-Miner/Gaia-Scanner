package com.gaiascanner.app.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Page size in PostScript points (1/72 inch). */
data class PagePoints(val width: Int, val height: Int)

/** Bitmap size for one rendered PDF page. [dpi] is the DPI actually used. */
data class PixelSize(val width: Int, val height: Int, val dpi: Float, val capped: Boolean)

/** Axis-aligned rectangle in points or pixels. */
data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

object PageMath {
    const val POINTS_PER_INCH = 72.0

    /** A4 is 210 x 297 mm, about 595.28 x 841.89 pt. PdfDocument takes whole points. */
    val A4 = PagePoints(595, 842)
    val LETTER = PagePoints(612, 792)
    const val FIT_LONG_SIDE_PT = 842

    /**
     * Pixel size for rendering a page of [widthPt] x [heightPt] points at [dpi].
     * If the result would exceed [maxPixels], the DPI is lowered for this page.
     */
    fun renderSize(
        widthPt: Int,
        heightPt: Int,
        dpi: Int,
        maxPixels: Long = ConvertDefaults.MAX_PAGE_PIXELS,
    ): PixelSize {
        require(widthPt > 0 && heightPt > 0) { "Page size must be positive" }
        require(dpi > 0) { "DPI must be positive" }
        val scale = dpi / POINTS_PER_INCH
        val w = max(1, (widthPt * scale).roundToInt())
        val h = max(1, (heightPt * scale).roundToInt())
        if (w.toLong() * h <= maxPixels) {
            return PixelSize(w, h, dpi.toFloat(), capped = false)
        }
        val cappedScale = sqrt(maxPixels.toDouble() / (widthPt.toDouble() * heightPt))
        val cw = max(1, floor(widthPt * cappedScale).toInt())
        val ch = max(1, floor(heightPt * cappedScale).toInt())
        return PixelSize(cw, ch, (cappedScale * POINTS_PER_INCH).toFloat(), capped = true)
    }

    /** Page size for an image of [imageWidth] x [imageHeight] pixels (already rotated). */
    /** Long side of a new blank page image: A4 at 150 DPI. Plain white, so more pixels add nothing. */
    const val BLANK_LONG_SIDE_PX = 1754

    /**
     * Pixel size of a new blank page with the same shape as a reference page of
     * [refWidth] x [refHeight] (its final size after crop and rotation). Without a
     * reference, an A4 portrait page.
     */
    fun blankPageSize(refWidth: Int?, refHeight: Int?): Pair<Int, Int> {
        if (refWidth == null || refHeight == null || refWidth <= 0 || refHeight <= 0) {
            // A4 is exactly 210 x 297 mm; the whole-point A4 above rounds to 1239 px.
            return (BLANK_LONG_SIDE_PX * 210.0 / 297.0).roundToInt() to BLANK_LONG_SIDE_PX
        }
        val scale = BLANK_LONG_SIDE_PX.toDouble() / max(refWidth, refHeight)
        return max(1, (refWidth * scale).roundToInt()) to max(1, (refHeight * scale).roundToInt())
    }

    fun pageSizeFor(imageWidth: Int, imageHeight: Int, option: PageSizeOption): PagePoints {
        require(imageWidth > 0 && imageHeight > 0) { "Image size must be positive" }
        val landscape = imageWidth > imageHeight
        return when (option) {
            PageSizeOption.FIT -> {
                if (landscape) {
                    PagePoints(FIT_LONG_SIDE_PT, max(1, (FIT_LONG_SIDE_PT.toDouble() * imageHeight / imageWidth).roundToInt()))
                } else {
                    PagePoints(max(1, (FIT_LONG_SIDE_PT.toDouble() * imageWidth / imageHeight).roundToInt()), FIT_LONG_SIDE_PT)
                }
            }
            PageSizeOption.A4 -> orient(A4, landscape)
            PageSizeOption.LETTER -> orient(LETTER, landscape)
        }
    }

    private fun orient(page: PagePoints, landscape: Boolean): PagePoints =
        if (landscape) PagePoints(max(page.width, page.height), min(page.width, page.height))
        else PagePoints(min(page.width, page.height), max(page.width, page.height))

    /** Area inside the margins. Margins shrink if they would leave no room. */
    fun contentBox(page: PagePoints, margin: MarginOption): Box {
        val m = min(margin.points.toFloat(), min(page.width, page.height) / 4f)
        return Box(m, m, page.width - 2 * m, page.height - 2 * m)
    }

    /** Largest box with the image's aspect ratio that fits inside [area], centered. */
    fun fitInside(area: Box, imageWidth: Int, imageHeight: Int): Box {
        require(imageWidth > 0 && imageHeight > 0) { "Image size must be positive" }
        val scale = min(area.width / imageWidth, area.height / imageHeight)
        val w = imageWidth * scale
        val h = imageHeight * scale
        return Box(area.left + (area.width - w) / 2f, area.top + (area.height - h) / 2f, w, h)
    }

    /**
     * Pixel size to store an image drawn into a box of [boxWidthPt] x [boxHeightPt] points,
     * at most [dpi]. Never upscales past the image's own size.
     */
    fun targetPixels(boxWidthPt: Float, boxHeightPt: Float, dpi: Int, imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        val tw = (boxWidthPt * dpi / POINTS_PER_INCH).roundToInt()
        val th = (boxHeightPt * dpi / POINTS_PER_INCH).roundToInt()
        if (tw >= imageWidth || th >= imageHeight) return imageWidth to imageHeight
        return max(1, tw) to max(1, th)
    }

    /**
     * Largest power-of-two sample size that keeps the decoded image at least
     * [reqWidth] x [reqHeight]. Same rule as BitmapFactory.Options.inSampleSize.
     */
    fun sampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= reqWidth && srcHeight / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }
}

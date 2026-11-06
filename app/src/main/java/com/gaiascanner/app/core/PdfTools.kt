package com.gaiascanner.app.core

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Reduce file size presets (owner decision, 2026-11-05). Each page is rendered
 * at [dpi] and stored as JPEG at [jpegQuality], so text becomes part of the image.
 */
enum class ReducePreset(val dpi: Int, val jpegQuality: Int) {
    HIGH(150, 85),
    MEDIUM(120, 70),
    SMALL(90, 55),
}

/**
 * A text watermark. [sizePercent] is the text height as a percentage of the
 * page width. [angle] is in degrees, counter-clockwise (45 rises to the right).
 * [opacity] is 0 to 100. [color] is opaque RGB as 0xRRGGBB.
 */
data class WatermarkStyle(
    val text: String = "",
    val sizePercent: Int = 8,
    val color: Int = 0x808080,
    val opacity: Int = 35,
    val angle: Int = 30,
    val tiled: Boolean = true,
) {
    fun normalized() = copy(
        text = text.trim().take(MAX_TEXT),
        sizePercent = sizePercent.coerceIn(MIN_SIZE, MAX_SIZE),
        color = color and 0xFFFFFF,
        opacity = opacity.coerceIn(MIN_OPACITY, 100),
        angle = angle.coerceIn(-90, 90),
    )

    /** ARGB colour with the opacity applied, for android.graphics.Paint. */
    val argb: Int get() = ((opacity.coerceIn(0, 100) * 255 + 50) / 100 shl 24) or (color and 0xFFFFFF)

    companion object {
        const val MAX_TEXT = 60
        const val MIN_SIZE = 3
        const val MAX_SIZE = 20
        const val MIN_OPACITY = 5

        /** Colour choices offered in the editor. */
        val COLORS = listOf(0x808080, 0x000000, 0xD32F2F, 0x1565C0, 0xFFFFFF)
    }
}

object WatermarkLayout {

    /**
     * Text centres for a tiled watermark, relative to the page centre, in the
     * rotated frame (rotate the canvas about the centre first). The grid covers
     * the page's diagonal, so no corner is left bare at any angle. Rows are
     * offset by half a step, like bricks.
     */
    fun tiles(pageWidth: Float, pageHeight: Float, textWidth: Float, textHeight: Float): List<Pair<Float, Float>> {
        if (pageWidth <= 0f || pageHeight <= 0f || textWidth <= 0f || textHeight <= 0f) return emptyList()
        val stepX = textWidth + max(textHeight * 2f, pageWidth * 0.08f)
        val stepY = textHeight * 3f
        val half = hypot(pageWidth, pageHeight) / 2f
        val cols = ceil(half / stepX).toInt() + 1
        val rows = ceil(half / stepY).toInt()
        val result = ArrayList<Pair<Float, Float>>()
        for (r in -rows..rows) {
            val shift = if (r % 2 == 0) 0f else stepX / 2f
            for (c in -cols..cols) {
                result += (c * stepX + shift) to (r * stepY)
            }
        }
        return result
    }

    /** Fraction by which a copy is smaller than the original: 0.75 means 75% smaller. Negative if larger. */
    fun savedFraction(originalBytes: Long, newBytes: Long): Double =
        if (originalBytes <= 0) 0.0 else 1.0 - newBytes.toDouble() / originalBytes
}

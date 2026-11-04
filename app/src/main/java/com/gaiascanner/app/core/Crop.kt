package com.gaiascanner.app.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point as fractions (0 to 1) of an image's width and height. Origin top-left. */
data class Point(val x: Float, val y: Float) {
    fun clamped() = Point(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * The four corners of the part of a photo to keep. The app straightens this
 * area into a flat rectangle (perspective crop).
 *
 * Corners are fractions of the image's upright size (EXIF applied), before the
 * user's extra rotation. Order: top-left, top-right, bottom-right, bottom-left.
 */
data class Quad(val tl: Point, val tr: Point, val br: Point, val bl: Point) {

    val corners: List<Point> get() = listOf(tl, tr, br, bl)

    /** True when the crop keeps the whole image, so it can be skipped. */
    val isFullFrame: Boolean
        get() = corners.zip(FULL.corners).all { (a, b) -> abs(a.x - b.x) < EPS && abs(a.y - b.y) < EPS }

    fun withCorner(index: Int, point: Point): Quad {
        val p = point.clamped()
        return when (index) {
            0 -> copy(tl = p)
            1 -> copy(tr = p)
            2 -> copy(br = p)
            3 -> copy(bl = p)
            else -> throw IllegalArgumentException("Corner index $index")
        }
    }

    /**
     * Puts the corners back in top-left, top-right, bottom-right, bottom-left
     * order, in case the user dragged one corner past another. Sorts them
     * clockwise around their centre and starts from the one nearest the top-left.
     */
    fun ordered(): Quad {
        val cx = corners.map { it.x }.average().toFloat()
        val cy = corners.map { it.y }.average().toFloat()
        // With y pointing down, a growing angle goes clockwise on screen.
        val clockwise = corners.sortedBy { atan2(it.y - cy, it.x - cx) }
        val start = clockwise.indices.minBy { clockwise[it].x + clockwise[it].y }
        val r = List(4) { clockwise[(start + it) % 4] }
        return Quad(r[0], r[1], r[2], r[3])
    }

    /**
     * Pixel size of the straightened result for an image of [width] x [height]
     * pixels: the longer of each pair of opposite edges.
     */
    fun outputSize(width: Int, height: Int): Pair<Int, Int> {
        fun len(a: Point, b: Point) = hypot((a.x - b.x) * width.toDouble(), (a.y - b.y) * height.toDouble())
        val w = max(len(tl, tr), len(bl, br)).roundToInt()
        val h = max(len(tl, bl), len(tr, br)).roundToInt()
        return max(1, w) to max(1, h)
    }

    /**
     * Moves the side from corner [edge] to the next corner by [dx], [dy]:
     * fractions of the image as shown after the user's [rotation].
     */
    fun moveEdge(edge: Int, dx: Float, dy: Float, rotation: Int): Quad {
        var q = this
        for (i in listOf(edge, (edge + 1) % 4)) {
            val shown = CropRotation.toDisplay(corners[i], rotation)
            q = q.withCorner(i, CropRotation.toStored(Point(shown.x + dx, shown.y + dy).clamped(), rotation))
        }
        return q
    }

    /** "x1,y1,x2,y2,x3,y3,x4,y4" for the database. */
    fun serialize(): String =
        corners.joinToString(",") { "${fmt(it.x)},${fmt(it.y)}" }

    companion object {
        private const val EPS = 0.002f

        val FULL = Quad(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f))

        /** Reads [serialize] output. Returns null for null, blank or malformed text. */
        fun parse(text: String?): Quad? {
            if (text.isNullOrBlank()) return null
            val v = text.split(',').map { it.trim().toFloatOrNull() ?: return null }
            if (v.size != 8 || v.any { it.isNaN() }) return null
            val p = List(4) { Point(v[it * 2], v[it * 2 + 1]).clamped() }
            return Quad(p[0], p[1], p[2], p[3])
        }

        private fun fmt(v: Float) = String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
    }
}

/**
 * Maps points between the stored image (no user rotation) and the image as
 * shown on screen after the user's clockwise rotation. Both use fractions.
 */
object CropRotation {
    fun toDisplay(p: Point, degrees: Int): Point = when (Rotation.normalize(degrees)) {
        90 -> Point(1f - p.y, p.x)
        180 -> Point(1f - p.x, 1f - p.y)
        270 -> Point(p.y, 1f - p.x)
        else -> p
    }

    fun toStored(p: Point, degrees: Int): Point = when (Rotation.normalize(degrees)) {
        90 -> Point(p.y, 1f - p.x)
        180 -> Point(1f - p.x, 1f - p.y)
        270 -> Point(1f - p.y, p.x)
        else -> p
    }

    /**
     * Final pixel size of a page image: upright size [uprightWidth] x
     * [uprightHeight], then the crop (if any), then the user's rotation.
     */
    fun finalSize(uprightWidth: Int, uprightHeight: Int, crop: Quad?, userRotation: Int): Pair<Int, Int> {
        val (w, h) = if (crop == null || crop.isFullFrame) uprightWidth to uprightHeight else crop.outputSize(uprightWidth, uprightHeight)
        return Rotation.rotatedSize(w, h, userRotation)
    }
}

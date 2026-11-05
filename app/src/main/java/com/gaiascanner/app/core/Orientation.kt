package com.gaiascanner.app.core

/**
 * How to turn a stored image upright: rotate clockwise by [degrees], then mirror
 * left-right if [flip]. Matches the EXIF orientation tag values 1 to 8.
 */
data class ExifTransform(val degrees: Int, val flip: Boolean) {
    companion object {
        val NONE = ExifTransform(0, false)

        fun fromExif(orientation: Int): ExifTransform = when (orientation) {
            2 -> ExifTransform(0, true) // flip horizontal
            3 -> ExifTransform(180, false)
            4 -> ExifTransform(180, true) // flip vertical
            5 -> ExifTransform(90, true) // transpose
            6 -> ExifTransform(90, false)
            7 -> ExifTransform(270, true) // transverse
            8 -> ExifTransform(270, false)
            else -> NONE
        }
    }
}

object Rotation {
    val ALLOWED = listOf(0, 90, 180, 270)

    fun normalize(degrees: Int): Int = ((degrees % 360) + 360) % 360 / 90 * 90

    fun rotateClockwise(current: Int): Int = normalize(current + 90)

    /** Size after turning by [degrees]: width and height swap for 90 and 270. */
    fun rotatedSize(width: Int, height: Int, degrees: Int): Pair<Int, Int> =
        if (normalize(degrees) % 180 == 90) height to width else width to height
}

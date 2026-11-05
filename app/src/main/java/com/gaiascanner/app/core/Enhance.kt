package com.gaiascanner.app.core

import java.util.Locale

/** Look of a page. Clear, Docs and Color use OpenCV; Photo is a colour matrix. */
enum class PageFilter {
    ORIGINAL,
    PHOTO,
    CLEAR,
    DOCS,
    COLOR;

    companion object {
        fun parse(name: String?): PageFilter = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

enum class EraseMode(val code: Char) {
    /** Paints white. Best on white paper. */
    WHITE('W'),

    /** Fills from the surrounding background (OpenCV inpaint). */
    FILL('F'),
}

/**
 * One eraser stroke. [points] are fractions of the page image after crop and
 * rotation. [width] is a fraction of that image's width.
 */
data class EraseStroke(val mode: EraseMode, val width: Float, val points: List<Point>)

object EraseStrokes {
    /** "W|0.03|x,y;x,y\nF|0.05|x,y" or null when there are no strokes. */
    fun serialize(strokes: List<EraseStroke>): String? {
        val usable = strokes.filter { it.points.isNotEmpty() }
        if (usable.isEmpty()) return null
        return usable.joinToString("\n") { s ->
            "${s.mode.code}|${fmt(s.width)}|" + s.points.joinToString(";") { "${fmt(it.x)},${fmt(it.y)}" }
        }
    }

    /** Reads [serialize] output. Malformed strokes are skipped. */
    fun parse(text: String?): List<EraseStroke> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@mapNotNull null
            val mode = EraseMode.entries.firstOrNull { it.code.toString() == parts[0] } ?: return@mapNotNull null
            val width = parts[1].toFloatOrNull()?.takeIf { it > 0f && it < 1f } ?: return@mapNotNull null
            val points = parts[2].split(';').mapNotNull { p ->
                val xy = p.split(',')
                if (xy.size != 2) return@mapNotNull null
                val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
                val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
                Point(x, y).clamped()
            }
            if (points.isEmpty()) null else EraseStroke(mode, width, points)
        }
    }

    private fun fmt(v: Float) = String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
}

/** Brightness and contrast, each -100 to 100, 0 = unchanged. */
data class Tone(val brightness: Int = 0, val contrast: Int = 0) {
    val isNeutral: Boolean get() = brightness == 0 && contrast == 0

    /**
     * A 4 x 5 matrix for android.graphics.ColorMatrix:
     * out = (in - 128) x c + 128 + b for red, green and blue.
     */
    fun matrix(): FloatArray {
        val c = ((100 + contrast.coerceIn(-100, 100)) / 100f).coerceIn(0.2f, 2f)
        val b = brightness.coerceIn(-100, 100) * 1.28f
        val t = 128f * (1f - c) + b
        return floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/**
 * Everything the user changed on one page. The original photo is kept and
 * this is applied when rendering, in this order: crop, rotation, erase, tone, filter.
 */
data class PageEdit(
    val crop: Quad? = null,
    val rotation: Int = 0,
    val strokes: List<EraseStroke> = emptyList(),
    val tone: Tone = Tone(),
    val filter: PageFilter = PageFilter.ORIGINAL,
) {
    /** Stable text for cache keys. */
    val key: String
        get() = listOf(
            crop?.serialize().orEmpty(),
            Rotation.normalize(rotation).toString(),
            EraseStrokes.serialize(strokes).orEmpty().hashCode().toString(),
            tone.brightness.toString(),
            tone.contrast.toString(),
            filter.name,
        ).joinToString("|")
}

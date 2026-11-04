package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import com.gaiascanner.app.core.EraseMode
import com.gaiascanner.app.core.EraseStroke
import com.gaiascanner.app.core.PageFilter
import com.gaiascanner.app.core.Tone
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Eraser, brightness/contrast and filters. Each function returns a new bitmap
 * and recycles its input, or returns the input unchanged when there is nothing to do.
 */
object Enhancer {

    // ---- Eraser ----

    fun erase(bitmap: Bitmap, strokes: List<EraseStroke>): Bitmap {
        if (strokes.isEmpty()) return bitmap
        var out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmap.recycle()
        val white = strokes.filter { it.mode == EraseMode.WHITE }
        if (white.isNotEmpty()) {
            val canvas = Canvas(out)
            white.forEach { draw(canvas, it, out.width, out.height, Color.WHITE) }
        }
        val fill = strokes.filter { it.mode == EraseMode.FILL }
        if (fill.isNotEmpty() && OpenCv.ready) out = inpaint(out, fill)
        return out
    }

    private fun draw(canvas: Canvas, s: EraseStroke, w: Int, h: Int, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = max(1f, s.width * w)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (s.points.size == 1) {
            canvas.drawPoint(s.points[0].x * w, s.points[0].y * h, paint)
            return
        }
        val path = Path()
        path.moveTo(s.points[0].x * w, s.points[0].y * h)
        s.points.drop(1).forEach { path.lineTo(it.x * w, it.y * h) }
        canvas.drawPath(path, paint)
    }

    /** Fills the brushed areas from their surroundings (OpenCV inpaint, Telea). */
    private fun inpaint(bitmap: Bitmap, strokes: List<EraseStroke>): Bitmap {
        val maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        maskBitmap.eraseColor(Color.BLACK)
        val canvas = Canvas(maskBitmap)
        strokes.forEach { draw(canvas, it, bitmap.width, bitmap.height, Color.WHITE) }
        val rgb = OpenCv.toRgb(bitmap)
        val maskRgb = OpenCv.toRgb(maskBitmap)
        maskBitmap.recycle()
        val mask = Mat()
        Imgproc.cvtColor(maskRgb, mask, Imgproc.COLOR_RGB2GRAY)
        Imgproc.threshold(mask, mask, 0.0, 255.0, Imgproc.THRESH_BINARY)
        val radius = (strokes.map { it.width * bitmap.width }.average() / 2).coerceIn(3.0, 20.0)
        val result = Mat()
        Photo.inpaint(rgb, mask, result, radius, Photo.INPAINT_TELEA)
        val out = OpenCv.toBitmap(result)
        listOf(rgb, maskRgb, mask, result).forEach { it.release() }
        bitmap.recycle()
        return out
    }

    // ---- Brightness and contrast ----

    fun tone(bitmap: Bitmap, tone: Tone): Bitmap =
        if (tone.isNeutral) bitmap else colorMatrix(bitmap, ColorMatrix(tone.matrix()))

    private fun colorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        bitmap.recycle()
        return out
    }

    private fun saturation(factor: Float) = ColorMatrix().apply { setSaturation(factor) }

    // ---- Filters ----

    fun filter(bitmap: Bitmap, filter: PageFilter): Bitmap = when (filter) {
        PageFilter.ORIGINAL -> bitmap
        PageFilter.PHOTO -> colorMatrix(
            bitmap,
            ColorMatrix(Tone(brightness = -6, contrast = 12).matrix()).apply { postConcat(saturation(1.2f)) },
        )
        PageFilter.CLEAR -> openCv(bitmap) { clear(it) }
        PageFilter.DOCS -> openCv(bitmap) { docs(it) }
        PageFilter.COLOR -> {
            val cleared = openCv(bitmap) { clear(it) }
            colorMatrix(cleared, saturation(1.5f))
        }
    }

    private fun openCv(bitmap: Bitmap, block: (Mat) -> Mat): Bitmap {
        if (!OpenCv.ready) return bitmap
        val rgb = OpenCv.toRgb(bitmap)
        val result = block(rgb)
        val out = OpenCv.toBitmap(result)
        rgb.release()
        if (result !== rgb) result.release()
        bitmap.recycle()
        return out
    }

    /**
     * Whitens the paper and removes shadows: each colour channel is divided by a
     * smooth estimate of the paper (text closed away with dilate, then a median
     * blur, computed at quarter size for speed), then text is deepened.
     */
    private fun clear(rgb: Mat): Mat {
        val w = rgb.cols()
        val h = rgb.rows()
        val k = max(7, (min(w, h) / 60) or 1)
        val small = Size(max(1, w / 4).toDouble(), max(1, h / 4).toDouble())
        val ks = max(3, (k / 4) or 1)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(ks.toDouble(), ks.toDouble()))
        val channels = mutableListOf<Mat>()
        Core.split(rgb, channels)
        for (ch in channels) {
            val s = Mat()
            Imgproc.resize(ch, s, small, 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.dilate(s, s, kernel)
            Imgproc.medianBlur(s, s, 2 * ks + 1)
            val bg = Mat()
            Imgproc.resize(s, bg, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Core.divide(ch, bg, ch, 255.0)
            s.release()
            bg.release()
        }
        val out = Mat()
        Core.merge(channels, out)
        channels.forEach { it.release() }
        kernel.release()
        Core.convertScaleAbs(out, out, 1.2, -40.0)
        return out
    }

    /** Black and white: Clear, then gray, then an Otsu threshold (no speckles, unlike adaptive). */
    private fun docs(rgb: Mat): Mat {
        val cleared = clear(rgb)
        val gray = Mat()
        Imgproc.cvtColor(cleared, gray, Imgproc.COLOR_RGB2GRAY)
        cleared.release()
        Imgproc.threshold(gray, gray, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        return gray
    }

    /** Scales [bitmap] down so its longer side is at most [maxSide]. */
    fun fit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val s = maxSide.toDouble() / longest
        val out = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * s).roundToInt()), max(1, (bitmap.height * s).roundToInt()), true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }
}

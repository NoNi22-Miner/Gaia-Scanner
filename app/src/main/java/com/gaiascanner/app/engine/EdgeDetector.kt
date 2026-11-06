package com.gaiascanner.app.engine

import android.graphics.Bitmap
import com.gaiascanner.app.core.Point
import com.gaiascanner.app.core.Quad
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds the four corners of a sheet of paper in a photo (OpenCV).
 * Same steps as the opencv-python prototype described in docs/SPEC.md 3.9.
 */
object EdgeDetector {
    private const val WORK_SIDE = 800.0
    private const val MIN_AREA = 0.15
    private const val MAX_AREA = 0.97
    private const val MIN_SCORE = 0.05

    /** Corners as fractions of [bitmap], or null if no page was found. */
    fun detect(bitmap: Bitmap): Quad? {
        if (!OpenCv.ready) return null
        val mats = mutableListOf<Mat>()
        fun <T : Mat> keep(m: T): T = m.also { mats += it }
        try {
            val rgb = keep(OpenCv.toRgb(bitmap))
            val scale = WORK_SIDE / max(rgb.cols(), rgb.rows())
            val small = keep(Mat())
            Imgproc.resize(rgb, small, Size(max(1.0, rgb.cols() * scale), max(1.0, rgb.rows() * scale)), 0.0, 0.0, Imgproc.INTER_AREA)
            val gray = keep(Mat())
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val w = gray.cols()
            val h = gray.rows()
            val area = w.toDouble() * h
            val k3 = keep(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

            // Several ways to separate paper from background.
            val binaries = mutableListOf<Mat>()
            val median = median(gray)
            for ((lo, hi) in listOf(0.66 * median to 1.33 * median, 40.0 to 120.0, 15.0 to 45.0)) {
                val e = keep(Mat())
                Imgproc.Canny(gray, e, max(0.0, lo), minOf(255.0, hi))
                Imgproc.dilate(e, e, k3, org.opencv.core.Point(-1.0, -1.0), 2)
                binaries += e
            }
            val otsu = keep(Mat())
            Imgproc.threshold(gray, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            val k9 = keep(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)))
            Imgproc.morphologyEx(otsu, otsu, Imgproc.MORPH_CLOSE, k9)
            binaries += otsu

            // Edge strength, to score how well a shape's outline sits on real edges.
            val grad = keep(Mat())
            Imgproc.morphologyEx(gray, grad, Imgproc.MORPH_GRADIENT, k3)
            Imgproc.dilate(grad, grad, k3, org.opencv.core.Point(-1.0, -1.0), 2)
            val gradBytes = ByteArray(w * h)
            grad.get(0, 0, gradBytes)

            var best: List<Point>? = null
            var bestScore = 0.0
            for (b in binaries) {
                for (q in candidates(b, MIN_AREA * area)) {
                    val a = polygonArea(q) / area
                    if (a > MAX_AREA) continue
                    val score = support(gradBytes, w, h, q) * (0.5 + a)
                    if (score > bestScore) {
                        best = q
                        bestScore = score
                    }
                }
            }
            if (best == null || bestScore < MIN_SCORE) return null
            val n = best.map { Point((it.x / w).coerceIn(0f, 1f), (it.y / h).coerceIn(0f, 1f)) }
            return Quad(n[0], n[1], n[2], n[3]).ordered()
        } catch (e: Exception) {
            return null
        } finally {
            mats.forEach { it.release() }
        }
    }

    private fun candidates(binary: Mat, minArea: Double): List<List<Point>> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        val out = mutableListOf<List<Point>>()
        val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }.take(10)
        for (c in sorted) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            for (eps in listOf(0.02, 0.03, 0.05)) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
                val pts = approx.toArray()
                approx.release()
                if (pts.size == 4) {
                    val asInt = MatOfPoint(*pts)
                    val convex = Imgproc.isContourConvex(asInt)
                    val a = Imgproc.contourArea(asInt)
                    asInt.release()
                    if (convex && a >= minArea) {
                        out += pts.map { Point(it.x.toFloat(), it.y.toFloat()) }
                        break
                    }
                }
            }
            c2f.release()
        }
        contours.forEach { it.release() }
        return out
    }

    /** Mean edge strength (0 to 1) sampled along the outline. */
    private fun support(grad: ByteArray, w: Int, h: Int, q: List<Point>): Double {
        var total = 0.0
        var n = 0
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val steps = max(2, hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).roundToInt())
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val x = (a.x + (b.x - a.x) * t).toInt()
                val y = (a.y + (b.y - a.y) * t).toInt()
                if (x in 0 until w && y in 0 until h) {
                    total += (grad[y * w + x].toInt() and 0xFF)
                    n++
                }
            }
        }
        return if (n == 0) 0.0 else total / n / 255.0
    }

    private fun polygonArea(q: List<Point>): Double {
        var s = 0.0
        for (i in q.indices) {
            val a = q[i]
            val b = q[(i + 1) % q.size]
            s += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return kotlin.math.abs(s) / 2
    }

    private fun median(gray: Mat): Double {
        val hist = IntArray(256)
        val bytes = ByteArray(gray.cols() * gray.rows())
        gray.get(0, 0, bytes)
        for (v in bytes) hist[v.toInt() and 0xFF]++
        var count = 0
        val half = bytes.size / 2
        for (i in 0..255) {
            count += hist[i]
            if (count >= half) return i.toDouble()
        }
        return 128.0
    }

}

package com.gaiascanner.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** Writes a measurement line that scripts/ci-device-tests.sh collects from logcat. */
object Measure {
    fun log(key: String, value: Any?) {
        Log.i("GaiaMeasure", "api=${Build.VERSION.SDK_INT} $key=$value")
    }
}

object TestFiles {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context get() = InstrumentationRegistry.getInstrumentation().context

    fun tempDir(name: String): File =
        File(context.cacheDir, "test-$name-${System.nanoTime()}").apply { mkdirs() }

    /** A PDF with one red square near the top-left of each page; the rest is blank. */
    fun makePdf(file: File, pages: List<Pair<Int, Int>>) {
        val doc = PdfDocument()
        val red = Paint().apply { color = Color.rgb(200, 30, 30) }
        try {
            pages.forEachIndexed { i, (w, h) ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                page.canvas.drawRect(50f, 50f, 300f, 300f, red)
                doc.finishPage(page)
            }
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    fun a4Pages(count: Int) = List(count) { 595 to 842 }

    fun asset(name: String, dir: File): File {
        val target = File(dir, name)
        testContext.assets.open(name).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        return target
    }

    fun solidImage(file: File, width: Int, height: Int, color: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(file).use { bitmap.compress(format, 90, it) }
        bitmap.recycle()
    }

    /**
     * A synthetic "photo": smooth gradients plus pixel noise, saved as JPEG.
     * Real camera photos compress differently, so sizes are only a rough guide.
     */
    fun photoLikeJpeg(file: File, width: Int, height: Int, seed: Int, quality: Int = 90): Long {
        val rnd = Random(seed)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val n = rnd.nextInt(-12, 13)
                val r = (x * 255 / width + n).coerceIn(0, 255)
                val g = (y * 255 / height + n).coerceIn(0, 255)
                val b = (((x + y) * 255 / (width + height)) + n + seed * 17).mod(256)
                row[x] = Color.rgb(r, g, b)
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
        return file.length()
    }
}

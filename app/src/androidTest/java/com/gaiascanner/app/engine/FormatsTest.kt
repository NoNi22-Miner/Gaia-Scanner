package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.Measure
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FormatsTest {

    @Test
    fun webpAndPngDecodeAndConvert() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("webp")
        val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val webp = File(dir, "a.webp").also { TestFiles.solidImage(it, 320, 240, Color.CYAN, webpFormat) }
        val png = File(dir, "b.png").also { TestFiles.solidImage(it, 320, 240, Color.MAGENTA, Bitmap.CompressFormat.PNG) }
        assertNotNull(ImageDecoding.readInfo(webp))
        assertNotNull(ImageDecoding.readInfo(png))
        val out = File(dir, "out.pdf")
        ImagesToPdf().convert(listOf(ImagesToPdf.Input(webp), ImagesToPdf.Input(png)), out, PageSizeOption.FIT, MarginOption.NONE)
        assertEquals(2, PdfFiles.pageCount(out))
        dir.deleteRecursively()
    }

    /** [test] item: does BitmapFactory decode HEIC on this Android version? */
    @Test
    fun measureHeicSupport() = runBlocking<Unit> {
        val dir = TestFiles.tempDir("heic")
        val heic = TestFiles.asset("sample.heic", dir)
        val info = ImageDecoding.readInfo(heic)
        Measure.log("heic_decodes", info != null)
        if (info != null) {
            assertEquals(64 to 48, info.uprightSize())
            val out = File(dir, "out.pdf")
            ImagesToPdf().convert(listOf(ImagesToPdf.Input(heic)), out, PageSizeOption.FIT, MarginOption.NONE)
            assertEquals(1, PdfFiles.pageCount(out))
        } else {
            // The app reports UNSUPPORTED_FORMAT for this file instead of crashing.
            val reason = try {
                ImagesToPdf().convert(listOf(ImagesToPdf.Input(heic)), File(dir, "x.pdf"), PageSizeOption.FIT, MarginOption.NONE)
                null
            } catch (e: ConvertException) {
                e.reason
            }
            assertTrue(reason != null)
        }
        dir.deleteRecursively()
    }
}

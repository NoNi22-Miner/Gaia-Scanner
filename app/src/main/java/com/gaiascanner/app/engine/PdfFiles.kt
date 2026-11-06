package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.gaiascanner.app.core.FailureReason
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Opening, probing and unlocking PDFs with Android's built-in PdfRenderer. */
object PdfFiles {

    val canUnlock: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /**
     * Opens [file]. Throws [ConvertException] with PASSWORD when the PDF needs a
     * password (the docs say PdfRenderer throws SecurityException for that), or
     * DAMAGED when it cannot be read. The caller closes the renderer.
     */
    fun open(file: File, password: String? = null): PdfRenderer {
        if (!file.exists()) throw ConvertException(FailureReason.MISSING_FILE)
        if (file.length() == 0L) throw ConvertException(FailureReason.DAMAGED)
        val fd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: IOException) {
            throw ConvertException(FailureReason.MISSING_FILE, e)
        }
        try {
            return if (password != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                openWithPassword(fd, password)
            } else {
                PdfRenderer(fd)
            }
        } catch (e: SecurityException) {
            closeQuietly(fd)
            val reason = if (password != null) FailureReason.WRONG_PASSWORD else FailureReason.PASSWORD
            throw ConvertException(reason, e)
        } catch (e: IOException) {
            closeQuietly(fd)
            throw ConvertException(FailureReason.DAMAGED, e)
        } catch (e: IllegalArgumentException) {
            closeQuietly(fd)
            throw ConvertException(FailureReason.DAMAGED, e)
        } catch (e: RuntimeException) {
            closeQuietly(fd)
            throw ConvertException(FailureReason.DAMAGED, e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun openWithPassword(fd: ParcelFileDescriptor, password: String): PdfRenderer =
        PdfRenderer(fd, LoadParams.Builder().setPassword(password).build())

    /** Page count, or a [ConvertException]. */
    fun pageCount(file: File): Int {
        val renderer = open(file)
        try {
            val count = renderer.pageCount
            if (count <= 0) throw ConvertException(FailureReason.DAMAGED)
            return count
        } finally {
            renderer.close()
        }
    }

    /**
     * Writes an unprotected copy of a password-protected PDF to [destination].
     * Uses PdfRenderer.write(..., removePasswordProtection = true), API 35+.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun writeUnlockedCopy(file: File, password: String, destination: File) {
        val renderer = open(file, password)
        try {
            val out = ParcelFileDescriptor.open(
                destination,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            )
            // The docs say write() closes the file descriptor it is given.
            renderer.write(out, true)
        } finally {
            renderer.close()
        }
    }

    /** First page as a small bitmap for the library, or null if it cannot be rendered. */
    fun renderFirstPage(file: File, maxWidthPx: Int): Bitmap? = try {
        val renderer = open(file)
        try {
            if (renderer.pageCount == 0) {
                null
            } else {
                val page = renderer.openPage(0)
                try {
                    val scale = maxWidthPx.toDouble() / max(1, page.width)
                    val w = max(1, (page.width * scale).roundToInt())
                    val h = max(1, (page.height * scale).roundToInt()).coerceAtMost(maxWidthPx * 4)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun closeQuietly(fd: ParcelFileDescriptor) {
        try {
            fd.close()
        } catch (_: Exception) {
        }
    }
}

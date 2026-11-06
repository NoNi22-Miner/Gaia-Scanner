package com.gaiascanner.app.engine

import android.graphics.Bitmap
import com.gaiascanner.app.core.CropRotation
import com.gaiascanner.app.core.PageEdit
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Renders a page as the user edited it: crop, rotation, erase, tone, filter. */
object PageRenderer {

    fun finalSize(info: ImageDecoding.Info, edit: PageEdit): Pair<Int, Int> =
        ImageDecoding.finalSize(info, edit.crop, edit.rotation)

    /** Full render from the photo file at [targetWidth] x [targetHeight] (final size). */
    fun render(file: File, info: ImageDecoding.Info, edit: PageEdit, targetWidth: Int, targetHeight: Int): Bitmap =
        enhance(ImageDecoding.decodeFinal(file, info, edit.crop, edit.rotation, targetWidth, targetHeight), edit)

    /**
     * Render from an upright image already in memory (the editor preview),
     * longest side at most [maxSide]. [upright] is not recycled.
     */
    fun renderFromUpright(upright: Bitmap, edit: PageEdit, maxSide: Int, withLook: Boolean = true): Bitmap {
        val (w, h) = CropRotation.finalSize(upright.width, upright.height, edit.crop, edit.rotation)
        val s = minOf(1.0, maxSide.toDouble() / max(w, h))
        val geo = ImageDecoding.warpAndRotate(upright, edit.crop, edit.rotation, max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
        return if (withLook) enhance(geo, edit) else geo
    }

    /** Thumbnail of the finished page, longest side about [maxSide], or null. */
    fun thumbnail(file: File, edit: PageEdit, maxSide: Int): Bitmap? = try {
        val info = ImageDecoding.readInfo(file)
        if (info == null) {
            null
        } else {
            val (w, h) = finalSize(info, edit)
            val s = minOf(1.0, maxSide.toDouble() / max(w, h))
            render(file, info, edit, max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
        }
    } catch (e: Exception) {
        null
    }

    /** Erase, then brightness/contrast, then filter. Recycles [bitmap] if it makes a new one. */
    fun enhance(bitmap: Bitmap, edit: PageEdit): Bitmap {
        var b = Enhancer.erase(bitmap, edit.strokes)
        b = Enhancer.tone(b, edit.tone)
        b = Enhancer.filter(b, edit.filter)
        return b
    }
}

package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.gaiascanner.app.core.CropRotation
import com.gaiascanner.app.core.ExifTransform
import com.gaiascanner.app.core.Quad
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.PageMath
import com.gaiascanner.app.core.Rotation
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes JPG, PNG, WebP and (where the phone supports it) HEIC/HEIF with
 * BitmapFactory. BitmapFactory does not apply EXIF orientation, so we do.
 */
object ImageDecoding {

    data class Info(
        /** Stored size, before EXIF orientation. */
        val width: Int,
        val height: Int,
        val exif: ExifTransform,
        val mimeType: String?,
    ) {
        /** Size after EXIF orientation and an extra user rotation. */
        fun uprightSize(userRotation: Int = 0): Pair<Int, Int> =
            Rotation.rotatedSize(width, height, exif.degrees + userRotation)
    }

    /** Size and orientation, or null if this phone cannot decode the file. */
    fun readInfo(file: File): Info? {
        if (!file.exists() || file.length() == 0L) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return Info(opts.outWidth, opts.outHeight, readExif(file), opts.outMimeType)
    }

    private fun readExif(file: File): ExifTransform = try {
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        ExifTransform.fromExif(orientation)
    } catch (e: Exception) {
        ExifTransform.NONE
    }

    /**
     * Decodes [file] upright (EXIF, then [userRotation] clockwise) and scaled to
     * [targetWidth] x [targetHeight], which are upright dimensions.
     */
    fun decodeUpright(file: File, info: Info, userRotation: Int, targetWidth: Int, targetHeight: Int): Bitmap {
        val total = Rotation.normalize(info.exif.degrees + userRotation)
        // The same size in the file's stored orientation.
        val (reqWidth, reqHeight) = Rotation.rotatedSize(targetWidth, targetHeight, total)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PageMath.sampleSize(info.width, info.height, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (e: OutOfMemoryError) {
            throw ConvertException(FailureReason.TOO_LARGE, e)
        } ?: throw ConvertException(FailureReason.UNSUPPORTED_FORMAT)

        val matrix = Matrix()
        if (decoded.width != reqWidth || decoded.height != reqHeight) {
            matrix.postScale(reqWidth / decoded.width.toFloat(), reqHeight / decoded.height.toFloat())
        }
        // EXIF: rotate, then mirror left-right. Then the user's rotation.
        if (info.exif.degrees != 0) matrix.postRotate(info.exif.degrees.toFloat())
        if (info.exif.flip) matrix.postScale(-1f, 1f)
        val user = Rotation.normalize(userRotation)
        if (user != 0) matrix.postRotate(user.toFloat())
        if (matrix.isIdentity) return decoded

        val result = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            decoded.recycle()
            throw ConvertException(FailureReason.TOO_LARGE, e)
        }
        if (result !== decoded) decoded.recycle()
        return result
    }

    /** Final size of a page image: upright, then [crop], then [userRotation]. */
    fun finalSize(info: Info, crop: Quad?, userRotation: Int): Pair<Int, Int> {
        val (w, h) = info.uprightSize()
        return CropRotation.finalSize(w, h, crop, userRotation)
    }

    /**
     * Decodes a page image as it should appear: upright (EXIF), then cropped and
     * straightened with [crop], then turned by [userRotation]. [targetWidth] x
     * [targetHeight] is the final size and should keep the aspect of [finalSize].
     */
    fun decodeFinal(file: File, info: Info, crop: Quad?, userRotation: Int, targetWidth: Int, targetHeight: Int): Bitmap {
        if (crop == null || crop.isFullFrame) {
            return decodeUpright(file, info, userRotation, targetWidth, targetHeight)
        }
        val (uw, uh) = info.uprightSize()
        val (finalW, _) = finalSize(info, crop, userRotation)
        val scale = targetWidth / finalW.toDouble()
        // Decode the whole upright image at the scale the cropped part needs.
        val upright = decodeUpright(
            file,
            info,
            0,
            max(1, (uw * scale).roundToInt()),
            max(1, (uh * scale).roundToInt()),
        )
        // Cropped size before the user's rotation.
        val (outW, outH) = Rotation.rotatedSize(targetWidth, targetHeight, userRotation)
        val warped = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            upright.recycle()
            throw ConvertException(FailureReason.TOO_LARGE, e)
        }
        val sw = upright.width.toFloat()
        val sh = upright.height.toFloat()
        val src = crop.corners.flatMap { listOf(it.x * sw, it.y * sh) }.toFloatArray()
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val matrix = Matrix()
        // Maps the four crop corners onto the corners of the output: a perspective straighten.
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            warped.recycle()
            upright.recycle()
            throw ConvertException(FailureReason.UNKNOWN)
        }
        Canvas(warped).drawBitmap(upright, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        upright.recycle()

        val user = Rotation.normalize(userRotation)
        if (user == 0) return warped
        val turned = try {
            Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, Matrix().apply { postRotate(user.toFloat()) }, true)
        } catch (e: OutOfMemoryError) {
            warped.recycle()
            throw ConvertException(FailureReason.TOO_LARGE, e)
        }
        if (turned !== warped) warped.recycle()
        return turned
    }

    /** Thumbnail as the page will appear, longest side about [maxSide] pixels, or null. */
    fun decodeThumbnail(file: File, maxSide: Int, userRotation: Int, crop: Quad? = null): Bitmap? = try {
        val info = readInfo(file)
        if (info == null) {
            null
        } else {
            val (w, h) = finalSize(info, crop, userRotation)
            val scale = minOf(1.0, maxSide.toDouble() / max(w, h))
            decodeFinal(
                file,
                info,
                crop,
                userRotation,
                max(1, (w * scale).roundToInt()),
                max(1, (h * scale).roundToInt()),
            )
        }
    } catch (e: Exception) {
        null
    }
}

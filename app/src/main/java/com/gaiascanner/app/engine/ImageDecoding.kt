package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.gaiascanner.app.core.ExifTransform
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

    /** Upright thumbnail whose longest side is about [maxSide] pixels, or null. */
    fun decodeThumbnail(file: File, maxSide: Int, userRotation: Int): Bitmap? = try {
        val info = readInfo(file)
        if (info == null) {
            null
        } else {
            val (w, h) = info.uprightSize(userRotation)
            val scale = minOf(1.0, maxSide.toDouble() / max(w, h))
            decodeUpright(
                file,
                info,
                userRotation,
                max(1, (w * scale).roundToInt()),
                max(1, (h * scale).roundToInt()),
            )
        }
    } catch (e: Exception) {
        null
    }
}

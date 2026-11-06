package com.gaiascanner.app.engine

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Loads OpenCV's native library once, on first use. */
object OpenCv {
    val ready: Boolean by lazy {
        try {
            OpenCVLoader.initLocal().also { if (!it) Log.w(TAG, "OpenCV did not load") }
        } catch (e: Throwable) {
            Log.w(TAG, "OpenCV did not load", e)
            false
        }
    }

    /** An ARGB_8888 bitmap as a 3-channel RGB Mat. The caller releases it. */
    fun toRgb(bitmap: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()
        return rgb
    }

    /** A 3-channel RGB (or 1-channel gray) Mat as a new ARGB_8888 bitmap. */
    fun toBitmap(mat: Mat): Bitmap {
        val rgba = Mat()
        Imgproc.cvtColor(mat, rgba, if (mat.channels() == 1) Imgproc.COLOR_GRAY2RGBA else Imgproc.COLOR_RGB2RGBA)
        val out = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, out)
        rgba.release()
        return out
    }

    private const val TAG = "GaiaOpenCv"
}

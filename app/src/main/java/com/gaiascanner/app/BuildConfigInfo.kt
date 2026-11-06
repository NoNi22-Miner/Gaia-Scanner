package com.gaiascanner.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Version name from the package manager, so the build does not need BuildConfig. */
object BuildConfigInfo {
    fun versionName(context: Context): String = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}

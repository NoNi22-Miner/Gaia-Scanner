package com.gaiascanner.app.ui.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.gaiascanner.app.AppInfo
import com.gaiascanner.app.BuildConfigInfo
import com.gaiascanner.app.R

/** Opens the support chat or email. Shows a short message if no app can handle it. */
object Support {

    fun openTelegram(context: Context) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.TELEGRAM_URL)))
    }

    fun sendEmail(context: Context) {
        val version = BuildConfigInfo.versionName(context)
        val body = context.getString(
            R.string.feedback_email_body,
            version,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(AppInfo.SUPPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_email_subject, AppInfo.APP_NAME, version))
            .putExtra(Intent.EXTRA_TEXT, body)
        start(context, intent)
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_LONG).show()
        }
    }
}

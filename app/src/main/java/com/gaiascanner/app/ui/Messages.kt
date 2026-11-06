package com.gaiascanner.app.ui

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import com.gaiascanner.app.R
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.data.LibraryRepository

@StringRes
fun messageFor(reason: FailureReason): Int = when (reason) {
    FailureReason.PASSWORD -> R.string.error_password
    FailureReason.WRONG_PASSWORD -> R.string.error_wrong_password
    FailureReason.DAMAGED -> R.string.error_damaged
    FailureReason.UNSUPPORTED_FORMAT -> R.string.error_unsupported_format
    FailureReason.OUT_OF_SPACE -> R.string.error_out_of_space
    FailureReason.TOO_LARGE -> R.string.error_too_large
    FailureReason.MISSING_FILE -> R.string.error_missing_file
    FailureReason.UNKNOWN -> R.string.error_unknown
}

/** One snackbar line that sums up an import. */
fun importSummary(context: Context, result: LibraryRepository.ImportResult): String {
    val ok = context.resources.getQuantityString(R.plurals.imported_files, result.imported, result.imported)
    if (result.failures.isEmpty()) return ok
    val first = result.failures.first()
    val why = context.getString(R.string.failure_line, first.name, context.getString(messageFor(first.reason)))
    val more = result.failures.size - 1
    return if (more > 0) {
        context.getString(R.string.import_with_failures_more, ok, why, more)
    } else {
        context.getString(R.string.import_with_failures, ok, why)
    }
}

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

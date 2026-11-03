package com.gaiascanner.app.engine

import com.gaiascanner.app.core.FailureReason
import java.io.FileNotFoundException
import java.io.IOException

/** A failure with a reason the user can understand. */
class ConvertException(val reason: FailureReason, cause: Throwable? = null) :
    Exception(reason.name, cause)

object Failures {
    fun reasonOf(t: Throwable): FailureReason = when {
        t is ConvertException -> t.reason
        isNoSpace(t) -> FailureReason.OUT_OF_SPACE
        t is OutOfMemoryError -> FailureReason.TOO_LARGE
        t is FileNotFoundException -> FailureReason.MISSING_FILE
        t is IOException -> FailureReason.DAMAGED
        else -> FailureReason.UNKNOWN
    }

    private fun isNoSpace(t: Throwable): Boolean {
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 8) {
            val msg = current.message.orEmpty()
            if ("ENOSPC" in msg || "No space left" in msg) return true
            current = current.cause
            depth++
        }
        return false
    }
}

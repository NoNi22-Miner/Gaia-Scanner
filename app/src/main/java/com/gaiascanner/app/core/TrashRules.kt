package com.gaiascanner.app.core

/** Trash keeps items for 30 days, then they are deleted for good (owner decision, phase 3). */
object TrashRules {
    const val KEEP_DAYS = 30
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val KEEP_MS = KEEP_DAYS * DAY_MS

    /** Whole days until the item is deleted for good: 30 right after trashing, 0 when due. */
    fun daysLeft(trashedAt: Long, now: Long): Int {
        val left = trashedAt + KEEP_MS - now
        if (left <= 0) return 0
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtMost(KEEP_DAYS)
    }
}

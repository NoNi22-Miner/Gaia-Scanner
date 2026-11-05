package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashRulesTest {
    private val day = TrashRules.DAY_MS

    @Test
    fun daysLeftCountsDownFromThirty() {
        assertEquals(30, TrashRules.daysLeft(trashedAt = 0, now = 0))
        assertEquals(30, TrashRules.daysLeft(trashedAt = 0, now = day / 2))
        assertEquals(29, TrashRules.daysLeft(trashedAt = 0, now = day + 1))
        assertEquals(1, TrashRules.daysLeft(trashedAt = 0, now = 29 * day + 1))
        assertEquals(0, TrashRules.daysLeft(trashedAt = 0, now = 30 * day))
        assertEquals(0, TrashRules.daysLeft(trashedAt = 0, now = 40 * day))
    }

    @Test
    fun clockSetBackDoesNotGoAboveThirty() {
        assertEquals(30, TrashRules.daysLeft(trashedAt = 10 * day, now = 0))
    }
}

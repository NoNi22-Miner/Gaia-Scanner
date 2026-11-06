package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOrderTest {

    private data class Item(val name: String, val created: Long, val modified: Long, val size: Long)

    private val items = listOf(
        Item("Scan 10", created = 1, modified = 30, size = 500),
        Item("scan 2", created = 3, modified = 10, size = 100),
        Item("Invoice", created = 2, modified = 20, size = 100),
    )

    private fun sorted(field: SortField, ascending: Boolean) =
        LibraryOrder.sort(items, LibrarySort(field, ascending)) { SortKey(it.name, it.created, it.modified, it.size) }
            .map { it.name }

    @Test
    fun nameSortIsNaturalAndIgnoresCase() {
        assertEquals(listOf("Invoice", "scan 2", "Scan 10"), sorted(SortField.NAME, ascending = true))
        assertEquals(listOf("Scan 10", "scan 2", "Invoice"), sorted(SortField.NAME, ascending = false))
    }

    @Test
    fun dateSorts() {
        assertEquals(listOf("Scan 10", "Invoice", "scan 2"), sorted(SortField.CREATED, ascending = true))
        assertEquals(listOf("Scan 10", "Invoice", "scan 2"), sorted(SortField.MODIFIED, ascending = false))
    }

    @Test
    fun sizeTiesFallBackToNameAToZ() {
        assertEquals(listOf("Invoice", "scan 2", "Scan 10"), sorted(SortField.SIZE, ascending = true))
        assertEquals(listOf("Scan 10", "Invoice", "scan 2"), sorted(SortField.SIZE, ascending = false))
    }

    @Test
    fun naturalOrderHandlesLeadingZerosAndPrefixes() {
        val names = listOf("a10", "a02", "a", "a1", "b")
        assertEquals(listOf("a", "a1", "a02", "a10", "b"), names.sortedWith(LibraryOrder.naturalNameOrder))
    }

    @Test
    fun searchMatchesAllWordsIgnoringCase() {
        assertTrue(LibraryOrder.matches("Tax Return 2026", "return tax"))
        assertTrue(LibraryOrder.matches("anything", "  "))
        assertFalse(LibraryOrder.matches("Tax Return", "invoice"))
    }

    @Test
    fun pathGoesFromRootDownAndSurvivesLoops() {
        data class F(val id: Long, val parent: Long?)
        val byId = mapOf(1L to F(1, null), 2L to F(2, 1), 3L to F(3, 2))
        assertEquals(listOf(1L, 2L, 3L), LibraryOrder.pathTo(3L, byId) { it.parent }.map { it.id })
        assertEquals(emptyList<F>(), LibraryOrder.pathTo(null, byId) { it.parent })
        val loop = mapOf(1L to F(1, 2), 2L to F(2, 1))
        assertEquals(listOf(1L, 2L), LibraryOrder.pathTo(2L, loop) { it.parent }.map { it.id })
    }

    @Test
    fun treeListsParentsBeforeChildrenWithDepth() {
        data class F(val id: Long, val parent: Long?, val name: String)
        val folders = listOf(F(3, 1, "b"), F(1, null, "Work"), F(2, 1, "a"), F(4, null, "Home"), F(5, 99, "orphan"))
        val tree = LibraryOrder.flattenTree(folders, { it.id }, { it.parent }, { it.name })
        assertEquals(listOf("Home" to 0, "orphan" to 0, "Work" to 0, "a" to 1, "b" to 1), tree.map { it.first.name to it.second })
    }
}

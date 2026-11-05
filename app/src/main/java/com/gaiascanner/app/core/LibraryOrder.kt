package com.gaiascanner.app.core

/** What the library sorts by. One choice for the whole library (owner decision, phase 3). */
enum class SortField { NAME, CREATED, MODIFIED, SIZE }

enum class LibraryView { LIST, GRID }

data class LibrarySort(val field: SortField = SortField.MODIFIED, val ascending: Boolean = false)

/** The values a library item is sorted by. Folders have size 0. */
data class SortKey(val name: String, val created: Long, val modified: Long, val size: Long)

object LibraryOrder {

    /** Case-insensitive name order where digit runs compare as numbers: "Scan 2" before "Scan 10". */
    val naturalNameOrder: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

    /** Sorts by [sort]. Ties fall back to name (A to Z), so the order is stable and readable. */
    fun <T> sort(items: List<T>, sort: LibrarySort, key: (T) -> SortKey): List<T> {
        val primary: Comparator<SortKey> = when (sort.field) {
            SortField.NAME -> Comparator { a, b -> compareNatural(a.name, b.name) }
            SortField.CREATED -> compareBy { it.created }
            SortField.MODIFIED -> compareBy { it.modified }
            SortField.SIZE -> compareBy { it.size }
        }
        val directed = if (sort.ascending) primary else primary.reversed()
        val withTie = directed.then { a, b -> compareNatural(a.name, b.name) }
        return items.sortedWith { a, b -> withTie.compare(key(a), key(b)) }
    }

    /** True if every word of [query] appears in [name], ignoring case. A blank query matches everything. */
    fun matches(name: String, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lower = name.lowercase()
        return words.all { lower.contains(it) }
    }

    /**
     * Folders from the root down to [id] (inclusive). Empty for the root or an unknown id.
     * Stops on a loop, so damaged data cannot hang the app.
     */
    fun <T> pathTo(id: Long?, byId: Map<Long, T>, parentOf: (T) -> Long?): List<T> {
        val path = ArrayList<T>()
        val seen = HashSet<Long>()
        var current = id
        while (current != null && seen.add(current)) {
            val item = byId[current] ?: break
            path += item
            current = parentOf(item)
        }
        return path.asReversed().toList()
    }

    private fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

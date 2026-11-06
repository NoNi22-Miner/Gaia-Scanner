package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FileNamesTest {

    @Test
    fun baseNameDropsExtensionAndIllegalCharacters() {
        assertEquals("report", FileNames.baseName("report.pdf"))
        assertEquals("a_b_c", FileNames.baseName("a/b:c.jpg"))
        assertEquals("archive.tar", FileNames.baseName("archive.tar.gz"))
        assertEquals("hidden", FileNames.baseName(".hidden"))
        assertEquals("Untitled", FileNames.baseName(null))
        assertEquals("Untitled", FileNames.baseName("  "))
        assertEquals("Untitled", FileNames.baseName("..."))
    }

    @Test
    fun baseNameIsShortened() {
        assertEquals(80, FileNames.baseName("x".repeat(200) + ".pdf").length)
    }

    @Test
    fun extensionIsLowerCase() {
        assertEquals("jpg", FileNames.extension("IMG_1.JPG"))
        assertEquals("heic", FileNames.extension("photo.heic"))
        assertEquals("", FileNames.extension("noext"))
        assertEquals("", FileNames.extension(".hidden"))
        assertEquals("", FileNames.extension("dot."))
    }

    @Test
    fun pageImageNamesArePadded() {
        assertEquals("doc_p001.jpg", FileNames.pageImageName("doc", 0, 12, "jpg"))
        assertEquals("doc_p100.png", FileNames.pageImageName("doc", 99, 100, "png"))
        assertEquals("doc_p0001.jpg", FileNames.pageImageName("doc", 0, 1200, "jpg"))
    }

    @Test
    fun pdfNameHasTimestamp() {
        val time = LocalDateTime.of(2026, 11, 3, 14, 5)
        assertEquals("scan_20261103-1405.pdf", FileNames.pdfName("scan", time))
    }

    @Test
    fun uniqueAddsCounterBeforeExtension() {
        val taken = setOf("a.pdf", "a (2).pdf")
        assertEquals("b.pdf", FileNames.unique("b.pdf") { it in taken })
        assertEquals("a (3).pdf", FileNames.unique("a.pdf") { it in taken })
        assertEquals("folder (2)", FileNames.unique("folder") { it == "folder" })
    }
}

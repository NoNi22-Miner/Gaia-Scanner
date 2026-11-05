package com.gaiascanner.app.core

import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Writes a PDF whose pages each show one JPEG image.
 *
 * The JPEG bytes go into the file unchanged (PDF's DCTDecode filter), so the PDF
 * is about as large as the JPEGs. Each page is written as soon as it is added,
 * so memory does not grow with the page count.
 *
 * Android's PdfDocument was measured to store images uncompressed, which made
 * photo PDFs 2.5 to 5 times larger than the photos. See CLAUDE.md.
 *
 * Plain Kotlin and java.io only, so it runs in JVM unit tests.
 */
class JpegPdfWriter(output: OutputStream, private val producer: String = "Gaia Scanner") : Closeable {

    private val out = CountingStream(output)
    /** Byte offset of each object, by object number (index 0 unused). */
    private val offsets = mutableListOf(0L)
    private val pageRefs = mutableListOf<Int>()
    private var finished = false

    init {
        write("%PDF-1.4\n")
        // A comment with bytes above 127 tells tools the file is binary.
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
        // Object 1 is the catalog and 2 the page tree. Both are written in finish().
        offsets += 0L
        offsets += 0L
    }

    val pageCount: Int get() = pageRefs.size

    /**
     * Adds a page of [pageWidth] x [pageHeight] points showing [jpeg]
     * ([imageWidth] x [imageHeight] pixels) inside [box]. [box] uses a top-left
     * origin in points, like [PageMath.fitInside].
     */
    fun addPage(
        pageWidth: Int,
        pageHeight: Int,
        jpeg: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        box: Box,
        components: Int = 3,
    ) {
        check(!finished) { "Writer already finished" }
        require(pageWidth > 0 && pageHeight > 0) { "Page size must be positive" }
        require(imageWidth > 0 && imageHeight > 0) { "Image size must be positive" }
        require(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "Not JPEG data" }
        val colorSpace = when (components) {
            1 -> "/DeviceGray"
            3 -> "/DeviceRGB"
            else -> throw IllegalArgumentException("Unsupported JPEG components: $components")
        }

        val imageRef = beginObject()
        write(
            "<< /Type /XObject /Subtype /Image /Width $imageWidth /Height $imageHeight " +
                "/ColorSpace $colorSpace /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n",
        )
        out.write(jpeg)
        write("\nendstream\nendobj\n")

        // PDF puts the origin at the bottom-left; the box is measured from the top.
        val y = pageHeight - box.top - box.height
        val content = "q ${num(box.width)} 0 0 ${num(box.height)} ${num(box.left)} ${num(y)} cm /Im0 Do Q\n"
            .toByteArray(Charsets.US_ASCII)
        val contentRef = beginObject()
        write("<< /Length ${content.size} >>\nstream\n")
        out.write(content)
        write("\nendstream\nendobj\n")

        val pageRef = beginObject()
        write(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] " +
                "/Resources << /XObject << /Im0 $imageRef 0 R >> >> /Contents $contentRef 0 R >>\nendobj\n",
        )
        pageRefs += pageRef
    }

    /** Writes the page tree, cross-reference table and trailer. Needs at least one page. */
    fun finish() {
        if (finished) return
        check(pageRefs.isNotEmpty()) { "A PDF needs at least one page" }
        finished = true

        offsets[1] = out.count
        write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offsets[2] = out.count
        write("2 0 obj\n<< /Type /Pages /Kids [${pageRefs.joinToString(" ") { "$it 0 R" }}] /Count ${pageRefs.size} >>\nendobj\n")
        val infoRef = beginObject()
        write("<< /Producer (${escape(producer)}) >>\nendobj\n")

        val xrefOffset = out.count
        val size = offsets.size
        val xref = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (i in 1 until size) xref.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]))
        write(xref.toString())
        write("trailer\n<< /Size $size /Root 1 0 R /Info $infoRef 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.flush()
    }

    override fun close() {
        out.close()
    }

    private fun beginObject(): Int {
        val number = offsets.size
        offsets += out.count
        write("$number 0 obj\n")
        return number
    }

    private fun write(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))

    private class CountingStream(target: OutputStream) : FilterOutputStream(target) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    companion object {
        /** Number for a content stream: no exponent, at most 3 decimals. */
        fun num(value: Float): String {
            val rounded = Math.round(value * 1000.0) / 1000.0
            if (rounded == Math.rint(rounded)) return rounded.toLong().toString()
            return String.format(Locale.ROOT, "%.3f", rounded).trimEnd('0').trimEnd('.')
        }

        private fun escape(text: String): String =
            text.filter { it.code in 32..126 }.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }
}

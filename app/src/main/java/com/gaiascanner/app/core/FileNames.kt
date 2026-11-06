package com.gaiascanner.app.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FileNames {
    private const val MAX_BASE_LENGTH = 80
    private const val FALLBACK = "Untitled"
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** File name without its extension, safe to use on any file system. */
    fun baseName(displayName: String?): String {
        val raw = displayName?.trim().orEmpty()
        val noExt = raw.substringBeforeLast('.', raw).ifBlank { raw }
        val cleaned = ILLEGAL.replace(noExt, "_").trim().trim('.').trim()
        return cleaned.take(MAX_BASE_LENGTH).trim().ifEmpty { FALLBACK }
    }

    /** Extension in lower case, without the dot, or "" if none. */
    fun extension(displayName: String?): String {
        val name = displayName?.trim().orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** "report_p001.jpg" for page index 0. Padding grows past 999 pages. */
    fun pageImageName(base: String, pageIndex: Int, pageCount: Int, extension: String): String {
        val digits = maxOf(3, pageCount.toString().length)
        val number = (pageIndex + 1).toString().padStart(digits, '0')
        return "${base}_p$number.$extension"
    }

    fun stamp(time: LocalDateTime): String = time.format(STAMP)

    /** "scan_20261103-1405.pdf" */
    fun pdfName(base: String, time: LocalDateTime): String = "${base}_${stamp(time)}.pdf"

    /** Adds " (2)", " (3)", ... before the extension until [exists] returns false. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (!exists(candidate)) return candidate
            n++
        }
    }
}

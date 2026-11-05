package com.gaiascanner.app.core

// Plain Kotlin only in this package (no Android imports), so it runs in JVM unit tests.

enum class ImageFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
}

enum class PageSizeOption {
    /** Page takes the image's shape, long side [PageMath.FIT_LONG_SIDE_PT]. */
    FIT,
    A4,
    LETTER,
}

/** Margins in PostScript points (1/72 inch). */
enum class MarginOption(val points: Int) {
    NONE(0),
    SMALL(18),
    LARGE(36),
}

object ConvertDefaults {
    val DPI_CHOICES = listOf(72, 150, 200, 300, 600)
    const val DPI = 150
    const val JPEG_QUALITY = 90

    /** Largest bitmap rendered for one PDF page: 16 MP, about 64 MB as ARGB_8888. */
    const val MAX_PAGE_PIXELS = 16_000_000L

    /** Images placed in a PDF are downscaled to at most this resolution on the page. */
    const val PDF_IMAGE_DPI = 300
}

enum class JobType { PDF_TO_IMAGES, IMAGES_TO_PDF }

enum class JobStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    DONE,
    FAILED,
    CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == CANCEL_REQUESTED
}

enum class DocumentKind { PDF, IMAGES }

/** Why a file could not be converted or imported. Shown to the user in plain words. */
enum class FailureReason {
    PASSWORD,
    WRONG_PASSWORD,
    DAMAGED,
    UNSUPPORTED_FORMAT,
    OUT_OF_SPACE,
    TOO_LARGE,
    MISSING_FILE,
    UNKNOWN,
}

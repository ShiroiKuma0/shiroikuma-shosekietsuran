package com.aryan.reader.whitebear

/**
 * Extra embedded book metadata read live from the file when the details dialog opens
 * (publisher / language / publication date / rating / ISBN). Kept out of the Room entity
 * so no schema migration is needed; publication date is the only editable one and is
 * written back into the EPUB file.
 */
data class WhiteBearExtraMetadata(
    val publisher: String? = null,
    val language: String? = null,
    val publicationDate: String? = null,
    /** Calibre rating, 0–10 (i.e. stars × 2). */
    val rating: Double? = null,
    val isbn: String? = null
) {
    val hasAny: Boolean
        get() = publisher != null || language != null || publicationDate != null ||
            rating != null || isbn != null
}

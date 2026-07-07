package com.aryan.reader.data

data class BookMetadataEdit(
    val title: String?,
    val author: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
    val description: String?,
    val coverImageUri: String? = null,
    val restoreOriginalCover: Boolean = false,
    val publicationDate: String? = null,
    /** Library tags, written back to the file as EPUB dc:subject / PDF Keywords. */
    val tags: List<String>? = null
)
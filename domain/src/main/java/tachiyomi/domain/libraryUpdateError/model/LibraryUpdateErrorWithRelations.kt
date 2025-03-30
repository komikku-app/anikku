package tachiyomi.domain.libraryUpdateError.model

import tachiyomi.domain.manga.model.MangaCover

data class LibraryUpdateErrorWithRelations(
    val animeId: Long,
    val animeTitle: String,
    val animeSource: Long,
    val mangaCover: MangaCover,
    val errorId: Long,
    val messageId: Long,
)

package tachiyomi.data.libraryUpdateError

import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.manga.model.MangaCover

val libraryUpdateErrorWithRelationsMapper:
    (Long, String, Long, Boolean, String?, Long, Long, Long) -> LibraryUpdateErrorWithRelations =
    { animeId, animeTitle, animeSource, favorite, animeThumbnail, coverLastModified, errorId, messageId ->
        LibraryUpdateErrorWithRelations(
            animeId = animeId,
            animeTitle = animeTitle,
            animeSource = animeSource,
            mangaCover = MangaCover(
                animeId = animeId,
                sourceId = animeSource,
                isAnimeFavorite = favorite,
                ogUrl = animeThumbnail,
                lastModified = coverLastModified,
            ),
            errorId = errorId,
            messageId = messageId,
        )
    }

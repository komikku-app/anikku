package eu.kanade.tachiyomi.data.database.models

typealias Episode = Chapter

fun Episode.toDomainEpisode() = toDomainChapter()

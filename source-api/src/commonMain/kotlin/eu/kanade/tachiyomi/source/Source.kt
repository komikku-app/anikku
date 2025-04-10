package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

typealias Source = eu.kanade.tachiyomi.animesource.AnimeSource

suspend fun Source.getMangaDetails(manga: SManga): SManga = getAnimeDetails(manga)
suspend fun Source.getChapterList(manga: SManga): List<SChapter> = getEpisodeList(manga)

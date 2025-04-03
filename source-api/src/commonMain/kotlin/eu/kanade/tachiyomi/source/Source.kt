package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.SManga

typealias Source = eu.kanade.tachiyomi.animesource.AnimeSource

suspend fun Source.getMangaDetails(manga: SManga) = getAnimeDetails(manga)
suspend fun Source.getChapterList(manga: SManga) = getEpisodeList(manga)

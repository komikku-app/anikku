package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.animesource.online.ResolvableAnimeSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

typealias ResolvableSource = ResolvableAnimeSource

suspend fun ResolvableSource.getManga(uri: String): SManga? = getAnime(uri)
suspend fun ResolvableSource.getChapter(uri: String): SChapter? = getEpisode(uri)

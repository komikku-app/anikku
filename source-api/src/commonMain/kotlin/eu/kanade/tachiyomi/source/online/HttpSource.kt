package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

typealias HttpSource = eu.kanade.tachiyomi.animesource.online.AnimeHttpSource

fun HttpSource.prepareNewChapter(chapter: SChapter, manga: SManga) = prepareNewEpisode(chapter, manga)

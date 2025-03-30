package tachiyomi.domain.episode.service

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.chapter.service.getChapterSort

fun getEpisodeSort(
    anime: Anime,
    sortDescending: Boolean = anime.sortDescending(),
) = getChapterSort(anime, sortDescending)

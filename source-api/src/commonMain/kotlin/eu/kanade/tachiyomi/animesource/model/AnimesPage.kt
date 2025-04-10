package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.source.model.SManga

data class AnimesPage(val animes: List<SAnime>, val hasNextPage: Boolean) {
    val mangas: List<SManga>
        get() = animes
}

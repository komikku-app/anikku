package eu.kanade.domain.episode.model

import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.chapter.model.toSChapter
import tachiyomi.domain.episode.model.Episode

fun Episode.toSEpisode() = toSChapter()
fun Episode.toDbEpisode() = toDbChapter()

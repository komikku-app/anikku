package tachiyomi.domain.episode.model

import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.toChapterUpdate

typealias EpisodeUpdate = ChapterUpdate
fun Episode.toEpisodeUpdate() = toChapterUpdate()

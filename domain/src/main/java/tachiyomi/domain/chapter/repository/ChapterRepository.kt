package tachiyomi.domain.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate

interface ChapterRepository {

    suspend fun addAll(chapters: List<Chapter>): List<Chapter>

    suspend fun update(chapterUpdate: ChapterUpdate)

    suspend fun updateAll(chapterUpdates: List<ChapterUpdate>)

    suspend fun removeEpisodesWithIds(episodeIds: List<Long>)

    suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean = false): List<Chapter>

    suspend fun getScanlatorsByAnimeId(animeId: Long): List<String>

    fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>>

    suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Chapter>

    // AM (FILLERMARK) -->
    suspend fun getFillermarkedEpisodesByAnimeId(animeId: Long): List<Chapter>
    // <-- AM (FILLERMARK)

    suspend fun getEpisodeById(id: Long): Chapter?

    suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean = false): Flow<List<Chapter>>

    suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Chapter?

    // SY -->
    suspend fun getEpisodeByUrl(url: String): List<Chapter>

    suspend fun getMergedEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean = false): List<Chapter>

    suspend fun getMergedEpisodeByAnimeIdAsFlow(
        animeId: Long,
        applyScanlatorFilter: Boolean = false,
    ): Flow<List<Chapter>>

    suspend fun getScanlatorsByMergeId(animeId: Long): List<String>

    fun getScanlatorsByMergeIdAsFlow(animeId: Long): Flow<List<String>>
    // SY <--
}

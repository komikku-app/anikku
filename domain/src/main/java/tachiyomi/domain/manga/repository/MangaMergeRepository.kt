package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference

interface MangaMergeRepository {
    suspend fun getMergedAnime(): List<Manga>

    suspend fun subscribeMergedAnime(): Flow<List<Manga>>

    suspend fun getMergedAnimeById(id: Long): List<Manga>

    suspend fun subscribeMergedAnimeById(id: Long): Flow<List<Manga>>

    suspend fun getReferencesById(id: Long): List<MergedMangaReference>

    suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>>

    suspend fun updateSettings(update: MergeMangaSettingsUpdate): Boolean

    suspend fun updateAllSettings(values: List<MergeMangaSettingsUpdate>): Boolean

    suspend fun insert(reference: MergedMangaReference): Long?

    suspend fun insertAll(references: List<MergedMangaReference>)

    suspend fun deleteById(id: Long)

    suspend fun deleteByMergeId(mergeId: Long)

    suspend fun getMergeAnimeForDownloading(mergeId: Long): List<Manga>
}

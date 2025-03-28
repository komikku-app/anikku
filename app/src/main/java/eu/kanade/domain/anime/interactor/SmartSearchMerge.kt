package eu.kanade.domain.anime.interactor

import android.app.Application
import eu.kanade.domain.anime.model.copyFrom
import eu.kanade.domain.anime.model.toSAnime
import exh.source.MERGED_SOURCE_ID
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.anime.interactor.DeleteAnimeById
import tachiyomi.domain.anime.interactor.DeleteByMergeId
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetMergedReferencesById
import tachiyomi.domain.anime.interactor.InsertMergedReference
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.MergedAnimeReference
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartSearchMerge(
    private val getAnime: GetAnime = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    private val insertMergedReference: InsertMergedReference = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val deleteAnimeById: DeleteAnimeById = Injekt.get(),
    private val deleteByMergeId: DeleteByMergeId = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
) {
    suspend fun smartSearchMerge(anime: Anime, originalAnimeId: Long): Anime {
        // KMK -->
        val context = Injekt.get<Application>()
        // KMK <--
        val originalAnime = getAnime.await(originalAnimeId)
            ?: throw IllegalArgumentException(context.stringResource(SYMR.strings.merge_unknown_entry, originalAnimeId))
        if (originalAnime.source == MERGED_SOURCE_ID) {
            val children = getMergedReferencesById.await(originalAnimeId)
            if (children.any { it.animeSourceId == anime.source && it.animeUrl == anime.url }) {
                // Merged already
                return originalAnime
            }

            val animeReferences = mutableListOf(
                MergedAnimeReference(
                    id = -1,
                    isInfoAnime = false,
                    getEpisodeUpdates = true,
                    episodeSortMode = 0,
                    episodePriority = 0,
                    downloadEpisodes = true,
                    mergeId = originalAnime.id,
                    mergeUrl = originalAnime.url,
                    animeId = anime.id,
                    animeUrl = anime.url,
                    animeSourceId = anime.source,
                ),
            )

            if (children.isEmpty() || children.all { it.animeSourceId != MERGED_SOURCE_ID }) {
                animeReferences += MergedAnimeReference(
                    id = -1,
                    isInfoAnime = false,
                    getEpisodeUpdates = false,
                    episodeSortMode = 0,
                    episodePriority = -1,
                    downloadEpisodes = false,
                    mergeId = originalAnime.id,
                    mergeUrl = originalAnime.url,
                    animeId = originalAnime.id,
                    animeUrl = originalAnime.url,
                    animeSourceId = MERGED_SOURCE_ID,
                )
            }

            // todo
            insertMergedReference.awaitAll(animeReferences)

            return originalAnime
        } else {
            if (anime.id == originalAnimeId) {
                // Merged already
                return originalAnime
            }
            var mergedAnime = Anime.create()
                .copy(
                    url = originalAnime.url,
                    ogTitle = originalAnime.title,
                    source = MERGED_SOURCE_ID,
                )
                .copyFrom(originalAnime.toSAnime())
                .copy(
                    favorite = true,
                    lastUpdate = originalAnime.lastUpdate,
                    viewerFlags = originalAnime.viewerFlags,
                    episodeFlags = originalAnime.episodeFlags,
                    dateAdded = System.currentTimeMillis(),
                )

            var existingAnime = getAnime.await(mergedAnime.url, mergedAnime.source)
            while (existingAnime != null) {
                if (existingAnime.favorite) {
                    // Duplicate entry found -> use it instead
                    mergedAnime = existingAnime
                    break
                } else {
                    withNonCancellableContext {
                        existingAnime?.id?.let {
                            deleteByMergeId.await(it)
                            deleteAnimeById.await(it)
                        }
                    }
                }
                // Remove previously merged entry from database (user already removed from favorites)
                existingAnime = getAnime.await(mergedAnime.url, mergedAnime.source)
            }

            mergedAnime = networkToLocalAnime.await(mergedAnime)

            getCategories.await(originalAnimeId)
                .let { categories ->
                    setAnimeCategories.await(mergedAnime.id, categories.map { it.id })
                }

            val originalAnimeReference = MergedAnimeReference(
                id = -1,
                isInfoAnime = true,
                getEpisodeUpdates = true,
                episodeSortMode = 0,
                episodePriority = 0,
                downloadEpisodes = true,
                mergeId = mergedAnime.id,
                mergeUrl = mergedAnime.url,
                animeId = originalAnime.id,
                animeUrl = originalAnime.url,
                animeSourceId = originalAnime.source,
            )

            val newAnimeReference = MergedAnimeReference(
                id = -1,
                isInfoAnime = false,
                getEpisodeUpdates = true,
                episodeSortMode = 0,
                episodePriority = 0,
                downloadEpisodes = true,
                mergeId = mergedAnime.id,
                mergeUrl = mergedAnime.url,
                animeId = anime.id,
                animeUrl = anime.url,
                animeSourceId = anime.source,
            )

            val mergedAnimeReference = MergedAnimeReference(
                id = -1,
                isInfoAnime = false,
                getEpisodeUpdates = false,
                episodeSortMode = 0,
                episodePriority = -1,
                downloadEpisodes = false,
                mergeId = mergedAnime.id,
                mergeUrl = mergedAnime.url,
                animeId = mergedAnime.id,
                animeUrl = mergedAnime.url,
                animeSourceId = MERGED_SOURCE_ID,
            )

            insertMergedReference.awaitAll(listOf(originalAnimeReference, newAnimeReference, mergedAnimeReference))

            return mergedAnime
        }

        // Note that if the anime are merged in a different order, this won't trigger, but I don't care lol
    }
}

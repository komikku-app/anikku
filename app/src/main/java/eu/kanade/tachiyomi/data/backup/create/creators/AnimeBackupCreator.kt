package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.backupEpisodeMapper
import eu.kanade.tachiyomi.data.backup.models.backupMergedMangaReferenceMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import exh.source.MERGED_SOURCE_ID
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.history.interactor.GetHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    // SY -->
    private val getCustomAnimeInfo: GetCustomAnimeInfo = Injekt.get(),
    // SY <--
) {

    suspend operator fun invoke(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        return animes.map {
            backupAnime(it, options)
        }
    }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        // Entry for this anime
        val animeObject = anime.toBackupAnime(
            // SY -->
            if (options.customInfo) {
                getCustomAnimeInfo.get(anime.id)
            } else {
                null
            },
            // SY <--
        )

        // SY -->
        if (anime.source == MERGED_SOURCE_ID) {
            animeObject.mergedMangaReferences = handler.awaitList {
                mergedQueries.selectByMergeId(anime.id, backupMergedMangaReferenceMapper)
            }
        }
        // SY <--

        animeObject.excludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByAnimeId(anime.id)
        }

        if (options.episodes) {
            // Backup all the episodes
            handler.awaitList {
                episodesQueries.getEpisodesByAnimeId(
                    animeId = anime.id,
                    applyScanlatorFilter = 0, // false
                    mapper = backupEpisodeMapper,
                )
            }
                .takeUnless(List<BackupEpisode>::isEmpty)
                ?.let { animeObject.episodes = it }
        }

        if (options.categories) {
            // Backup categories for this anime
            val categoriesForAnime = getCategories.await(anime.id)
            if (categoriesForAnime.isNotEmpty()) {
                animeObject.categories = categoriesForAnime.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { anime_syncQueries.getTracksByAnimeId(anime.id, backupTrackMapper) }
            if (tracks.isNotEmpty()) {
                animeObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByAnimeId = getHistory.await(anime.id)
            if (historyByAnimeId.isNotEmpty()) {
                val history = historyByAnimeId.map { history ->
                    val episode = handler.awaitOne { episodesQueries.getEpisodeById(history.episodeId) }
                    BackupHistory(episode.url, history.seenAt?.time ?: 0L, history.watchDuration)
                }
                if (history.isNotEmpty()) {
                    animeObject.history = history
                }
            }
        }

        return animeObject
    }
}

private fun Anime.toBackupAnime(/* SY --> */customAnimeInfo: CustomAnimeInfo?/* SY <-- */) =
    BackupAnime(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer_flags = this.viewerFlags.toInt(),
        episodeFlags = this.episodeFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        // SY -->
    ).also { backupManga ->
        customAnimeInfo?.let {
            backupManga.customTitle = it.title
            backupManga.customArtist = it.artist
            backupManga.customAuthor = it.author
            backupManga.customThumbnailUrl = it.thumbnailUrl
            backupManga.customDescription = it.description
            backupManga.customGenre = it.genre
            backupManga.customStatus = it.status?.toInt() ?: 0
        }
    }
// SY <--

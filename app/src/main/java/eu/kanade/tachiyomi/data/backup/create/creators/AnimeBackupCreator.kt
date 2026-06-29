package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.backupAnimeTrackMapper
import eu.kanade.tachiyomi.data.backup.models.backupEpisodeMapper
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.history.interactor.GetHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
) {

    suspend operator fun invoke(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        return animes.map {
            backupAnime(it, options)
        }
    }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        // Entry for this anime
        val animeObject = anime.toBackupAnime()

        if (options.chapters) {
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
            val tracks = handler.awaitList { anime_syncQueries.getTracksByAnimeId(anime.id, backupAnimeTrackMapper) }
            if (tracks.isNotEmpty()) {
                animeObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByAnimeId = getHistory.await(anime.id)
            if (historyByAnimeId.isNotEmpty()) {
                val history = historyByAnimeId.map { history ->
                    val episode = handler.awaitOne { episodesQueries.getEpisodeById(history.episodeId) }
                    BackupHistory(episode.url, history.seenAt?.time ?: 0L)
                }
                if (history.isNotEmpty()) {
                    animeObject.history = history
                }
            }
        }

        return animeObject
    }
}

private fun Anime.toBackupAnime() =
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
        fetchType = this.fetchType,
        parentId = this.parentId,
        id = this.id,
        seasonFlags = this.seasonFlags,
        seasonNumber = this.seasonNumber,
        seasonSourceOrder = this.seasonSourceOrder,
    )

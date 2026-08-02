package app.anikku.macos.platform.library

import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

data class LibraryUpdateProgress(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentTitle: String? = null,
)

data class LibraryUpdateResult(
    val scanned: Int,
    val updated: Int,
    val newlyDiscoveredEpisodes: Int,
    val failures: Map<Long, String>,
)

/** Resolves saved library entries through installed extensions. */
class MacOSLibraryUpdateService(
    private val libraryRepository: LibraryRepository,
    private val historyRepository: HistoryRepository,
    private val sourceResolver: (Long) -> AnimeSource?,
) {
    private val _progress = MutableStateFlow(LibraryUpdateProgress())
    val progress: StateFlow<LibraryUpdateProgress> = _progress.asStateFlow()

    suspend fun updateAll(): LibraryUpdateResult = withContext(Dispatchers.IO) {
        val entries = libraryRepository.getAll()
        _progress.value = LibraryUpdateProgress(running = true, total = entries.size)
        var updated = 0
        var newlyDiscovered = 0
        val failures = linkedMapOf<Long, String>()

        try {
            entries.forEachIndexed { index, entry ->
                _progress.value = LibraryUpdateProgress(
                    running = true,
                    completed = index,
                    total = entries.size,
                    currentTitle = entry.title,
                )
                val source = sourceResolver(entry.sourceId)
                if (source == null) {
                    failures[entry.animeId] = "Source ${entry.sourceId} is not installed"
                    return@forEachIndexed
                }
                val animeUrl = entry.url?.takeIf(String::isNotBlank)
                if (animeUrl == null) {
                    failures[entry.animeId] = "Saved source URL is missing"
                    return@forEachIndexed
                }

                try {
                    val requestAnime = SAnime.create().apply {
                        url = animeUrl
                        title = entry.title
                    }
                    val details = source.getAnimeDetails(requestAnime)
                    val episodes = source.getEpisodeList(details)
                    val distinctEpisodes = episodes.distinctBy { episode ->
                        runCatching { episode.url }.getOrDefault("") to episode.episode_number
                    }
                    val latest = distinctEpisodes.maxByOrNull { it.episode_number }
                    val latestNumber = latest?.episode_number?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
                    val watchedNumber = historyRepository.getForAnime(entry.animeId)
                        .maxOfOrNull { it.episodeNumber } ?: 0.0
                    val unseenCount = distinctEpisodes.count { it.episode_number.toDouble() > watchedNumber }
                    val previousKnown = entry.latestEpisodeNumber
                    val discoveredForEntry = distinctEpisodes.count {
                        it.episode_number.toDouble() > previousKnown.coerceAtLeast(watchedNumber)
                    }
                    newlyDiscovered += discoveredForEntry

                    libraryRepository.add(
                        entry.copy(
                            title = safeValue(entry.title) { details.title },
                            url = safeNullable(entry.url) { details.url },
                            thumbnailUrl = safeNullable(entry.thumbnailUrl) { details.thumbnail_url },
                            author = safeNullable(entry.author) { details.author },
                            artist = safeNullable(entry.artist) { details.artist },
                            description = safeNullable(entry.description) { details.description },
                            genre = runCatching { details.getGenres() }.getOrNull() ?: entry.genre,
                            status = runCatching { details.status }.getOrDefault(entry.status),
                            latestEpisodeNumber = latestNumber,
                            latestEpisodeName = latest?.let { runCatching { it.name }.getOrNull() },
                            unseenEpisodeCount = unseenCount,
                        ),
                    )
                    updated++
                } catch (error: Exception) {
                    logger.warn(error) { "Library update failed for ${entry.title}" }
                    failures[entry.animeId] = error.message?.take(160) ?: error::class.simpleName.orEmpty()
                }
            }
        } finally {
            _progress.value = LibraryUpdateProgress(
                running = false,
                completed = entries.size,
                total = entries.size,
            )
        }

        LibraryUpdateResult(
            scanned = entries.size,
            updated = updated,
            newlyDiscoveredEpisodes = newlyDiscovered,
            failures = failures,
        )
    }

    private inline fun safeValue(fallback: String, block: () -> String): String =
        runCatching(block).getOrNull()?.takeIf(String::isNotBlank) ?: fallback

    private inline fun safeNullable(fallback: String?, block: () -> String?): String? =
        runCatching(block).getOrNull()?.takeIf(String::isNotBlank) ?: fallback
}

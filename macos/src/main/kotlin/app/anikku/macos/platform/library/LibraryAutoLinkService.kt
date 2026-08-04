package app.anikku.macos.platform.library

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.data.LibraryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val autoLinkLogger = KotlinLogging.logger {}

data class AutoLinkResult(
    val attempted: Int = 0,
    val linked: Int = 0,
    val failed: Int = 0,
) {
    fun describe(): String = when {
        attempted == 0 -> "no unlinked titles to match"
        linked > 0 -> "auto-linked $linked to source${if (linked == 1) "" else "s"}"
        else -> "no auto-match found"
    }
}

/**
 * Automatically attaches a streaming source to library entries that were
 * imported from a tracker (AniList) and therefore have no source yet.
 *
 * For each unlinked entry it searches the installed catalogue sources via
 * [AnimeSourceMatcher] and, on a high-confidence title match, persists the
 * source id + URL onto the entry so episodes stream from there directly.
 *
 * Mirrors the shape of [MacOSLibraryUpdateService] (injected repos, runs on
 * Dispatchers.IO, returns a result object). A re-entrancy guard prevents two
 * passes (manual sync + periodic sync) from searching at the same time.
 */
class LibraryAutoLinkService(
    private val libraryRepository: LibraryRepository,
    private val matcher: AnimeSourceMatcher,
) {
    private val running = AtomicBoolean(false)

    /**
     * Batch pass over unlinked library entries (capped at [limit] per call so
     * a large AniList library never blocks the UI). Skips entries already
     * linked by a concurrent pass.
     */
    suspend fun autoLink(limit: Int = 10): AutoLinkResult = withContext(Dispatchers.IO) {
        if (!running.compareAndSet(false, true)) {
            return@withContext AutoLinkResult() // another pass is in flight
        }
        try {
            val unlinked = libraryRepository.getAll()
                .filter { it.sourceId == 0L || it.url.isNullOrBlank() }
                .take(limit.coerceAtLeast(0))
            var linked = 0
            var failed = 0

            for (entry in unlinked) {
                val current = libraryRepository.get(entry.animeId) ?: continue
                if (current.sourceId != 0L) continue // linked by a concurrent pass
                try {
                    val match = matcher.findBest(current.title)
                    if (match != null) {
                        libraryRepository.add(
                            current.copy(
                                sourceId = match.sourceId,
                                url = match.url,
                                thumbnailUrl = match.thumbnailUrl?.takeIf { it.isNotBlank() }
                                    ?: current.thumbnailUrl,
                            ),
                        )
                        linked++
                    }
                } catch (error: Exception) {
                    autoLinkLogger.warn(error) { "Auto-link failed for ${current.title}" }
                    failed++
                }
            }
            AutoLinkResult(attempted = unlinked.size, linked = linked, failed = failed)
        } finally {
            running.set(false)
        }
    }

    /**
     * Match and link a single entry (used by the anime detail screen when a
     * library entry has no source). Returns the match that was persisted, or
     * null when the entry is already linked or no confident match exists.
     */
    suspend fun autoLinkOne(animeId: Long, title: String): SourceMatch? = withContext(Dispatchers.IO) {
        val current = libraryRepository.get(animeId) ?: return@withContext null
        if (current.sourceId != 0L) return@withContext null
        val match = matcher.findBest(title.ifBlank { current.title })
        if (match != null) {
            libraryRepository.add(
                current.copy(
                    sourceId = match.sourceId,
                    url = match.url,
                    thumbnailUrl = match.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: current.thumbnailUrl,
                ),
            )
        }
        match
    }
}

val LocalLibraryAutoLinkService = compositionLocalOf<LibraryAutoLinkService?> { null }

val LocalAnimeSourceMatcher = compositionLocalOf<AnimeSourceMatcher?> { null }

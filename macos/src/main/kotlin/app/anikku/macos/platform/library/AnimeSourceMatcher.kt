package app.anikku.macos.platform.library

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * A candidate source match for an anime title: which installed extension has
 * it, and at which URL.
 */
data class SourceMatch(
    val sourceId: Long,
    val sourceName: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    /** 1.0 = normalized title equality, 0.85 = one title contains the other. */
    val score: Double,
)

/**
 * Finds anime titles across installed catalogue extensions by searching each
 * one and scoring the results against the requested title.
 *
 * Used by:
 * - [LibraryAutoLinkService] to auto-link AniList-imported entries to sources
 * - the player's cross-source fallback (when a source fails to play, try the
 *   next source that has the same anime)
 *
 * Matching is deliberately conservative: only high-confidence matches
 * (score >= [ACCEPT_THRESHOLD]) are returned, so a wrong anime is never
 * silently linked.
 */
class AnimeSourceMatcher(
    private val sourcesProvider: () -> List<CatalogueSource>,
    private val searchTimeoutMs: Long = 15_000L,
) {

    /**
     * Search every installed source for [title] and return usable matches
     * ordered by score (best first). [excludeSourceId] skips a source — used
     * by the player fallback so it doesn't immediately re-try the failing one.
     */
    suspend fun findMatches(title: String, excludeSourceId: Long? = null): List<SourceMatch> {
        val query = title.trim()
        if (query.isBlank()) return emptyList()

        val sources = sourcesProvider().filter { it.id != excludeSourceId }
        if (sources.isEmpty()) return emptyList()

        // Early-exit: once any source reports an exact-title match, later
        // sources skip their (potentially slow) search. Parallel searches still
        // all start together, so worst case is bounded by searchTimeoutMs.
        val exactFound = AtomicBoolean(false)

        return withContext(Dispatchers.IO) {
            coroutineScope {
                val results = sources.map { source ->
                    async {
                        if (exactFound.get()) return@async emptyList()
                        searchSource(source, query).also { matches ->
                            if (matches.any { it.score >= EXACT_SCORE }) exactFound.set(true)
                        }
                    }
                }
                results.flatMap { it.await() }.sortedByDescending { it.score }
            }
        }
    }

    /** Best single match for [title], or null if no source has it confidently. */
    suspend fun findBest(title: String, excludeSourceId: Long? = null): SourceMatch? =
        findMatches(title, excludeSourceId).firstOrNull()

    private suspend fun searchSource(source: CatalogueSource, query: String): List<SourceMatch> {
        val animes = try {
            withTimeout(searchTimeoutMs) {
                source.getSearchAnime(page = 1, query = query, filters = AnimeFilterList()).animes
            }
        } catch (_: Exception) {
            // Timeouts and per-source failures just mean "no match here" —
            // extensions run in a separate classloader and can throw broadly.
            return emptyList()
        }
        return animes.mapNotNull { anime ->
            val safeUrl = runCatching { anime.url }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val safeTitle = runCatching { anime.title }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val score = scoreTitle(query, safeTitle)
            if (score < ACCEPT_THRESHOLD) return@mapNotNull null
            SourceMatch(
                sourceId = source.id,
                sourceName = source.name,
                url = safeUrl,
                title = safeTitle,
                thumbnailUrl = anime.thumbnail_url,
                score = score,
            )
        }
    }

    companion object {
        /** Exact normalized-title equality. */
        const val EXACT_SCORE = 1.0

        /** One normalized title fully contains the other. */
        const val CONTAINS_SCORE = 0.85

        /** Minimum score for a match to be considered usable. */
        const val ACCEPT_THRESHOLD = 0.85

        /** Trailing parenthetical suffixes common in anime titles, e.g. "(TV)", "(Dub)", "(Season 1)". */
        private val END_PARENTHESIS = Regex("\\s*\\([^)]*\\)\\s*$")

        /**
         * Canonical form used for title comparison: lowercase, ASCII/Unicode
         * letters and digits only, trailing parenthetical suffixes stripped.
         */
        fun normalizeTitle(title: String): String {
            val stripped = title.trim().replace(END_PARENTHESIS, "")
            return stripped.lowercase().filter { it.isLetterOrDigit() }
        }

        /**
         * Score how well [candidate] matches the requested [query] title.
         * 1.0 for normalized equality, 0.85 when one fully contains the other,
         * 0.0 otherwise. Containment requires both titles to be >= 4 chars to
         * avoid trivial false positives on short strings.
         */
        fun scoreTitle(query: String, candidate: String): Double {
            val q = normalizeTitle(query)
            val c = normalizeTitle(candidate)
            if (q.isEmpty() || c.isEmpty()) return 0.0
            if (q == c) return EXACT_SCORE
            if (q.length >= 4 && c.contains(q)) return CONTAINS_SCORE
            if (c.length >= 4 && q.contains(c)) return CONTAINS_SCORE
            return 0.0
        }
    }
}

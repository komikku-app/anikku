package app.anikku.macos.platform.auth

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * Two-way AniList library sync.
 *
 * **Pull**: imports the user's AniList media lists into the local Library with
 * category mapping (add-if-missing; backfills `anilistId` on existing entries
 * matched by title and refreshes cover/description).
 *
 * **Push**: for every library entry with a resolvable AniList id (imported via
 * sync, or manually linked via the tracker search screen), pushes
 * `max(localSeenCount, remoteProgress)` and a status derived from the entry's
 * category when it is more committed than the remote state.
 *
 * Merge policy (progress never regresses in either direction):
 * - progress: always the max of local and remote.
 * - status: follows AniList on import; the app pushes a status only when the
 *   user's category is a terminal/commit state (Completed/Dropped) or the
 *   entry is currently Watching and remote is unset/planning/paused.
 *
 * Cadence is owned by the caller (AnikkuApp reads/writes
 * [app.anikku.macos.ui.settings.SettingsState] interval + last-sync time).
 */
class AniListSyncService(
    private val trackerManager: TrackerManager,
    private val libraryRepository: LibraryRepository,
    private val historyRepository: HistoryRepository,
) {

    fun canSync(): Boolean = trackerManager.isLoggedIn("anilist")

    /**
     * Run one full pull + push cycle. Never throws: network failures are
     * reported in [SyncOutcome.errors].
     */
    suspend fun syncNow(): SyncOutcome = withContext(Dispatchers.IO) {
        if (!canSync()) {
            return@withContext SyncOutcome(errors = listOf("Not logged in to AniList"))
        }
        val remote = trackerManager.fetchAniListLibrary()
        if (remote == null) {
            return@withContext SyncOutcome(errors = listOf("Failed to fetch AniList library"))
        }
        val remoteByMediaId = remote.associateBy { it.mediaId }

        // ---- Pull ----------------------------------------------------------
        val categories = libraryRepository.getCategories()
        val existing = libraryRepository.getAll()
        val existingByAnilist = existing.filter { it.anilistId != null }.associateBy { it.anilistId!! }
        val existingByTitle = existing.associateBy { it.title.lowercase() }

        var imported = 0
        var updated = 0
        for (entry in remote) {
            val current = existingByAnilist[entry.mediaId]
                ?: existingByTitle[entry.title.lowercase()]
            if (current == null) {
                libraryRepository.add(
                    LibraryRepository.LibraryEntry(
                        animeId = entry.mediaId,
                        title = entry.title,
                        anilistId = entry.mediaId,
                        thumbnailUrl = entry.coverUrl,
                        description = entry.description,
                        genre = entry.genres,
                        status = animeStatusInt(entry.mediaStatus),
                        categoryId = categoryIdForStatus(entry.status, categories),
                    ),
                )
                imported++
            } else if (current.anilistId == null) {
                // Matched by title — backfill the AniList id and enrich cover/desc.
                libraryRepository.add(
                    current.copy(
                        anilistId = entry.mediaId,
                        thumbnailUrl = current.thumbnailUrl ?: entry.coverUrl,
                        description = current.description ?: entry.description,
                        genre = current.genre ?: entry.genres,
                        status = current.status.takeIf { it != 0 } ?: animeStatusInt(entry.mediaStatus),
                    ),
                )
                updated++
            }
        }

        // ---- Push ----------------------------------------------------------
        val categoryById = categories.associateBy { it.id }
        val pushable = existing.mapNotNull { local ->
            val anilistId = local.anilistId
                ?: trackerManager.tokenStore.getAnimeMapping("anilist", local.title)?.toLongOrNull()
            if (anilistId == null) null else local to anilistId
        }

        var pushed = 0
        val errors = mutableListOf<String>()
        for ((local, anilistId) in pushable) {
            try {
                val remoteEntry = remoteByMediaId[anilistId]
                val remoteProgress = remoteEntry?.progress ?: 0
                val remoteStatus = remoteEntry?.status

                val localSeen = historyRepository.getForAnime(local.animeId)
                    .filter { it.totalSeconds <= 0 || it.lastSecondSeen >= it.totalSeconds * 0.8 }
                    .map { it.episodeNumber.toInt() }
                    .distinct()
                    .count()
                val cappedLocalSeen = remoteEntry?.totalEpisodes?.takeIf { it > 0 }
                    ?.let { minOf(localSeen, it) } ?: localSeen

                val progressToPush = maxOf(cappedLocalSeen, remoteProgress)
                val statusToPush = shouldPushStatus(
                    categoryById[local.categoryId]?.name,
                    remoteStatus,
                )

                val progressChanged = progressToPush != remoteProgress
                val statusChanged = statusToPush != null && statusToPush != remoteStatus
                if (progressChanged || statusChanged) {
                    val ok = trackerManager.updateProgress(
                        tracker = "anilist",
                        remoteAnimeId = anilistId.toString(),
                        episodeNumber = progressToPush,
                        status = statusToPush,
                    )
                    if (ok) {
                        pushed++
                        logger.info { "AniList push ${local.title}: progress=$progressToPush status=$statusToPush" }
                    } else {
                        errors.add("push rejected: ${local.title}")
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "AniList push failed for ${local.title}" }
                errors.add("push error: ${local.title}")
            }
        }

        SyncOutcome(
            imported = imported,
            updated = updated,
            pushed = pushed,
            remoteCount = remote.size,
            errors = errors.take(5),
        )
    }

    companion object {
        /**
         * Map an AniList MediaListStatus to a local category id. Matches by
         * category name (case-insensitive) so user-renamed categories still
         * resolve; falls back to the default category.
         */
        fun categoryIdForStatus(status: String, categories: List<CategoryEntry>): Long {
            val name = when (status) {
                "CURRENT", "REPEATING" -> "Watching"
                "COMPLETED" -> "Completed"
                "DROPPED" -> "Dropped"
                "PLANNING" -> "Plan to Watch"
                else -> null
            } ?: return CATEGORY_DEFAULT_ID
            return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: CATEGORY_DEFAULT_ID
        }

        /** Map an AniList MediaStatus (the anime's release state) to the app's status int. */
        fun animeStatusInt(mediaStatus: String?): Int = when (mediaStatus) {
            "RELEASING" -> 1
            "FINISHED" -> 2
            "NOT_YET_RELEASED" -> 4
            "CANCELLED" -> 5
            "HIATUS" -> 6
            else -> 0
        }

        /** Map a local category name to an AniList MediaListStatus (null = no status). */
        fun anilistStatusForCategory(categoryName: String?): String? = when {
            categoryName.isNullOrBlank() -> null
            categoryName.equals("watching", ignoreCase = true) -> "CURRENT"
            categoryName.equals("completed", ignoreCase = true) -> "COMPLETED"
            categoryName.equals("dropped", ignoreCase = true) -> "DROPPED"
            categoryName.equals("plan to watch", ignoreCase = true) -> "PLANNING"
            else -> null
        }

        /**
         * Decide whether the app should push its category-derived status,
         * given the remote (AniList) status. Never downgrades a remote
         * terminal state; only pushes terminal/commit states or Watching when
         * the remote is unset/planning/paused.
         */
        fun shouldPushStatus(localCategory: String?, remoteStatus: String?): String? {
            val local = anilistStatusForCategory(localCategory) ?: return null
            if (remoteStatus == "COMPLETED") return null
            return when (local) {
                "COMPLETED", "DROPPED" -> local
                "CURRENT" -> if (remoteStatus == null || remoteStatus == "PLANNING" || remoteStatus == "PAUSED") local else null
                else -> null
            }
        }
    }
}

/**
 * Result of one [AniListSyncService.syncNow] cycle.
 */
data class SyncOutcome(
    val imported: Int = 0,
    val updated: Int = 0,
    val pushed: Int = 0,
    val remoteCount: Int = 0,
    val errors: List<String> = emptyList(),
) {
    fun toMessage(label: String = "AniList"): String = buildString {
        append("$label sync: ")
        val parts = mutableListOf<String>()
        if (imported > 0) parts.add("$imported imported")
        if (updated > 0) parts.add("$updated updated")
        if (pushed > 0) parts.add("$pushed pushed")
        if (errors.isNotEmpty()) parts.add("${errors.size} error(s)")
        append(parts.joinToString(", ").ifBlank { "up to date" })
    }
}

/** CompositionLocal providing the app-wide [AniListSyncService] (may be null). */
val LocalAniListSyncService = compositionLocalOf<AniListSyncService?> { null }

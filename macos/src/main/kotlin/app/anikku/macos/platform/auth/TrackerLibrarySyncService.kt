package app.anikku.macos.platform.auth

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val trackerSyncLogger = KotlinLogging.logger {}

/** One pending tracker progress update: (remoteId, progress, status?). */
data class PushPlan(
    val remoteId: Long,
    val progress: Int,
    val status: String?,
)

/**
 * Two-way library sync for trackers beyond AniList (MyAnimeList + Kitsu).
 *
 * Mirrors [AniListSyncService]'s pull/push shape: pull imports the remote list
 * into the library (matching existing entries by per-tracker id or title, so a
 * show synced from multiple trackers stays one entry), push writes
 * history-derived watched-episode counts and status back. AniList keeps its
 * own service; this one is wired only for trackers that have it configured.
 */
class TrackerLibrarySyncService(
    private val trackerManager: TrackerManager,
    private val libraryRepository: LibraryRepository,
    private val historyRepository: HistoryRepository,
) {

    suspend fun syncNow(tracker: String): SyncOutcome = when (tracker) {
        "myanimelist" -> syncMal()
        "kitsu" -> syncKitsu()
        else -> SyncOutcome(errors = listOf("Unsupported tracker: $tracker"))
    }

    // -----------------------------------------------------------------------
    // MyAnimeList
    // -----------------------------------------------------------------------

    private suspend fun syncMal(): SyncOutcome = withContext(Dispatchers.IO) {
        if (!trackerManager.isLoggedIn("myanimelist")) {
            return@withContext SyncOutcome(errors = listOf("Not logged in to MyAnimeList"))
        }
        val remote = trackerManager.fetchMyAnimeListLibrary()
        if (remote == null) {
            return@withContext SyncOutcome(errors = listOf("Failed to fetch MyAnimeList library"))
        }

        val (imported, updated) = pullMal(remote)
        val plans = planMalPush(remote)
        var pushed = 0
        val errors = mutableListOf<String>()
        for (plan in plans) {
            if (trackerManager.updateProgress("myanimelist", plan.remoteId.toString(), plan.progress, plan.status)) {
                pushed++
            } else {
                errors.add("MAL push failed for id ${plan.remoteId}")
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

    /** Pull phase for MAL: import new entries, backfill malId on title-matched ones. */
    internal fun pullMal(remote: List<MalLibraryEntry>): Pair<Int, Int> {
        val categories = libraryRepository.getCategories()
        val existing = libraryRepository.getAll()
        val existingByMalId = existing.filter { it.malId != null }.associateBy { it.malId!! }
        val existingByTitle = existing.associateBy { it.title.lowercase() }
        var imported = 0
        var updated = 0

        for (entry in remote) {
            val current = existingByMalId[entry.malId] ?: existingByTitle[entry.title.lowercase()]
            if (current == null) {
                libraryRepository.add(
                    LibraryRepository.LibraryEntry(
                        animeId = entry.malId,
                        title = entry.title,
                        malId = entry.malId,
                        thumbnailUrl = entry.coverUrl,
                        categoryId = malCategoryForStatus(entry.status, categories),
                    ),
                )
                imported++
            } else if (current.malId == null) {
                // Title-matched existing entry (e.g. already imported from
                // AniList) — attach the MAL id so it stays a single entry.
                libraryRepository.add(
                    current.copy(
                        malId = entry.malId,
                        thumbnailUrl = entry.coverUrl?.takeIf { it.isNotBlank() } ?: current.thumbnailUrl,
                    ),
                )
                updated++
            }
        }
        return imported to updated
    }

    /** Push phase for MAL: decide (id, progress, status) updates without sending them. */
    internal fun planMalPush(remote: List<MalLibraryEntry>): List<PushPlan> {
        val remoteById = remote.associateBy { it.malId }
        val categories = libraryRepository.getCategories()
        val categoryById = categories.associateBy { it.id }
        val existing = libraryRepository.getAll()

        val plans = mutableListOf<PushPlan>()
        for (local in existing) {
            val malId = local.malId
                ?: trackerManager.tokenStore.getAnimeMapping("myanimelist", local.title)?.toLongOrNull()
                ?: continue
            val remoteEntry = remoteById[malId] ?: continue
            val localSeen = watchedEpisodeCount(local.animeId)
            val capped = remoteEntry.totalEpisodes?.let { total -> localSeen.coerceAtMost(total) } ?: localSeen
            val progressToPush = maxOf(capped, remoteEntry.progress)
            val statusToPush = malStatusForCategory(categoryById[local.categoryId]?.name, remoteEntry.status)
            plans += PushPlan(malId, progressToPush, statusToPush)
        }
        return plans
    }

    // -----------------------------------------------------------------------
    // Kitsu
    // -----------------------------------------------------------------------

    private suspend fun syncKitsu(): SyncOutcome = withContext(Dispatchers.IO) {
        if (!trackerManager.isLoggedIn("kitsu")) {
            return@withContext SyncOutcome(errors = listOf("Not logged in to Kitsu"))
        }
        val remote = trackerManager.fetchKitsuLibrary()
        if (remote == null) {
            return@withContext SyncOutcome(errors = listOf("Failed to fetch Kitsu library"))
        }

        val (imported, updated) = pullKitsu(remote)
        val plans = planKitsuPush(remote)
        var pushed = 0
        val errors = mutableListOf<String>()
        for (plan in plans) {
            if (trackerManager.updateProgress("kitsu", plan.remoteId.toString(), plan.progress, plan.status)) {
                pushed++
            } else {
                errors.add("Kitsu push failed for id ${plan.remoteId}")
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

    /** Pull phase for Kitsu: import new entries, backfill kitsuId on title-matched ones. */
    internal fun pullKitsu(remote: List<KitsuLibraryEntry>): Pair<Int, Int> {
        val categories = libraryRepository.getCategories()
        val existing = libraryRepository.getAll()
        val existingByKitsuId = existing.filter { it.kitsuId != null }.associateBy { it.kitsuId!! }
        val existingByTitle = existing.associateBy { it.title.lowercase() }
        var imported = 0
        var updated = 0

        for (entry in remote) {
            val current = existingByKitsuId[entry.kitsuId] ?: existingByTitle[entry.title.lowercase()]
            if (current == null) {
                libraryRepository.add(
                    LibraryRepository.LibraryEntry(
                        animeId = entry.kitsuId,
                        title = entry.title,
                        kitsuId = entry.kitsuId,
                        thumbnailUrl = entry.coverUrl,
                        categoryId = kitsuCategoryForStatus(entry.status, categories),
                    ),
                )
                imported++
            } else if (current.kitsuId == null) {
                libraryRepository.add(
                    current.copy(
                        kitsuId = entry.kitsuId,
                        thumbnailUrl = entry.coverUrl?.takeIf { it.isNotBlank() } ?: current.thumbnailUrl,
                    ),
                )
                updated++
            }
        }
        return imported to updated
    }

    /** Push phase for Kitsu: decide (id, progress, status) updates without sending them. */
    internal fun planKitsuPush(remote: List<KitsuLibraryEntry>): List<PushPlan> {
        val remoteById = remote.associateBy { it.kitsuId }
        val categories = libraryRepository.getCategories()
        val categoryById = categories.associateBy { it.id }
        val existing = libraryRepository.getAll()

        val plans = mutableListOf<PushPlan>()
        for (local in existing) {
            val kitsuId = local.kitsuId
                ?: trackerManager.tokenStore.getAnimeMapping("kitsu", local.title)?.toLongOrNull()
                ?: continue
            val remoteEntry = remoteById[kitsuId] ?: continue
            val localSeen = watchedEpisodeCount(local.animeId)
            val capped = remoteEntry.totalEpisodes?.let { total -> localSeen.coerceAtMost(total) } ?: localSeen
            val progressToPush = maxOf(capped, remoteEntry.progress)
            val statusToPush = kitsuStatusForCategory(categoryById[local.categoryId]?.name, remoteEntry.status)
            plans += PushPlan(kitsuId, progressToPush, statusToPush)
        }
        return plans
    }

    /** Distinct episodes the user finished (>=80% watched) for [animeId]. */
    private fun watchedEpisodeCount(animeId: Long): Int =
        historyRepository.getForAnime(animeId)
            .filter { it.totalSeconds <= 0 || it.lastSecondSeen >= it.totalSeconds * 0.8 }
            .map { it.episodeNumber.toInt() }
            .distinct()
            .count()

    companion object {
        /** MAL list status → library category name. */
        fun malCategoryForStatus(status: String, categories: List<CategoryEntry>): Long {
            val name = when (status) {
                "watching" -> "Watching"
                "completed" -> "Completed"
                "dropped" -> "Dropped"
                "plan_to_watch" -> "Plan to Watch"
                else -> return CATEGORY_DEFAULT_ID
            }
            return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: CATEGORY_DEFAULT_ID
        }

        /** Kitsu list status → library category name. */
        fun kitsuCategoryForStatus(status: String, categories: List<CategoryEntry>): Long {
            val name = when (status) {
                "current" -> "Watching"
                "completed" -> "Completed"
                "dropped" -> "Dropped"
                "planned" -> "Plan to Watch"
                else -> return CATEGORY_DEFAULT_ID
            }
            return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: CATEGORY_DEFAULT_ID
        }

        /** Library category name → MAL list status, null when no change is needed. */
        fun malStatusForCategory(categoryName: String?, remoteStatus: String): String? {
            val local = when (categoryName?.lowercase()) {
                "watching" -> "watching"
                "completed" -> "completed"
                "dropped" -> "dropped"
                "plan to watch" -> "plan_to_watch"
                else -> null
            } ?: return null
            return if (local == remoteStatus) null else local
        }

        /** Library category name → Kitsu list status, null when no change is needed. */
        fun kitsuStatusForCategory(categoryName: String?, remoteStatus: String): String? {
            val local = when (categoryName?.lowercase()) {
                "watching" -> "current"
                "completed" -> "completed"
                "dropped" -> "dropped"
                "plan to watch" -> "planned"
                else -> null
            } ?: return null
            return if (local == remoteStatus) null else local
        }
    }
}

val LocalTrackerLibrarySyncService = compositionLocalOf<TrackerLibrarySyncService?> { null }

package tachiyomi.domain.anime.model

import androidx.compose.runtime.Immutable
import aniyomi.domain.anime.SeasonDisplayMode
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.anime.interactor.GetCustomAnimeInfo
import uy.kohesive.injekt.injectLazy
import java.io.Serializable
import java.time.Instant
import kotlin.math.pow

typealias Manga = Anime

@Immutable
data class Anime(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val episodeFlags: Long,
    val coverLastModified: Long,
    val url: String,
    // SY -->
    val ogTitle: String,
    val ogArtist: String?,
    val ogAuthor: String?,
    val ogThumbnailUrl: String?,
    val ogDescription: String?,
    val ogGenre: List<String>?,
    val ogStatus: Long,
    // SY <--
    val updateStrategy: UpdateStrategy,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val fetchType: FetchType,
    val parentId: Long?,
    val seasonFlags: Long,
    val seasonNumber: Double,
    val seasonSourceOrder: Long,
) : Serializable {

    // SY -->
    private val customAnimeInfo = if (favorite) {
        getCustomAnimeInfo.get(id)
    } else {
        null
    }

    val title: String
        get() = customAnimeInfo?.title ?: ogTitle

    val author: String?
        get() = customAnimeInfo?.author ?: ogAuthor

    val artist: String?
        get() = customAnimeInfo?.artist ?: ogArtist

    val thumbnailUrl: String?
        get() = customAnimeInfo?.thumbnailUrl ?: ogThumbnailUrl

    val description: String?
        get() = customAnimeInfo?.description ?: ogDescription

    val genre: List<String>?
        get() = customAnimeInfo?.genre ?: ogGenre

    val status: Long
        get() = customAnimeInfo?.status ?: ogStatus
    // SY <--

    val expectedNextUpdate: Instant?
        get() = nextUpdate
            /* KMK -->
            Always predict release date even for Completed entries
            .takeIf { status != SAnime.COMPLETED.toLong() }
             KMK <-- */
            ?.let { Instant.ofEpochMilli(it) }

    val sorting: Long
        get() = episodeFlags and EPISODE_SORTING_MASK

    val displayMode: Long
        get() = episodeFlags and EPISODE_DISPLAY_MASK

    val unseenFilterRaw: Long
        get() = episodeFlags and EPISODE_UNSEEN_MASK

    val downloadedFilterRaw: Long
        get() = episodeFlags and EPISODE_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = episodeFlags and EPISODE_BOOKMARKED_MASK

    // AM (FILLERMARK) -->
    val fillermarkedFilterRaw: Long
        get() = episodeFlags and EPISODE_FILLERMARKED_MASK
    // <-- AM (FILLERMARK)

    val skipIntroLength: Int
        get() = (viewerFlags and ANIME_INTRO_MASK).toInt()

    val skipIntroDisable: Boolean
        get() = (viewerFlags and ANIME_INTRO_DISABLE_MASK) == ANIME_INTRO_DISABLE_MASK

    val nextEpisodeToAir: Int
        get() = (viewerFlags and ANIME_AIRING_EPISODE_MASK).removeHexZeros(zeros = 2).toInt()

    val nextEpisodeAiringAt: Long
        get() = (viewerFlags and ANIME_AIRING_TIME_MASK).removeHexZeros(zeros = 6)

    val unseenFilter: TriState
        get() = when (unseenFilterRaw) {
            EPISODE_SHOW_UNSEEN -> TriState.ENABLED_IS
            EPISODE_SHOW_SEEN -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val bookmarkedFilter: TriState
        get() = when (bookmarkedFilterRaw) {
            EPISODE_SHOW_BOOKMARKED -> TriState.ENABLED_IS
            EPISODE_SHOW_NOT_BOOKMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    // AM (FILLERMARK) -->
    val fillermarkedFilter: TriState
        get() = when (fillermarkedFilterRaw) {
            EPISODE_SHOW_FILLERMARKED -> TriState.ENABLED_IS
            EPISODE_SHOW_NOT_FILLERMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    // <-- AM (FILLERMARK)

    fun sortDescending(): Boolean {
        return episodeFlags and EPISODE_SORT_DIR_MASK == EPISODE_SORT_DESC
    }

    val seasonDownloadedFilterRaw: Long
        get() = seasonFlags and SEASON_DOWNLOADED_MASK

    val seasonUnseenFilterRaw: Long
        get() = seasonFlags and SEASON_UNSEEN_MASK

    val seasonStartedFilterRaw: Long
        get() = seasonFlags and SEASON_STARTED_MASK

    val seasonCompletedFilterRaw: Long
        get() = seasonFlags and SEASON_COMPLETED_MASK

    val seasonBookmarkedFilterRaw: Long
        get() = seasonFlags and SEASON_BOOKMARKED_MASK

    val seasonFillermarkedFilterRaw: Long
        get() = seasonFlags and SEASON_FILLERMARKED_MASK

    val seasonUnseenFilter: TriState
        get() = when (seasonUnseenFilterRaw) {
            SEASON_SHOW_UNSEEN -> TriState.ENABLED_IS
            SEASON_SHOW_SEEN -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val seasonStartedFilter: TriState
        get() = when (seasonStartedFilterRaw) {
            SEASON_SHOW_STARTED -> TriState.ENABLED_IS
            SEASON_SHOW_NOT_STARTED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val seasonCompletedFilter: TriState
        get() = when (seasonCompletedFilterRaw) {
            SEASON_SHOW_COMPLETED -> TriState.ENABLED_IS
            SEASON_SHOW_NOT_COMPLETED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val seasonBookmarkedFilter: TriState
        get() = when (seasonBookmarkedFilterRaw) {
            SEASON_SHOW_BOOKMARKED -> TriState.ENABLED_IS
            SEASON_SHOW_NOT_BOOKMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val seasonFillermarkedFilter: TriState
        get() = when (seasonFillermarkedFilterRaw) {
            SEASON_SHOW_FILLERMARKED -> TriState.ENABLED_IS
            SEASON_SHOW_NOT_FILLERMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val seasonSorting: Long
        get() = seasonFlags and SEASON_SORT_MASK

    fun seasonSortDescending(): Boolean {
        return seasonFlags and SEASON_SORT_DIR_MASK == SEASON_SORT_DESC
    }

    val seasonDisplayGridMode: SeasonDisplayMode
        get() = SeasonDisplayMode.fromLong(
            (seasonFlags and SEASON_GRID_DISPLAY_MODE_MASK) shr SEASON_GRID_DISPLAY_MODE_BIT_OFFSET,
        )

    val seasonDisplayGridSize: Int
        get() = ((seasonFlags and SEASON_GRID_DISPLAY_SIZE_MASK) shr SEASON_GRID_DISPLAY_SIZE_BIT_OFFSET).toInt()

    val seasonDownloadedOverlay: Boolean
        get() = (seasonFlags and SEASON_OVERLAY_DOWNLOADED_MASK) == SEASON_OVERLAY_DOWNLOADED_MASK

    val seasonUnseenOverlay: Boolean
        get() = (seasonFlags and SEASON_OVERLAY_UNSEEN_MASK) == SEASON_OVERLAY_UNSEEN_MASK

    val seasonLocalOverlay: Boolean
        get() = (seasonFlags and SEASON_OVERLAY_LOCAL_MASK) == SEASON_OVERLAY_LOCAL_MASK

    val seasonLangOverlay: Boolean
        get() = (seasonFlags and SEASON_OVERLAY_LANG_MASK) == SEASON_OVERLAY_LANG_MASK

    val seasonContinueOverlay: Boolean
        get() = (seasonFlags and SEASON_OVERLAY_CONT_MASK) == SEASON_OVERLAY_CONT_MASK

    val seasonDisplayMode: Long
        get() = seasonFlags and SEASON_DISPLAY_MODE_MASK

    private fun Long.removeHexZeros(zeros: Int): Long {
        val hex = 16.0
        return this.div(hex.pow(zeros)).toLong()
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val EPISODE_SORT_DESC = 0x00000000L
        const val EPISODE_SORT_ASC = 0x00000001L
        const val EPISODE_SORT_DIR_MASK = 0x00000001L

        const val EPISODE_SHOW_UNSEEN = 0x00000002L
        const val EPISODE_SHOW_SEEN = 0x00000004L
        const val EPISODE_UNSEEN_MASK = 0x00000006L

        const val EPISODE_SHOW_DOWNLOADED = 0x00000008L
        const val EPISODE_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val EPISODE_DOWNLOADED_MASK = 0x00000018L

        const val EPISODE_SHOW_BOOKMARKED = 0x00000020L
        const val EPISODE_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val EPISODE_BOOKMARKED_MASK = 0x00000060L

        // AM (FILLERMARK) -->
        const val EPISODE_SHOW_FILLERMARKED = 0x00000080L
        const val EPISODE_SHOW_NOT_FILLERMARKED = 0x00000100L
        const val EPISODE_FILLERMARKED_MASK = 0x00000180L

        const val EPISODE_SORTING_SOURCE = 0x00000000L
        const val EPISODE_SORTING_NUMBER = 0x00000100L
        const val EPISODE_SORTING_UPLOAD_DATE = 0x00000200L
        const val EPISODE_SORTING_ALPHABET = 0x00000300L
        const val EPISODE_SORTING_MASK = 0x00000300L
        // <-- AM (FILLERMARK)

        const val EPISODE_DISPLAY_NAME = 0x00000000L
        const val EPISODE_DISPLAY_NUMBER = 0x00100000L
        const val EPISODE_DISPLAY_MASK = 0x00100000L

        const val SEASON_SORT_DESC = 0x00000000L
        const val SEASON_SORT_ASC = 0x00000001L
        const val SEASON_SORT_DIR_MASK = 0x00000001L

        const val SEASON_SHOW_DOWNLOADED = 0x00000002L
        const val SEASON_SHOW_NOT_DOWNLOADED = 0x00000004L
        const val SEASON_DOWNLOADED_MASK = 0x00000006L

        const val SEASON_SHOW_UNSEEN = 0x00000008L
        const val SEASON_SHOW_SEEN = 0x00000010L
        const val SEASON_UNSEEN_MASK = 0x00000018L

        const val SEASON_SHOW_STARTED = 0x00000020L
        const val SEASON_SHOW_NOT_STARTED = 0x00000040L
        const val SEASON_STARTED_MASK = 0x00000060L

        const val SEASON_SHOW_COMPLETED = 0x00000080L
        const val SEASON_SHOW_NOT_COMPLETED = 0x00000100L
        const val SEASON_COMPLETED_MASK = 0x00000180L

        const val SEASON_SHOW_BOOKMARKED = 0x00000200L
        const val SEASON_SHOW_NOT_BOOKMARKED = 0x00000400L
        const val SEASON_BOOKMARKED_MASK = 0x00000600L

        const val SEASON_SHOW_FILLERMARKED = 0x00000800L
        const val SEASON_SHOW_NOT_FILLERMARKED = 0x00001000L
        const val SEASON_FILLERMARKED_MASK = 0x00001800L

        const val SEASON_SORT_SOURCE = 0x00000000L
        const val SEASON_SORT_SEASON = 0x00002000L
        const val SEASON_SORT_UPLOAD = 0x00004000L
        const val SEASON_SORT_ALPHABET = 0x00006000L
        const val SEASON_SORT_COUNT = 0x00008000L
        const val SEASON_SORT_LAST_SEEN = 0x0000A000L
        const val SEASON_SORT_FETCHED = 0x0000C000L
        const val SEASON_SORT_MASK = 0x0000E000L

        const val SEASON_GRID_DISPLAY_MODE_BIT_OFFSET = 16
        const val SEASON_GRID_DISPLAY_MODE_MASK = 0x00030000L

        const val SEASON_GRID_DISPLAY_SIZE_BIT_OFFSET = 18
        const val SEASON_GRID_DISPLAY_SIZE_MASK = 0x003C0000L

        const val SEASON_OVERLAY_DOWNLOADED_MASK = 0x00400000L
        const val SEASON_OVERLAY_UNSEEN_MASK = 0x00800000L
        const val SEASON_OVERLAY_LOCAL_MASK = 0x01000000L
        const val SEASON_OVERLAY_LANG_MASK = 0x02000000L
        const val SEASON_OVERLAY_CONT_MASK = 0x04000000L

        const val SEASON_DISPLAY_MODE_SOURCE = 0x00000000L
        const val SEASON_DISPLAY_MODE_NUMBER = 0x08000000L
        const val SEASON_DISPLAY_MODE_MASK = 0x08000000L

        const val ANIME_INTRO_MASK = 0x0000000000000FFL
        const val ANIME_AIRING_EPISODE_MASK = 0x000000000FFFF00L
        const val ANIME_AIRING_TIME_MASK = 0x0FFFFFFFF000000L
        const val ANIME_INTRO_DISABLE_MASK = 0x100000000000000L

        fun create() = Anime(
            id = -1L,
            url = "",
            // Sy -->
            ogTitle = "",
            // SY <--
            source = -1L,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            episodeFlags = 0L,
            coverLastModified = 0L,
            // SY -->
            ogArtist = null,
            ogAuthor = null,
            ogThumbnailUrl = null,
            ogDescription = null,
            ogGenre = null,
            ogStatus = 0L,
            // SY <--
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = false,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            fetchType = FetchType.Episodes,
            parentId = null,
            seasonFlags = 0L,
            seasonNumber = -1.0,
            seasonSourceOrder = 0L,
        )

        // SY -->
        private val getCustomAnimeInfo: GetCustomAnimeInfo by injectLazy()
        // SY <--
    }
}

package app.anikku.macos.platform.preference

import androidx.compose.runtime.compositionLocalOf

/**
 * Persists bookmarked episode IDs to [MacOSPreferenceStore] so bookmark
 * state survives app restarts.
 *
 * Uses a `StringSet` preference keyed as `"bookmarked_episodes"` where each
 * string element is the string representation of an [EpisodeModel.id].
 *
 * Usage:
 * ```
 * val bookmarkStore = LocalBookmarkStore.current
 * val isBookmarked = bookmarkStore.toggleBookmark(episodeId)
 * ```
 */
class BookmarkStore(
    private val preferenceStore: MacOSPreferenceStore,
) {

    companion object {
        private const val KEY_BOOKMARKED = "bookmarked_episodes"
    }

    private val bookmarkPref = preferenceStore.getStringSet(KEY_BOOKMARKED, emptySet())

    /** Returns the set of currently bookmarked episode IDs. */
    fun getBookmarkedIds(): Set<Long> {
        return bookmarkPref.get().mapNotNull { it.toLongOrNull() }.toSet()
    }

    /**
     * Toggles the bookmark state for [episodeId].
     * @return `true` if the episode is now bookmarked, `false` if unbookmarked.
     */
    fun toggleBookmark(episodeId: Long): Boolean {
        val current = getBookmarkedIds()
        val toggled = if (episodeId in current) {
            current - episodeId
        } else {
            current + episodeId
        }
        bookmarkPref.set(toggled.map { it.toString() }.toSet())
        return episodeId !in current
    }

    /** Returns `true` if the given episode ID is currently bookmarked. */
    fun isBookmarked(episodeId: Long): Boolean = episodeId in getBookmarkedIds()
}

/**
 * CompositionLocal providing the [BookmarkStore] to the Compose tree.
 * Must be provided via CompositionLocalProvider in AnikkuApp.kt.
 */
val LocalBookmarkStore = compositionLocalOf { BookmarkStore(MacOSPreferenceStore(java.io.File(""))) }

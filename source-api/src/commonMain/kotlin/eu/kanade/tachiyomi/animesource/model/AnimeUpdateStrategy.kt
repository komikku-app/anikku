package eu.kanade.tachiyomi.animesource.model

/**
 * Define the update strategy for a single [SAnime].
 * The strategy used will only take effect on the library update.
 *
 * @since extensions-lib 1.4
 */
@Suppress("UNUSED")
enum class AnimeUpdateStrategy {
    /**
     * Series marked as always update will be included in the library
     * update if they aren't excluded by additional restrictions.
     */
    ALWAYS_UPDATE,

    /**
     * Series marked as only fetch once will be automatically skipped
     * during library updates. Useful for cases where the series is previously
     * known to be finished and have only a single chapter, for example.
     */
    ONLY_FETCH_ONCE,
}

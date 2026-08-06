package app.anikku.macos.ui.screens.browse

/**
 * Curated list of extensions verified to work from this app's fleet testing
 * (streaming + playback end-to-end from a typical residential IP, 2026-08).
 *
 * Used in three places so first-time users never have to guess which of the
 * bundled extensions actually work:
 *  - [BrowseTab]'s "Recommended" row (tap to browse the source)
 *  - Onboarding's "Add Sources" step (browse or install each recommendation)
 *  - The palette/settings surfaces that point people at working sources
 *
 * The health badges next to each entry still reflect LIVE per-source checks —
 * this list is the starting point, not a guarantee.
 */
object KnownGoodSources {

    data class Rec(
        val pkgName: String,
        val displayName: String,
    )

    /** Fleet-verified working extensions, best first. */
    val RECOMMENDED: List<Rec> = listOf(
        Rec("eu.kanade.tachiyomi.animeextension.all.animexin", "Animexin"),
        Rec("eu.kanade.tachiyomi.animeextension.all.lmanime", "Lmanime"),
        Rec("eu.kanade.tachiyomi.animeextension.all.subsplease", "SubsPlease"),
        Rec("eu.kanade.tachiyomi.animeextension.en.mkissa", "Mkissa"),
        Rec("eu.kanade.tachiyomi.animeextension.en.oppaistream", "OppaiStream"),
        Rec("eu.kanade.tachiyomi.animeextension.en.anikoto", "Anikoto"),
        Rec("eu.kanade.tachiyomi.animeextension.en.nyaasi", "Nyaa.si"),
        Rec("eu.kanade.tachiyomi.animeextension.en.anidb", "AniDB"),
        Rec("eu.kanade.tachiyomi.animeextension.en.animegg", "Animegg"),
        Rec("eu.kanade.tachiyomi.animeextension.en.cineby", "Cineby"),
    )

    /** Look up a recommendation by its extension pkgName, or null. */
    fun byPkgName(pkgName: String): Rec? =
        RECOMMENDED.firstOrNull { it.pkgName == pkgName }

    /** The pkgNames of every recommendation — for "is this one of ours" checks. */
    val recommendedPkgNames: Set<String> = RECOMMENDED.mapTo(mutableSetOf()) { it.pkgName }
}

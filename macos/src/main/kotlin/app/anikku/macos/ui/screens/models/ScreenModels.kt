package app.anikku.macos.ui.screens.models

/**
 * Local data models for Phase 5 macOS screens.
 *
 * These lightweight models match the fields needed by the UI layer
 * without depending on the shared domain/data modules (which are
 * Android library projects that haven't been wired to the macOS build).
 *
 * When the domain modules are properly included in the macOS build,
 * these can be replaced with direct usage of domain models
 * (Anime, Episode, LibraryAnime, etc.).
 */
data class AnimeModel(
    val id: Long,
    val title: String,
    val source: Long = 0L,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Int = 0,
    val thumbnailUrl: String? = null,
    val url: String? = null,
    val favorite: Boolean = false,
    val coverLastModified: Long = 0L,
)

data class EpisodeModel(
    val id: Long,
    val animeId: Long,
    val name: String,
    val episodeNumber: Double,
    val url: String? = null,
    val seen: Boolean = false,
    val bookmark: Boolean = false,
    val dateUpload: Long = 0,
    val scanlator: String? = null,
    val totalSeconds: Long = 0L,
    val lastSecondSeen: Long = 0L,
)

data class HistoryEntryModel(
    val id: Long,
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeNumber: Double,
    val seenAt: Long = 0L,
    val watchDuration: Long = 0L,
)

data class UpdateModel(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val seen: Boolean = false,
    val scanlator: String? = null,
    val dateFetch: Long = 0L,
)

data class SourceModel(
    val id: Long,
    val name: String,
    val lang: String,
    val isInstalled: Boolean = true,
)

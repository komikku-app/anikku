@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.io.Serializable

interface SAnime : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    // AY -->
    var background_url: String?
    // <-- AY

    var update_strategy: AnimeUpdateStrategy

    // AY -->
    var fetch_type: FetchType

    var season_number: Double
    // <-- AY

    var initialized: Boolean

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    // SY -->
    val originalTitle: String
    val originalAuthor: String?
    val originalArtist: String?
    val originalThumbnailUrl: String?
    val originalDescription: String?
    val originalGenre: String?
    val originalStatus: Int
    // SY <--

    fun copy() = create().also {
        it.url = url
        // SY -->
        it.title = originalTitle
        it.artist = originalArtist
        it.author = originalAuthor
        it.thumbnail_url = originalThumbnailUrl
        it.description = originalDescription
        it.genre = originalGenre
        it.status = originalStatus
        // SY <--
        // AY -->
        it.background_url = background_url
        // <-- AY
        it.update_strategy = update_strategy
        // AY -->
        it.fetch_type = fetch_type
        it.season_number = season_number
        // <-- AY
        it.initialized = initialized
    }

    // SY -->
    fun copy(
        url: String = this.url,
        title: String = this.originalTitle,
        artist: String? = this.originalArtist,
        author: String? = this.originalAuthor,
        description: String? = this.originalDescription,
        genre: String? = this.originalGenre,
        status: Int = this.originalStatus,
        thumbnail_url: String? = this.originalThumbnailUrl,
        // ANK -->
        background_url: String? = this.background_url,
        fetch_type: FetchType = this.fetch_type,
        season_number: Double = this.season_number,
        update_strategy: UpdateStrategy = this.update_strategy,
        // ANK <--
        initialized: Boolean = this.initialized,
    ) = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        // ANK -->
        it.background_url = background_url
        it.fetch_type = fetch_type
        it.season_number = season_number
        it.update_strategy = update_strategy
        // ANK <--
        it.initialized = initialized
    }
    // SY <--

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SAnime {
            return SAnimeImpl()
        }

        // SY -->
        operator fun invoke(
            url: String,
            title: String,
            artist: String? = null,
            author: String? = null,
            description: String? = null,
            genre: String? = null,
            status: Int = 0,
            thumbnail_url: String? = null,
            // ANK -->
            background_url: String? = null,
            fetch_type: FetchType = FetchType.Episodes,
            season_number: Double = -1.0,
            update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            // ANK <--
            initialized: Boolean = false,
        ): SAnime {
            return create().also {
                it.url = url
                it.title = title
                it.artist = artist
                it.author = author
                it.description = description
                it.genre = genre
                it.status = status
                it.thumbnail_url = thumbnail_url
                // ANK -->
                it.background_url = background_url
                it.fetch_type = fetch_type
                it.season_number = season_number
                it.update_strategy = update_strategy
                // ANK <--
                it.initialized = initialized
            }
        }
        // SY <--
    }
}

package com.example.animeextension

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

/**
 * Sample anime source for testing the macOS extension loading pipeline.
 *
 * This source implements the real [AnimeCatalogueSource] interface from the
 * source-api module (compiled against source-api-jvm.jar). It provides hardcoded
 * sample anime data with real playable video URLs so the pipeline can be tested
 * end-to-end without network scraping.
 *
 * Unlike real extension JARs (which bundle their own copy of source-api types),
 * this sample is compiled against the same types as the host application. The
 * pre-built source-api JARs are expected at `../libs/source-api-jvm.jar` and
 * `../libs/common-jvm.jar`.
 */
class SampleAnimeSource : AnimeCatalogueSource {

    override val id: Long = 999002L
    override val name: String = "SampleSource"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    // ── Sample data ────────────────────────────────────────────────

    private data class SampleAnimeData(
        val url: String,
        val title: String,
        val author: String,
        val artist: String,
        val description: String,
        val genre: String,
        val status: Int,
        val thumbnailUrl: String,
    )

    private val sampleAnime = listOf(
        SampleAnimeData(
            url = "/sample/anime/1",
            title = "Starlight Adventures",
            author = "Akira Nakamura",
            artist = "Mariko Tanaka",
            description = "In a world where stars grant magical powers, young Luna discovers she can control the constellations. Together with her friends, she must protect the Star Realm from the encroaching darkness.",
            genre = "Adventure, Fantasy, Magic",
            status = SAnime.ONGOING,
            thumbnailUrl = "https://picsum.photos/seed/starlight/400/600",
        ),
        SampleAnimeData(
            url = "/sample/anime/2",
            title = "Cyber Frontier",
            author = "Kenji Sato",
            artist = "Yuki Mori",
            description = "In the year 2147, humanity has expanded across the solar system. A young hacker discovers a conspiracy that threatens the fragile peace between Earth and Mars colonies.",
            genre = "Sci-Fi, Action, Thriller",
            status = SAnime.ONGOING,
            thumbnailUrl = "https://picsum.photos/seed/cyber/400/600",
        ),
        SampleAnimeData(
            url = "/sample/anime/3",
            title = "Whisker Chronicles",
            author = "Hana Yoshida",
            artist = "Taro Suzuki",
            description = "A heartwarming tale of a cat who can talk and the lonely librarian who befriends him. Together, they solve mysteries in their quaint countryside town.",
            genre = "Slice of Life, Mystery, Comedy",
            status = SAnime.COMPLETED,
            thumbnailUrl = "https://picsum.photos/seed/whisker/400/600",
        ),
        SampleAnimeData(
            url = "/sample/anime/4",
            title = "Crimson Samurai",
            author = "Ryo Hayashi",
            artist = "Aoi Kobayashi",
            description = "During the Sengoku period, a disgraced samurai seeks redemption by protecting a village from bandits. But an ancient evil lurks in the nearby forest.",
            genre = "Historical, Action, Supernatural",
            status = SAnime.COMPLETED,
            thumbnailUrl = "https://picsum.photos/seed/crimson/400/600",
        ),
        SampleAnimeData(
            url = "/sample/anime/5",
            title = "Neon Dreams",
            author = "Mizuki Fujimoto",
            artist = "Sora Yamamoto",
            description = "In a neon-lit cyberpunk city, a street musician discovers her songs can manipulate digital reality. She joins forces with a rogue AI to take down the corrupt megacorporation.",
            genre = "Cyberpunk, Music, Drama",
            status = SAnime.ON_HIATUS,
            thumbnailUrl = "https://picsum.photos/seed/neon/400/600",
        ),
    )

    // ── CatalogueSource implementation ─────────────────────────────

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val data = sampleAnime.find { it.url == anime.url }
            ?: return SAnime.create().apply {
                url = anime.url
                title = anime.title
                initialized = false
            }

        return SAnime.create().apply {
            url = data.url
            title = data.title
            author = data.author
            artist = data.artist
            description = data.description
            genre = data.genre
            status = data.status
            thumbnail_url = data.thumbnailUrl
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // Return 12 episodes for any anime
        return (1..12).map { i ->
            SEpisode.create().apply {
                url = "${anime.url}/episode/$i"
                name = "Episode $i"
                episode_number = i.toFloat()
                date_upload = 1700000000000L + (i * 604_800_000L) // weekly uploads
                scanlator = "SampleScan"
            }
        }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        // Return real playable public-domain video URLs (Big Buck Bunny variants)
        return listOf(
            Video(
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                videoTitle = "1080p",
                resolution = 1080,
                headers = null,
                preferred = true,
            ),
            Video(
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                videoTitle = "720p",
                resolution = 720,
                headers = null,
                preferred = false,
            ),
        )
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return AnimesPage(
            animes = sampleAnime.map { data ->
                SAnime.create().apply {
                    url = data.url
                    title = data.title
                    thumbnail_url = data.thumbnailUrl
                    status = data.status
                    initialized = true
                }
            },
            hasNextPage = false,
        )
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val filtered = sampleAnime.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true) ||
                it.genre.contains(query, ignoreCase = true)
        }

        return AnimesPage(
            animes = filtered.map { data ->
                SAnime.create().apply {
                    url = data.url
                    title = data.title
                    thumbnail_url = data.thumbnailUrl
                    status = data.status
                    initialized = true
                }
            },
            hasNextPage = false,
        )
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // ── RxJava stubs (required by AnimeCatalogueSource) ────────────

    @Deprecated("Use suspend API", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")
}

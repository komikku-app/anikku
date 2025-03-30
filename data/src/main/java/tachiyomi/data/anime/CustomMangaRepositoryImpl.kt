package tachiyomi.data.anime

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import java.io.File

class CustomMangaRepositoryImpl(context: Context) : CustomMangaRepository {
    private val editJson = File(context.getExternalFilesDir(null), "edits.json")

    private val customAnimeMap = fetchCustomData()

    override fun get(animeId: Long) = customAnimeMap[animeId]

    private fun fetchCustomData(): MutableMap<Long, CustomMangaInfo> {
        if (!editJson.exists() || !editJson.isFile) return mutableMapOf()

        val json = try {
            Json.decodeFromString<AnimeList>(
                editJson.bufferedReader().use { it.readText() },
            )
        } catch (e: Exception) {
            null
        } ?: return mutableMapOf()

        val animesJson = json.animes ?: return mutableMapOf()
        return animesJson
            .mapNotNull { animeJson ->
                val id = animeJson.id ?: return@mapNotNull null
                id to animeJson.toAnime()
            }
            .toMap()
            .toMutableMap()
    }

    override fun set(animeInfo: CustomMangaInfo) {
        if (
            animeInfo.title == null &&
            animeInfo.author == null &&
            animeInfo.artist == null &&
            animeInfo.thumbnailUrl == null &&
            animeInfo.description == null &&
            animeInfo.genre == null &&
            animeInfo.status == null
        ) {
            customAnimeMap.remove(animeInfo.id)
        } else {
            customAnimeMap[animeInfo.id] = animeInfo
        }
        saveCustomInfo()
    }

    private fun saveCustomInfo() {
        val jsonElements = customAnimeMap.values.map { it.toJson() }
        if (jsonElements.isNotEmpty()) {
            editJson.delete()
            editJson.writeText(Json.encodeToString(AnimeList(jsonElements)))
        }
    }

    @Serializable
    data class AnimeList(
        val animes: List<AnimeJson>? = null,
    )

    @Serializable
    data class AnimeJson(
        var id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    ) {

        fun toAnime() = CustomMangaInfo(
            id = this@AnimeJson.id!!,
            title = this@AnimeJson.title?.takeUnless { it.isBlank() },
            author = this@AnimeJson.author,
            artist = this@AnimeJson.artist,
            thumbnailUrl = this@AnimeJson.thumbnailUrl,
            description = this@AnimeJson.description,
            genre = this@AnimeJson.genre,
            status = this@AnimeJson.status?.takeUnless { it == 0L },
        )
    }

    private fun CustomMangaInfo.toJson(): AnimeJson {
        return AnimeJson(
            id,
            title,
            author,
            artist,
            thumbnailUrl,
            description,
            genre,
            status,
        )
    }
}

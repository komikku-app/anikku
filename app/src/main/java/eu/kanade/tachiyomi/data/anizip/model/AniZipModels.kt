package eu.kanade.tachiyomi.data.anizip.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class AniZipResponse(
    val titles: Map<String, String>? = null,
    val episodes: Map<String, AniZipEpisode>? = null,
    val episodeCount: Int? = null,
    val specialCount: Int? = null,
    val images: List<AniZipImage>? = null,
    val mappings: AniZipMappings? = null,
)

@Serializable
data class AniZipEpisode(
    val episode: JsonElement? = null,
    val title: JsonElement? = null,
    val overview: String? = null,
    val summary: String? = null,
    val image: String? = null,
    val airDate: String? = null,
    @SerialName("airdate") val airdate: String? = null,
    val runtime: Int? = null,
    val length: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val absoluteEpisodeNumber: Int? = null,
    val rating: JsonElement? = null,
    val anidbEid: Int? = null,
    val tvdbId: Int? = null,
    val tvdbShowId: Int? = null,
) {
    fun getPreferredTitle(): String? {
        val t = title ?: return null
        return when (t) {
            is JsonObject -> {
                t["en"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                    ?: t["x-jat"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                    ?: t["ja"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                    ?: t.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
            }
            is JsonPrimitive -> t.contentOrNull
            else -> null
        }?.trim()?.ifBlank { null }
    }

    val bestOverview: String?
        get() = overview?.trim()?.ifBlank { null } ?: summary?.trim()?.ifBlank { null }

    val bestAirDate: String?
        get() = airDate?.trim()?.ifBlank { null } ?: airdate?.trim()?.ifBlank { null }

    val formattedRating: String?
        get() {
            val raw = (rating as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
            if (raw.isBlank()) return null
            val doubleVal = raw.toDoubleOrNull() ?: return raw
            return if (doubleVal == 0.0) null else String.format("%.2f", doubleVal).trimEnd('0').trimEnd('.')
        }
}

@Serializable
data class AniZipImage(
    val coverType: String? = null,
    val url: String? = null,
)

@Serializable
data class AniZipMappings(
    @SerialName("animeplanet_id") val animeplanetId: String? = null,
    @SerialName("kitsu_id") val kitsuId: Long? = null,
    @SerialName("mal_id") val malId: Long? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("anilist_id") val anilistId: Long? = null,
    @SerialName("anisearch_id") val anisearchId: Long? = null,
    @SerialName("anidb_id") val anidbId: Long? = null,
    @SerialName("notifymoe_id") val notifymoeId: String? = null,
    @SerialName("livechart_id") val livechartId: Long? = null,
    @SerialName("thetvdb_id") val tvdbId: Long? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("themoviedb_id") val tmdbId: String? = null,
)

data class AniZipEpisodeMeta(
    val episodeNumber: String,
    val title: String? = null,
    val overview: String? = null,
    val image: String? = null,
    val rating: String? = null,
    val airDate: String? = null,
    val airDateMillis: Long? = null,
)

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.anikku.macos.platform.backup

import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File
import java.util.zip.GZIPInputStream

/** Decoder for the gzip + kotlinx.serialization ProtoBuf format used by Anikku Android. */
internal object AndroidBackupCodec {
    private const val GZIP_ID_1 = 0x1f
    private const val GZIP_ID_2 = 0x8b

    fun isAndroidBackup(file: File): Boolean {
        if (file.extension.equals("tachibk", ignoreCase = true)) return true
        return file.inputStream().buffered().use { input ->
            input.mark(2)
            input.read() == GZIP_ID_1 && input.read() == GZIP_ID_2
        }
    }

    fun decode(
        file: File,
        currentCategories: List<CategoryEntry>,
    ): MacOSBackupManager.BackupData {
        val encoded = file.inputStream().buffered().use { input ->
            input.mark(2)
            val gzip = input.read() == GZIP_ID_1 && input.read() == GZIP_ID_2
            input.reset()
            (if (gzip) GZIPInputStream(input) else input).use { it.readBytes() }
        }
        require(encoded.isNotEmpty()) { "Android backup is empty" }
        val android = ProtoBuf.decodeFromByteArray(AndroidBackup.serializer(), encoded)

        val existingByName = currentCategories.associateBy { it.name }
        var nextCategoryId = (currentCategories.maxOfOrNull { it.id } ?: CATEGORY_DEFAULT_ID) + 1L
        val importedCategories = android.categories
            .sortedBy { it.order }
            .map { category ->
                existingByName[category.name] ?: CategoryEntry(
                    id = nextCategoryId++,
                    name = category.name,
                    order = category.order,
                    hidden = category.hidden,
                )
            }
        val categoryIdByOrder = android.categories
            .associate { backupCategory ->
                backupCategory.order to (
                    importedCategories.firstOrNull { it.name == backupCategory.name }?.id
                        ?: CATEGORY_DEFAULT_ID
                    )
            }

        val library = android.anime
            .filter { it.favorite }
            .map { anime ->
                val latestEpisode = anime.episodes.maxByOrNull { it.episodeNumber }
                val latestHistory = anime.history.maxByOrNull { it.lastRead }
                val resumeEpisode = latestHistory?.let { history ->
                    anime.episodes.firstOrNull { it.url == history.url }
                }
                MacOSBackupManager.BackupLibraryEntry(
                    animeId = stableId(anime.url),
                    title = anime.customTitle ?: anime.title,
                    sourceId = anime.source,
                    url = anime.url,
                    thumbnailUrl = anime.thumbnailUrl,
                    author = anime.customAuthor ?: anime.author,
                    artist = anime.customArtist ?: anime.artist,
                    description = anime.customDescription ?: anime.description,
                    genre = anime.customGenre ?: anime.genre,
                    status = anime.customStatus.takeUnless { it == 0 } ?: anime.status,
                    categoryId = anime.categories.firstNotNullOfOrNull(categoryIdByOrder::get)
                        ?: CATEGORY_DEFAULT_ID,
                    lastSecondSeen = resumeEpisode?.lastSecondSeen ?: 0L,
                    totalSeconds = resumeEpisode?.totalSeconds ?: 0L,
                    latestEpisodeNumber = latestEpisode?.episodeNumber?.toDouble() ?: 0.0,
                    latestEpisodeName = latestEpisode?.name,
                    unseenEpisodeCount = anime.episodes.count { !it.seen },
                    addedAt = anime.dateAdded,
                    lastUpdatedAt = anime.lastModifiedAt,
                )
            }

        val history = android.anime.flatMap { anime ->
            val episodesByUrl = anime.episodes.associateBy { it.url }
            anime.history.map { item ->
                val episode = episodesByUrl[item.url]
                MacOSBackupManager.BackupHistoryEntry(
                    animeId = stableId(anime.url),
                    episodeId = stableId(item.url),
                    animeTitle = anime.customTitle ?: anime.title,
                    episodeName = episode?.name.orEmpty(),
                    episodeNumber = episode?.episodeNumber?.toDouble() ?: 0.0,
                    sourceId = anime.source,
                    animeUrl = anime.url,
                    episodeUrl = item.url,
                    seenAt = item.lastRead,
                    watchDuration = item.readDuration,
                    lastSecondSeen = episode?.lastSecondSeen ?: 0L,
                    totalSeconds = episode?.totalSeconds ?: 0L,
                )
            }
        }

        val customAnime = android.anime.mapNotNull { anime ->
            if (
                anime.customTitle == null && anime.customAuthor == null && anime.customArtist == null &&
                anime.customDescription == null && anime.customGenre == null && anime.customStatus == 0
            ) {
                null
            } else {
                MacOSBackupManager.BackupCustomAnimeEntry(
                    id = stableId(anime.url),
                    title = anime.customTitle,
                    author = anime.customAuthor,
                    artist = anime.customArtist,
                    description = anime.customDescription,
                    genre = anime.customGenre,
                    status = anime.customStatus.takeUnless { it == 0 }?.toLong(),
                )
            }
        }

        return MacOSBackupManager.BackupData(
            appName = "Anikku Android import",
            library = library,
            categories = importedCategories.map {
                MacOSBackupManager.BackupCategoryEntry(it.id, it.name, it.order, it.isDefault, it.hidden)
            },
            history = history,
            downloads = emptyList(),
            customAnime = customAnime,
        )
    }

    private fun stableId(url: String): Long = url.hashCode().toLong().let { if (it == 0L) 1L else it }
}

@Serializable
private data class AndroidBackup(
    @ProtoNumber(3) val anime: List<AndroidBackupAnime> = emptyList(),
    @ProtoNumber(4) val categories: List<AndroidBackupCategory> = emptyList(),
)

@Serializable
private data class AndroidBackupAnime(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0L,
    @ProtoNumber(16) val episodes: List<AndroidBackupEpisode> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(104) val history: List<AndroidBackupHistory> = emptyList(),
    @ProtoNumber(106) val lastModifiedAt: Long = 0L,
    @ProtoNumber(602) val customStatus: Int = 0,
    @ProtoNumber(800) val customTitle: String? = null,
    @ProtoNumber(801) val customArtist: String? = null,
    @ProtoNumber(802) val customAuthor: String? = null,
    @ProtoNumber(804) val customDescription: String? = null,
    @ProtoNumber(805) val customGenre: List<String>? = null,
)

@Serializable
private data class AndroidBackupEpisode(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val seen: Boolean = false,
    @ProtoNumber(6) val lastSecondSeen: Long = 0L,
    @ProtoNumber(16) val totalSeconds: Long = 0L,
    @ProtoNumber(9) val episodeNumber: Float = 0f,
)

@Serializable
private data class AndroidBackupHistory(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val lastRead: Long,
    @ProtoNumber(3) val readDuration: Long = 0L,
)

@Serializable
private data class AndroidBackupCategory(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val order: Long = 0L,
    @ProtoNumber(900) val hidden: Boolean = false,
)

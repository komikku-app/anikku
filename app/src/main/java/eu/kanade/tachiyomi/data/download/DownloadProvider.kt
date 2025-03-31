package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.size
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<episode>
 *
 * @param context the application context.
 */
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    // AM (FILE_SIZE) -->
    private val localFileSystem: LocalSourceFileSystem = Injekt.get(),
    // <-- AM (FILE_SIZE)
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for an manga. For internal use only.
     *
     * @param animeTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    internal fun getAnimeDir(animeTitle: String, source: Source): UniFile {
        try {
            return downloadsDir!!
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getAnimeDirName(animeTitle))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(
                context.stringResource(
                    MR.strings.invalid_location,
                    downloadsDir?.displayablePath ?: "",
                ),
            )
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: Source): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for an manga if it exists.
     *
     * @param animeTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    fun findAnimeDir(animeTitle: String, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getAnimeDirName(animeTitle))
    }

    /**
     * Returns the download directory for an episode if it exists.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param animeTitle the title of the manga to query.
     * @param source the source of the episode.
     */
    fun findEpisodeDir(
        episodeName: String,
        episodeScanlator: String?,
        animeTitle: String,
        source: Source,
    ): UniFile? {
        val animeDir = findAnimeDir(animeTitle, source)
        return getValidEpisodeDirNames(episodeName, episodeScanlator).asSequence()
            .mapNotNull { animeDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the episodes that exist.
     *
     * @param chapters the episodes to query.
     * @param manga the manga of the episode.
     * @param source the source of the episode.
     */
    fun findEpisodeDirs(chapters: List<Chapter>, manga: Manga, source: Source): Pair<UniFile?, List<UniFile>> {
        val animeDir = findAnimeDir(manga.title, source) ?: return null to emptyList()
        return animeDir to chapters.mapNotNull { episode ->
            getValidEpisodeDirNames(episode.name, episode.scanlator).asSequence()
                .mapNotNull { animeDir.findFile(it) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    /**
     * Returns the download directory name for an manga.
     *
     * @param animeTitle the title of the manga to query.
     */
    fun getAnimeDirName(animeTitle: String): String {
        return DiskUtil.buildValidFilename(animeTitle)
    }

    /**
     * Returns the episode directory name for an episode.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     */
    fun getEpisodeDirName(episodeName: String, episodeScanlator: String?): String {
        val newEpisodeName = sanitizeEpisodeName(episodeName)
        return DiskUtil.buildValidFilename(
            when {
                !episodeScanlator.isNullOrBlank() -> "${episodeScanlator}_$newEpisodeName"
                else -> newEpisodeName
            },
        )
    }

    /**
     * Return the new name for the episode (in case it's empty or blank)
     *
     * @param episodeName the name of the episode
     */
    private fun sanitizeEpisodeName(episodeName: String): String {
        return episodeName.ifBlank {
            "Episode"
        }
    }

    /**
     * Returns the episode directory name for an episode.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     */
    fun getOldEpisodeDirName(episodeName: String, episodeScanlator: String?): String {
        return DiskUtil.buildValidFilename(
            when {
                episodeScanlator != null -> "${episodeScanlator}_$episodeName"
                else -> episodeName
            },
        )
    }

    fun isEpisodeDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return oldChapter.name != newChapter.name ||
            oldChapter.scanlator?.takeIf { it.isNotBlank() } != newChapter.scanlator?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns valid downloaded episode directory names.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     */
    fun getValidEpisodeDirNames(episodeName: String, episodeScanlator: String?): List<String> {
        val episodeDirName = getEpisodeDirName(episodeName, episodeScanlator)
        val oldEpisodeDirName = getOldEpisodeDirName(episodeName, episodeScanlator)
        return listOf(episodeDirName, oldEpisodeDirName)
    }

    // AM (FILE_SIZE) -->
    /**
     * Returns an episode file size in bytes.
     * Returns null if the episode is not found in expected location
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param animeTitle the title of the manga
     * @param source the source of the manga
     */
    fun getEpisodeFileSize(
        episodeName: String,
        episodeUrl: String?,
        episodeScanlator: String?,
        animeTitle: String,
        source: Source?,
    ): Long? {
        if (source == null) return null
        return if (source.isLocal()) {
            val (animeDirName, episodeDirName) = episodeUrl?.split('/', limit = 2) ?: return null
            localFileSystem.getBaseDirectory()?.findFile(animeDirName)?.findFile(episodeDirName)?.size()
        } else {
            findEpisodeDir(episodeName, episodeScanlator, animeTitle, source)?.size()
        }
    }
    // <-- AM (FILE_SIZE)
}

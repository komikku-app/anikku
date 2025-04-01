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
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
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
     * Returns the download directory for a manga. For internal use only.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(mangaTitle: String, source: Source): UniFile {
        try {
            return downloadsDir!!
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getMangaDirName(mangaTitle))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(
                context.stringResource(
                    MR.strings.invalid_location,
                    (downloadsDir?.displayablePath ?: "") +
                        "/${getSourceDirName(source)}/${getMangaDirName(mangaTitle)}",
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
     * Returns the download directory for a manga if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    fun findMangaDir(mangaTitle: String, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getMangaDirName(mangaTitle))
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the chapter.
     */
    fun findChapterDir(chapterName: String, chapterScanlator: String?, mangaTitle: String, source: Source): UniFile? {
        val mangaDir = findMangaDir(mangaTitle, source)
        return getValidChapterDirNames(chapterName, chapterScanlator).asSequence()
            .mapNotNull { mangaDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): Pair<UniFile?, List<UniFile>> {
        val mangaDir = findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source) ?: return null to emptyList()
        return mangaDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator).asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
        }
    }

    // SY -->
    /**
     * Returns a list of all files in manga directory
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findUnmatchedChapterDirs(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
    ): List<UniFile> {
        val mangaDir = findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source) ?: return emptyList()
        return mangaDir.listFiles().orEmpty().asList().filter {
            chapters.find { chp ->
                getValidChapterDirNames(chp.name, chp.scanlator).any { dir ->
                    mangaDir.findFile(dir) != null
                }
            } == null ||
                it.name?.endsWith(Downloader.TMP_DIR_SUFFIX) == true
        }
    }
    // SY <--

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun getMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(mangaTitle)
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getChapterDirName(chapterName: String, chapterScanlator: String?): String {
        val newChapterName = sanitizeChapterName(chapterName)
        return DiskUtil.buildValidFilename(
            when {
                !chapterScanlator.isNullOrBlank() -> "${chapterScanlator}_$newChapterName"
                else -> newChapterName
            },
        )
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Episode"
        }
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    private fun getOldChapterDirName(chapterName: String, chapterScanlator: String?): String {
        return DiskUtil.buildValidFilename(
            when {
                chapterScanlator != null -> "${chapterScanlator}_$chapterName"
                else -> chapterName
            },
        )
    }

    fun isEpisodeDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return oldChapter.name != newChapter.name ||
            oldChapter.scanlator?.takeIf { it.isNotBlank() } != newChapter.scanlator?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?): List<String> {
        val chapterDirName = getChapterDirName(chapterName, chapterScanlator)
        val oldChapterDirName = getOldChapterDirName(chapterName, chapterScanlator)
        return listOf(chapterDirName, oldChapterDirName)
    }

    // AM (FILE_SIZE) -->
    /**
     * Returns a chapter file size in bytes.
     * Returns null if the chapter is not found in expected location
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga
     * @param source the source of the manga
     */
    fun getChapterFileSize(
        chapterName: String,
        chapterUrl: String?,
        chapterScanlator: String?,
        mangaTitle: String,
        source: Source?,
    ): Long? {
        if (source == null) return null
        return if (source.isLocal()) {
            val (mangaDirName, chapterDirName) = chapterUrl?.split('/', limit = 2) ?: return null
            localFileSystem.getBaseDirectory()?.findFile(mangaDirName)?.findFile(chapterDirName)?.size()
        } else {
            findChapterDir(chapterName, chapterScanlator, mangaTitle, source)?.size()
        }
    }
    // <-- AM (FILE_SIZE)
}

package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.size
import eu.kanade.tachiyomi.util.storage.DiskUtil
import exh.log.xLogE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to manage chapter downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new chapters or query
 * downloaded chapters.
 */
class DownloadManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val provider: DownloadProvider = Injekt.get(),
    private val cache: DownloadCache = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    /**
     * Downloader whose only task is to download chapters.
     */
    private val downloader = Downloader(context, provider, cache, sourceManager)

    val isRunning: Boolean
        get() = downloader.isRunning

    /**
     * Queue to delay the deletion of a list of chapters until triggered.
     */
    private val pendingDeleter = DownloadPendingDeleter(context)

    val queueState
        get() = downloader.queueState

    // For use by DownloadService only
    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = DownloadJob.isRunningFlow(context)

    /**
     * Tells the downloader to begin downloads.
     */
    fun startDownloads() {
        if (downloader.isRunning) return

        if (DownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            DownloadJob.start(context)
        }
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
        downloader.stop()
    }

    /**
     * Empties the download queue.
     */
    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    /**
     * Returns the download from queue if the chapter is queued for download
     * else it will return null which means that the chapter is not queued for download
     *
     * @param chapterId the chapter to check.
     */
    fun getQueuedDownloadOrNull(chapterId: Long): Download? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun startDownloadNow(chapterId: Long) {
        val existingDownload = getQueuedDownloadOrNull(chapterId)
        // If not in queue try to start a new download
        val toAdd = existingDownload ?: runBlocking { Download.fromChapterId(chapterId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        downloader.updateQueue(downloads)
    }

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param manga the manga of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueuing the chapters.
     * @param altDownloader whether to use the alternative downloader
     */
    fun downloadChapters(
        manga: Manga,
        chapters: List<Chapter>,
        autoStart: Boolean = true,
        altDownloader: Boolean = false,
        video: Video? = null,
    ) {
        // AM (FILLERMARK) -->
        val filteredChapters = getChaptersToDownload(chapters)
        downloader.queueEpisodes(manga, filteredChapters, autoStart, altDownloader, video)
        // <-- AM (FILLERMARK)
    }
    fun downloadEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        autoStart: Boolean = true,
        altDownloader: Boolean = false,
        video: Video? = null,
    ) = downloadChapters(anime, episodes, autoStart, altDownloader, video)

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<Download>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!DownloadJob.isRunning(context)) startDownloads()
    }

    /**
     * Builds the page list of a downloaded episode.
     *
     * @param source the source of the episode.
     * @param anime the anime of the episode.
     * @param episode the downloaded episode.
     * @return an observable containing the list of pages from the episode.
     */
    fun buildVideo(source: Source, anime: Anime, episode: Episode): Video {
        val episodeDir =
            provider.findChapterDir(
                episode.name,
                episode.scanlator,
                // SY -->
                anime.ogTitle,
                // SY <--
                source,
            )
        val files = episodeDir?.listFiles().orEmpty()
            .filter { "video" in it.type.orEmpty() }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.video_list_empty_error))
        }

        val file = files[0]

        return Video(
            file.uri.toString(),
            "download: " + file.uri.toString(),
            file.uri.toString(),
            file.uri,
        ).apply { status = Video.State.Ready }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(chapterName, chapterScanlator, mangaTitle, sourceId, skipCache)
    }
    fun isEpisodeDownloaded(
        episodeName: String,
        episodeScanlator: String?,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ) = isChapterDownloaded(episodeName, episodeScanlator, animeTitle, sourceId, skipCache)

    /**
     * Returns the amount of downloaded chapters.
     */
    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    /**
     * Returns the amount of downloaded/local chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return if (manga.source == LocalSource.ID) {
            LocalSourceFileSystem(storageManager).getFilesInMangaDirectory(manga.url)
                .count { Archive.isSupported(it) }
        } else {
            cache.getDownloadCount(manga)
        }
    }

    /**
     * Returns the size of downloaded episodes.
     */
    fun getDownloadSize(): Long {
        return cache.getTotalDownloadSize()
    }

    /**
     * Returns the size of downloaded/local episodes for an manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadSize(manga: Manga): Long {
        return if (manga.source == LocalSource.ID) {
            LocalSourceFileSystem(storageManager).getMangaDirectory(manga.url)
                ?.size() ?: 0L
        } else {
            cache.getDownloadSize(manga)
        }
    }

    fun cancelQueuedDownloads(downloads: List<Download>) {
        removeFromDownloadQueue(downloads.map { it.chapter })
    }

    /**
     * Deletes the directories of a list of downloaded chapters.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun deleteChapters(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
        // KMK -->
        /** Ignore categories exclusion */
        ignoreCategoryExclusion: Boolean = false,
        // KMK <--
    ) {
        launchIO {
            val filteredChapters = getChaptersToDelete(
                chapters,
                manga,
                // KMK -->
                ignoreCategoryExclusion,
                // KMK <--
            )
            if (filteredChapters.isEmpty()) {
                return@launchIO
            }

            removeFromDownloadQueue(filteredChapters)

            val (mangaDir, chapterDirs) = provider.findChapterDirs(filteredChapters, manga, source)
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, manga)

            // Delete manga directory if empty
            if (mangaDir?.listFiles()?.isEmpty() == true) {
                deleteManga(manga, source, removeQueued = false)
            }
        }
    }

    /**
     * Deletes the directory of a downloaded manga.
     *
     * @param manga the manga to delete.
     * @param source the source of the manga.
     * @param removeQueued whether to also remove queued downloads.
     */
    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(manga)
            }
            provider.findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)?.delete()
            cache.removeManga(manga)

            // Delete source directory if empty
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(chapters: List<Chapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(chapters)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    // SY -->
    /**
     * return the list of all manga folders
     */
    fun getMangaFolders(source: Source): List<UniFile> {
        return provider.findSourceDir(source)?.listFiles()?.toList().orEmpty()
    }

    /**
     * Deletes the directories of chapters that were read or have no match
     *
     * @param allChapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    suspend fun cleanupChapters(
        allChapters: List<Chapter>,
        manga: Manga,
        source: Source,
        removeRead: Boolean,
        removeNonFavorite: Boolean,
    ): Int {
        var cleaned = 0

        if (removeNonFavorite && !manga.favorite) {
            val mangaFolder = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source).getOrElse { e ->
                logcat(LogPriority.ERROR, e) { "Manga download folder doesn't exist." }
                return 0
            }
            cleaned += 1 + mangaFolder.listFiles().orEmpty().size
            mangaFolder.delete()
            cache.removeManga(manga)
            return cleaned
        }

        val filesWithNoChapter = provider.findUnmatchedChapterDirs(allChapters, manga, source)
        cleaned += filesWithNoChapter.size
        cache.removeFolders(filesWithNoChapter.mapNotNull { it.name }, manga)
        filesWithNoChapter.forEach { it.delete() }

        if (removeRead) {
            val readChapters = allChapters.filter { it.read }
            val readChapterDirs = provider.findChapterDirs(readChapters, manga, source)
            readChapterDirs.second.forEach { it.delete() }
            cleaned += readChapterDirs.second.size
            cache.removeChapters(readChapters, manga)
        }

        if (cache.getDownloadCount(manga) == 0) {
            val mangaFolder = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source).getOrElse { e ->
                logcat(LogPriority.ERROR, e) { "Manga download folder doesn't exist." }
                return cleaned
            }
            if (!mangaFolder.listFiles().isNullOrEmpty()) {
                mangaFolder.delete()
                cache.removeManga(manga)
            } else {
                xLogE("Cache and download folder doesn't match for " + /* SY --> */ manga.ogTitle /* SY <-- */)
            }
        }
        return cleaned
    }
    // SY <--

    /**
     * Adds a list of chapters to be deleted later.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     */
    suspend fun enqueueEpisodesToDelete(chapters: List<Chapter>, manga: Manga) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, manga), manga)
    }

    /**
     * Triggers the execution of the deletion of pending chapters.
     */
    fun deletePendingEpisodes() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Renames source download folder
     *
     * @param oldSource the old source.
     * @param newSource the new source.
     */
    fun renameSource(oldSource: Source, newSource: Source) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + Downloader.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (!oldFolder.renameTo(newName)) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    /**
     * Renames an already downloaded chapter
     *
     * @param source the source of the manga.
     * @param manga the manga of the chapter.
     * @param oldChapter the existing chapter with the old name.
     * @param newChapter the target chapter with the new name.
     */
    suspend fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {
        val oldNames = provider.getValidChapterDirNames(oldChapter.name, oldChapter.scanlator)
        val mangaDir = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source).getOrElse { e ->
            logcat(LogPriority.ERROR, e) { "Manga download folder doesn't exist. Skipping renaming after source sync" }
            return
        }

        // Assume there's only 1 version of the chapter name formats present
        val oldDownload = oldNames.asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull() ?: return

        val newName = provider.getChapterDirName(newChapter.name, newChapter.scanlator)

        if (oldDownload.name == newName) return

        if (oldDownload.renameTo(newName)) {
            cache.removeChapter(oldChapter, manga)
            cache.addChapter(newName, mangaDir, manga)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded episode: ${oldNames.joinToString()}" }
        }
    }

    private suspend fun getChaptersToDelete(
        chapters: List<Chapter>,
        manga: Manga,
        // KMK -->
        /** Ignore categories exclusion */
        ignoreCategoryExclusion: Boolean = false,
        // KMK <--
    ): List<Chapter> {
        // KMK -->
        val filteredCategoryManga = if (ignoreCategoryExclusion) {
            chapters
        } else {
            // KMK <--
            // Retrieve the categories that are set to exclude from being deleted on read
            val categoriesToExclude = downloadPreferences.removeExcludeCategories().get().map(String::toLong).toSet()

            val categoriesForManga = getCategories.await(manga.id)
                .map { it.id }
                .ifEmpty { listOf(0) }
            if (categoriesForManga.intersect(categoriesToExclude).isNotEmpty()) {
                chapters.filterNot { it.read }
            } else {
                chapters
            }
        }

        return if (!downloadPreferences.removeBookmarkedChapters().get() &&
            // KMK -->
            // if manually deleting single chapter then will allow deleting bookmark chapter
            (chapters.size > 1 || !ignoreCategoryExclusion)
            // KMK <--
        ) {
            filteredCategoryManga.filterNot { it.bookmark }
        } else {
            filteredCategoryManga
        }
    }

    // AM (FILLERMARK) -->
    private fun getChaptersToDownload(chapters: List<Chapter>): List<Chapter> {
        return if (!downloadPreferences.notDownloadFillermarkedItems().get()) {
            chapters.filterNot { it.fillermark }
        } else {
            chapters
        }
    }
    // <-- AM (FILLERMARK)

    fun statusFlow(): Flow<Download> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == Download.State.DOWNLOADING }.asFlow(),
            )
        }

    fun progressFlow(): Flow<Download> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == Download.State.DOWNLOADING }
                    .asFlow(),
            )
        }

    fun renameMangaDir(oldTitle: String, newTitle: String, source: Long) {
        val sourceDir = provider.findSourceDir(sourceManager.getOrStub(source)) ?: return
        val mangaDir = sourceDir.findFile(DiskUtil.buildValidFilename(oldTitle)) ?: return
        mangaDir.renameTo(DiskUtil.buildValidFilename(newTitle))
    }
}

package app.anikku.macos.platform.download

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import app.anikku.macos.player.MPVLib
import app.anikku.macos.player.TorrentStreamingCoordinator
import app.anikku.macos.player.TorrentStreamingResult
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * macOS download manager.
 *
 * Downloads are written below [MacOSStorageProvider.downloadsDirectory]/videos
 * using a unique `.part` file and are exposed as completed only after an atomic
 * move succeeds. The resulting file's parent is the same directory consumed by
 * [app.anikku.macos.platform.media.MacOSHttpServer] for offline playback.
 */
open class MacOSDownloadManager(
    private val repository: DownloadRepository,
    private val extensionManager: MacOSExtensionManager,
    private val storageProvider: MacOSStorageProvider,
    private val notifier: MacOSNotificationManager,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private val downloadLock = Any()
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeCalls = ConcurrentHashMap<Long, Call>()
    private val temporaryFiles = ConcurrentHashMap<Long, File>()
    private val attemptTokens = ConcurrentHashMap<Long, String>()
    /**
     * Torrent engine for magnet downloads (TorrServer with WebTorrent fallback).
     * A second TorrServer instance (while the player streams) falls back to
     * WebTorrent automatically — both backends produce a local HTTP URL the
     * regular download loop can fetch.
     */
    private val torrentCoordinator = TorrentStreamingCoordinator()
    @Volatile
    private var closed = false
    private val _downloads = MutableStateFlow(repository.getAll())
    open val downloads: StateFlow<List<DownloadRepository.DownloadEntry>> = _downloads.asStateFlow()

    /**
     * Maximum number of downloads that run in parallel (1-10). Backed by the
     * user's "Simultaneous downloads" setting — each download builds a fresh
     * [limitedParallelism] dispatcher, so changes apply live to new starts.
     */
    var maxConcurrentDownloads: Int = MAX_CONCURRENT_DOWNLOADS
        set(value) {
            field = value.coerceIn(1, 10)
        }

    init {
        resumePendingDownloads()
    }

    /** Enqueue an episode unless an existing non-error entry already owns it. */
    fun enqueue(
        animeId: Long,
        sourceId: Long,
        animeTitle: String,
        episodeName: String,
        episodeNumber: Double,
        episodeUrl: String?,
        coverUrl: String? = null,
    ): DownloadRepository.DownloadEntry {
        synchronized(downloadLock) {
            check(!closed) { "Download manager is closed" }
            val existing = repository.getAll().firstOrNull {
                it.animeId == animeId &&
                    it.episodeNumber == episodeNumber &&
                    it.status != DownloadRepository.DownloadStatus.ERROR &&
                    (it.status != DownloadRepository.DownloadStatus.COMPLETED ||
                        it.filePath?.let { path -> File(path).isFile } == true)
            }
            if (existing != null) return existing

            val entry = repository.enqueue(
                animeId = animeId,
                sourceId = sourceId,
                animeTitle = animeTitle,
                episodeName = episodeName,
                episodeNumber = episodeNumber,
                episodeUrl = episodeUrl,
                coverUrl = coverUrl,
            )
            refreshState()
            processDownload(entry)
            return entry
        }
    }

    /**
     * Pause an active download, keeping the partial file so [resume] can
     * continue from where it stopped (HTTP Range) instead of restarting.
     */
    open fun pause(id: Long) {
        synchronized(downloadLock) {
            val entry = repository.get(id) ?: return
            if (!entry.isActive) return
            attemptTokens.remove(id)
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancel()
            val resumePath = preservePartialForResume(id, entry)
            repository.update(
                id = id,
                status = DownloadRepository.DownloadStatus.PAUSED,
                resumePartialPath = resumePath,
            )
            refreshState()
        }
    }

    /**
     * Rename the in-flight partial file to a stable ".resume.part" name so a
     * later [resume] can find it regardless of the attempt token.
     */
    private fun preservePartialForResume(id: Long, entry: DownloadRepository.DownloadEntry): String? {
        val current = temporaryFiles.remove(id) ?: return null
        if (!isManagedDownloadPath(current) || !current.isFile) return null
        val finalName = entry.filePath?.substringAfterLast('/') ?: return null
        val resumeFile = File(current.parentFile, ".$finalName.resume.part")
        return try {
            Files.deleteIfExists(resumeFile.toPath())
            Files.move(current.toPath(), resumeFile.toPath())
            resumeFile.absolutePath
        } catch (e: Exception) {
            logger.warn(e) { "Failed to preserve partial for resume (id=$id)" }
            null
        }
    }

    /** Resume a paused download, continuing from the preserved partial file. */
    open fun resume(id: Long) {
        synchronized(downloadLock) {
            val entry = repository.get(id) ?: return
            if (entry.status != DownloadRepository.DownloadStatus.PAUSED) return
            attemptTokens.remove(id)
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancel()
            repository.update(id, status = DownloadRepository.DownloadStatus.QUEUED)
            refreshState()
            processDownload(repository.get(id) ?: return)
        }
    }

    /** Cancel and remove a download, including final and partial files. */
    open fun cancel(id: Long) {
        synchronized(downloadLock) {
            val entry = repository.get(id) ?: return
            attemptTokens.remove(id)
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancel()
            cleanupDownloadFiles(entry)
            repository.remove(id)
            refreshState()
        }
    }

    /** Cancel all queued/active downloads without manually altering semaphore permits. */
    open fun cancelAll() {
        synchronized(downloadLock) {
            val entries = repository.getAll().filter { it.isActive }
            entries.forEach { entry ->
                attemptTokens.remove(entry.id)
                activeCalls.remove(entry.id)?.cancel()
                activeJobs.remove(entry.id)?.cancel()
                cleanupDownloadFiles(entry)
                repository.remove(entry.id)
            }
            refreshState()
        }
    }

    /** Pause every active download (partials are preserved for resume). */
    fun pauseAll() {
        repository.getActive().map { it.id }.forEach(::pause)
    }

    /** Resume every paused download. */
    fun resumeAll() {
        repository.getAll()
            .filter { it.status == DownloadRepository.DownloadStatus.PAUSED }
            .map { it.id }
            .forEach(::resume)
    }

    /** Retry an errored download using a fresh temporary file. */
    open fun retry(id: Long) {
        synchronized(downloadLock) {
            val entry = repository.get(id) ?: return
            if (entry.status != DownloadRepository.DownloadStatus.ERROR) return
            attemptTokens.remove(id)
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancel()
            cleanupDownloadFiles(entry)
            repository.update(id, status = DownloadRepository.DownloadStatus.QUEUED, progress = 0f)
            refreshState()
            processDownload(repository.get(id) ?: return)
        }
    }

    fun getLocalFile(animeId: Long, episodeNumber: Double): File? {
        val entry = repository.getAll().find {
            it.animeId == animeId && it.episodeNumber == episodeNumber &&
                it.status == DownloadRepository.DownloadStatus.COMPLETED
        }
        return entry?.filePath?.let(::safeManagedDownloadFile)
    }

    /** The managed downloads directory (may not exist yet). */
    fun downloadsDirectory(): File = File(storageProvider.downloadsDirectory, "videos")

    /**
     * Change where NEW downloads are stored (Settings > Download location).
     * Existing files stay where they are.
     */
    fun setDownloadsDirectory(path: String) {
        storageProvider.customDownloadsDirectory = path.trim().takeIf { it.isNotEmpty() }
    }

    fun isDownloading(animeId: Long, episodeNumber: Double): Boolean = repository.getAll().any {
        it.animeId == animeId && it.episodeNumber == episodeNumber && it.isActive
    }

    fun isDownloaded(animeId: Long, episodeNumber: Double): Boolean =
        repository.isDownloaded(animeId, episodeNumber)

    private fun refreshState() {
        _downloads.value = repository.getAll()
    }

    private fun resumePendingDownloads() {
        repository.getAll()
            .filter { it.status == DownloadRepository.DownloadStatus.QUEUED }
            .forEach(::processDownload)
    }

    private fun processDownload(entry: DownloadRepository.DownloadEntry) {
        synchronized(downloadLock) {
            if (closed || activeJobs.containsKey(entry.id)) return
            val token = UUID.randomUUID().toString()
            attemptTokens[entry.id] = token

            val job = scope.launch(downloadDispatcher(), start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    currentCoroutineContext().ensureActive()
                    executeDownload(entry, token)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    markFailed(entry, token, failure)
                }
            }
            job.invokeOnCompletion {
                activeJobs.remove(entry.id, job)
                attemptTokens.remove(entry.id, token)
            }
            activeJobs[entry.id] = job
            job.start()
        }
    }

    private fun downloadDispatcher() = Dispatchers.IO.limitedParallelism(maxConcurrentDownloads)

    private suspend fun executeDownload(entry: DownloadRepository.DownloadEntry, token: String) {
        updateIfCurrent(entry.id, token) {
            repository.update(entry.id, status = DownloadRepository.DownloadStatus.DOWNLOADING)
            refreshState()
        }

        val video = resolveVideo(entry)
        requireCurrentAttempt(entry.id, token)
        val videoUrl = video?.videoUrl ?: ""
        require(videoUrl.isNotBlank()) { "Video URL is empty" }
        // Persist the stream headers (Referer, Origin, ...) with the entry so the
        // actual request below authenticates the same way the player does. Many
        // sources refuse bare requests without these.
        val videoHeaders = video?.headers?.let { headers ->
            buildMap { for (i in 0 until headers.size) put(headers.name(i), headers.value(i)) }
        }?.takeIf { it.isNotEmpty() }
        if (!videoHeaders.isNullOrEmpty()) {
            updateIfCurrent(entry.id, token) {
                repository.update(entry.id, videoUrl = videoUrl, headers = videoHeaders)
            }
        }

        // Torrent episodes resolve to magnet links, which have no HTTP URL to
        // fetch directly. Route them through the torrent engine (TorrServer /
        // WebTorrent) — it produces a local HTTP stream we download from like
        // any other URL, then tear the torrent down when done.
        var torrentStream: TorrentStreamingResult.Success? = null
        val effectiveUrl = if (videoUrl.startsWith("magnet:", ignoreCase = true)) {
            when (val result = torrentCoordinator.start(videoUrl)) {
                is TorrentStreamingResult.Success -> {
                    torrentStream = result
                    result.httpUrl
                }
                is TorrentStreamingResult.Failure ->
                    throw IOException("Torrent download failed: ${result.message}")
            }
        } else {
            videoUrl
        }
        require(effectiveUrl.startsWith("http://", ignoreCase = true) ||
            effectiveUrl.startsWith("https://", ignoreCase = true)) { "Unsupported video URL scheme" }

        val downloadsDir = prepareDownloadsDirectory()

        // HLS/DASH manifests can't be saved raw — the result would be a text
        // playlist, not the episode. Download every segment and write a local
        // playlist instead.
        if (isManifestUrl(effectiveUrl)) {
            val manifestDir = File(
                downloadsDir,
                "${sanitizeFileName("${entry.animeTitle}_E${String.format("%.0f", entry.episodeNumber)}")}_${entry.id}",
            )
            if (manifestDir.exists() || !manifestDir.mkdirs()) {
                throw IOException("Could not create manifest download directory")
            }
            val manifestHeaders = repository.get(entry.id)?.headers
            val result = HlsDashDownloader(httpClient).download(
                manifestUrl = effectiveUrl,
                headers = manifestHeaders,
                targetDir = manifestDir,
                onProgress = { downloaded, total ->
                    updateIfCurrent(entry.id, token) {
                        val denom = total.coerceAtLeast(downloaded)
                        repository.update(
                            id = entry.id,
                            downloadedBytes = downloaded.toLong(),
                            totalBytes = denom.toLong(),
                            progress = if (denom > 0) downloaded.toFloat() / denom else 0f,
                        )
                    }
                    refreshState()
                },
            ) ?: throw IOException("Manifest download failed (no playable segments)")

            val playlistFile = result.playlistFile
            synchronized(downloadLock) {
                requireCurrentAttempt(entry.id, token)
                repository.update(
                    id = entry.id,
                    status = DownloadRepository.DownloadStatus.COMPLETED,
                    progress = 1f,
                    filePath = playlistFile.absolutePath,
                    fileName = playlistFile.name,
                    totalBytes = manifestDir.listFiles()?.sumOf { runCatching { it.length() }.getOrDefault(0L) }
                        ?: playlistFile.length(),
                    downloadedBytes = playlistFile.length(),
                )
                refreshState()
            }
            runCatching { notifyDownloadComplete(entry) }
            logger.info {
                "Download complete (manifest): ${entry.animeTitle} - ${entry.episodeName} (${manifestDir.absolutePath})"
            }
            return
        }

        val extension = extractExtension(effectiveUrl)
        val safeBase = sanitizeFileName("${entry.animeTitle}_E${String.format("%.0f", entry.episodeNumber)}")
        val finalFile = File(downloadsDir, "${safeBase}_${entry.id}$extension")
        val tempFile = File(downloadsDir, ".${finalFile.name}.$token.part")
        requireSafeDownloadFile(finalFile, downloadsDir)
        requireSafeDownloadFile(tempFile, downloadsDir)
        if (Files.isSymbolicLink(finalFile.toPath()) || Files.isSymbolicLink(tempFile.toPath())) {
            throw IOException("Refusing symlinked download path")
        }
        if (finalFile.exists()) {
            throw IOException("Download destination already exists: ${finalFile.name}")
        }

        // Resume support: a paused download preserved its partial file — reuse
        // it and continue from its byte offset via an HTTP Range request.
        var resumeOffset = 0L
        entry.resumePartialPath?.takeIf { it.isNotBlank() }?.let { stored ->
            val candidate = runCatching { File(stored) }.getOrNull()
            if (candidate != null && candidate.isFile &&
                !Files.isSymbolicLink(candidate.toPath()) && candidate.length() > 0
            ) {
                resumeOffset = candidate.length()
            } else {
                // Stale/invalid partial — discard it and start fresh.
                runCatching { candidate?.delete() }
            }
        }

        synchronized(downloadLock) {
            requireCurrentAttempt(entry.id, token)
            Files.deleteIfExists(tempFile.toPath())
            if (resumeOffset > 0) {
                // Move the preserved partial into this attempt's temp file.
                val resumeFile = File(entry.resumePartialPath!!)
                Files.move(resumeFile.toPath(), tempFile.toPath())
            } else {
                Files.createFile(tempFile.toPath())
            }
            temporaryFiles[entry.id] = tempFile
            repository.update(
                id = entry.id,
                videoUrl = videoUrl,
                filePath = finalFile.absolutePath,
                fileName = finalFile.name,
                resumePartialPath = DownloadRepository.CLEAR_RESUME_PARTIAL,
            )
            refreshState()
        }

        var completed = false
        try {
            val request = Request.Builder().url(effectiveUrl).apply {
                // Apply the stream headers captured from the source's Video. This
                // manager uses a bare OkHttpClient (no global UA interceptor), so
                // sources that require Referer/User-Agent would 403 without these.
                val storedHeaders = repository.get(entry.id)?.headers
                if (!storedHeaders.isNullOrEmpty()) {
                    runCatching { headers(storedHeaders.toHeaders()) }
                }
                // Continue a paused download from where its partial file ended.
                if (resumeOffset > 0) {
                    header("Range", "bytes=$resumeOffset-")
                }
                // Parity with the player: some CDNs reject requests with no
                // User-Agent at all, even when the source didn't send one.
                val headers = repository.get(entry.id)?.headers
                if (headers.isNullOrEmpty() || headers.none { it.key.equals("User-Agent", ignoreCase = true) }) {
                    header("User-Agent", MPVLib.DEFAULT_USER_AGENT)
                }
            }.get().build()
            val call = httpClient.newCall(request)
            synchronized(downloadLock) {
                if (attemptTokens[entry.id] != token) {
                    call.cancel()
                    throw CancellationException("Download attempt was replaced")
                }
                activeCalls[entry.id] = call
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful && response.code != 206 && response.code != 416) {
                        throw IOException("Download failed: HTTP ${response.code}")
                    }
                    if (response.code == 416) {
                        // The source's file shrank below our partial — restart fresh.
                        throw IOException("Download failed: HTTP 416 (source file changed)")
                    }
                    val body = response.body ?: throw IOException("Download response has no body")
                    // 206 = the server honoured our Range and continues at
                    // resumeOffset; 200 = it ignored Range, so start over.
                    val continuing = response.code == 206 && resumeOffset > 0
                    val offset = if (continuing) resumeOffset else 0L
                    val contentLength = body.contentLength()
                    val totalLength = if (continuing && contentLength > 0) offset + contentLength
                    else contentLength
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tempFile, continuing).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var totalRead = offset
                            var lastProgressRefresh = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                requireCurrentAttempt(entry.id, token)
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val now = System.currentTimeMillis()
                                val shouldRefresh = now - lastProgressRefresh >= 200L
                                updateIfCurrent(entry.id, token) {
                                    repository.update(
                                        id = entry.id,
                                        downloadedBytes = totalRead,
                                        totalBytes = if (totalLength > 0) totalLength else totalRead,
                                        progress = if (totalLength > 0) {
                                            (totalRead.toDouble() / totalLength).toFloat().coerceIn(0f, 1f)
                                        } else 0f,
                                    )
                                }
                                if (shouldRefresh) {
                                    lastProgressRefresh = now
                                    refreshState()
                                }
                            }
                            // Make sure the final state is published even if the
                            // last chunk arrived before the throttle window.
                            updateIfCurrent(entry.id, token) {
                                repository.update(
                                    id = entry.id,
                                    downloadedBytes = totalRead,
                                    totalBytes = if (totalLength > 0) totalLength else totalRead,
                                    progress = if (totalLength > 0) {
                                        (totalRead.toDouble() / totalLength).toFloat().coerceIn(0f, 1f)
                                    } else 0f,
                                )
                            }
                            refreshState()
                        }
                    }
                }
            } finally {
                activeCalls.remove(entry.id, call)
            }

            currentCoroutineContext().ensureActive()
            synchronized(downloadLock) {
                requireCurrentAttempt(entry.id, token)
                atomicComplete(tempFile, finalFile)
                repository.update(
                    id = entry.id,
                    status = DownloadRepository.DownloadStatus.COMPLETED,
                    progress = 1f,
                    totalBytes = finalFile.length(),
                    downloadedBytes = finalFile.length(),
                )
                refreshState()
                completed = true
            }

            runCatching { notifyDownloadComplete(entry) }
            runCatching { repository.pruneCompleted(20) }
            logger.info {
                "Download complete: ${entry.animeTitle} - ${entry.episodeName} (${formatSize(finalFile.length())})"
            }
        } finally {
            // Tear down any torrent stream backing this download (the saved
            // file stays on disk and plays offline like any other download).
            torrentStream?.let { torrentCoordinator.stop(it) }
            // Only remove the temp file while it is still this attempt's
            // in-flight file. pause() detaches the mapping before preserving
            // the partial for resume, so a late-arriving cancellation must
            // not delete the file the resume will continue from.
            if (temporaryFiles.get(entry.id) == tempFile) {
                Files.deleteIfExists(tempFile.toPath())
                temporaryFiles.remove(entry.id, tempFile)
            }
            if (!completed && attemptTokens[entry.id] == token &&
                repository.get(entry.id)?.status != DownloadRepository.DownloadStatus.COMPLETED
            ) {
                Files.deleteIfExists(finalFile.toPath())
            }
        }
    }

    /** Testable production hook; the default retains the existing notification behavior. */
    protected open fun notifyDownloadComplete(entry: DownloadRepository.DownloadEntry) {
        notifier.showDownloadComplete(entry.animeTitle, entry.episodeName)
    }

    /** Testable production hook; production resolves through the loaded extension source. */
    protected open suspend fun resolveVideoUrl(entry: DownloadRepository.DownloadEntry): String =
        resolveVideo(entry)?.videoUrl
            ?: throw IllegalStateException("No video URLs returned for episode")

    /**
     * Resolve the full [Video] for a download, preferring the source's preferred
     * quality (same policy the player uses). The returned video carries the
     * stream headers (Referer, Origin, ...) that many sources require.
     *
     * Prefers DIRECT media URLs over HLS/DASH manifests: saving a manifest raw
     * yields a text playlist, not the episode. Sources that only expose a
     * manifest are still handled — executeDownload routes them through
     * [HlsDashDownloader].
     */
    protected open suspend fun resolveVideo(entry: DownloadRepository.DownloadEntry): Video? {
        val source = extensionManager.getSource(entry.sourceId)
            ?: throw IllegalStateException("Source not found for ID ${entry.sourceId}")
        val episode = SEpisode.create().apply { url = entry.episodeUrl ?: "" }
        val videos = source.getVideoList(episode)
        return videos.firstOrNull { it.preferred && !isManifestUrl(it.videoUrl ?: "") }
            ?: videos.firstOrNull { !isManifestUrl(it.videoUrl ?: "") }
            ?: videos.firstOrNull { it.preferred }
            ?: videos.firstOrNull()
    }

    /**
     * Delete all completed downloads (files + queue entries).
     * Returns the number of entries removed. Files outside the managed
     * videos directory are never touched.
     */
    fun removeCompleted(): Int {
        synchronized(downloadLock) {
            val completed = repository.getCompleted()
            var removed = 0
            completed.forEach { entry ->
                entry.filePath?.let { path ->
                    safeManagedDownloadFile(path)?.let { file ->
                        runCatching { Files.deleteIfExists(file.toPath()) }
                            .onFailure { e -> logger.warn(e) { "Failed to delete completed download ${file.path}" } }
                    }
                }
                repository.remove(entry.id)
                removed++
            }
            if (removed > 0) refreshState()
            return removed
        }
    }

    private fun prepareDownloadsDirectory(): File {
        val downloadsDir = File(storageProvider.downloadsDirectory, "videos")
        val path = downloadsDir.toPath()
        Files.createDirectories(path)
        require(!Files.isSymbolicLink(path)) { "Downloads directory is a symlink" }
        require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "Downloads path is not a directory" }
        return downloadsDir.canonicalFile
    }

    private fun requireSafeDownloadFile(file: File, root: File) {
        require(file.canonicalFile.parentFile == root.canonicalFile) {
            "Download path escapes the managed videos directory"
        }
    }

    private fun atomicComplete(tempFile: File, finalFile: File) {
        try {
            Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), finalFile.toPath())
        }
        require(Files.isRegularFile(finalFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Completed download is not a regular file"
        }
    }

    private fun requireCurrentAttempt(id: Long, token: String) {
        if (attemptTokens[id] != token) {
            throw CancellationException("Download attempt was replaced")
        }
    }

    private inline fun updateIfCurrent(id: Long, token: String, update: () -> Unit) {
        synchronized(downloadLock) {
            requireCurrentAttempt(id, token)
            update()
        }
    }

    private fun cleanupTemporaryFile(id: Long, entry: DownloadRepository.DownloadEntry) {
        temporaryFiles.remove(id)?.let { temporary ->
            if (isManagedDownloadPath(temporary)) temporary.delete()
        }
        entry.filePath?.let { path ->
            safeManagedDownloadFile(path)?.let { finalFile ->
                finalFile.parentFile?.listFiles()
                    ?.filter { it.name.startsWith(".${finalFile.name}.") && it.name.endsWith(".part") }
                    ?.forEach { part ->
                        if (isManagedDownloadPath(part)) part.delete()
                    }
            }
        }
    }

    private fun cleanupDownloadFiles(entry: DownloadRepository.DownloadEntry) {
        cleanupTemporaryFile(entry.id, entry)
        entry.filePath?.let { path -> safeManagedDownloadFile(path)?.delete() }
    }

    private fun safeManagedDownloadFile(path: String): File? {
        val root = runCatching { File(storageProvider.downloadsDirectory, "videos").canonicalFile }
            .getOrNull() ?: return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf {
            it.parentFile == root &&
                !Files.isSymbolicLink(it.toPath()) &&
                Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
    }

    private fun isManagedDownloadPath(file: File): Boolean =
        runCatching {
            val root = File(storageProvider.downloadsDirectory, "videos").canonicalFile
            file.canonicalFile.parentFile == root && !Files.isSymbolicLink(file.toPath())
        }.getOrDefault(false)

    private fun markFailed(
        entry: DownloadRepository.DownloadEntry,
        token: String,
        failure: Exception,
    ) {
        synchronized(downloadLock) {
            if (attemptTokens[entry.id] != token) return
            val current = repository.get(entry.id)
            if (current == null || current.status == DownloadRepository.DownloadStatus.PAUSED) {
                // Cancellation may be reported by OkHttp as an ordinary I/O failure.
                // Preserve the explicit pause/remove decision made by the caller.
                return
            }
            cleanupDownloadFiles(entry)
            repository.update(entry.id, status = DownloadRepository.DownloadStatus.ERROR)
            refreshState()
        }
        logger.error(failure) { "Download failed for ${entry.id}: ${entry.animeTitle} - ${entry.episodeName}" }
    }

    private fun extractExtension(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/').substringAfterLast('\\')
        val suffix = lastSegment.substringAfterLast('.', "")
        return if (suffix.matches(Regex("[A-Za-z0-9]{1,8}"))) ".${suffix.lowercase()}" else ".mp4"
    }

    internal fun sanitizeFileName(name: String): String {
        val sanitized = name
            .replace(Regex("[\\u0000-\\u001f/\\\\:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(MAX_FILENAME_LENGTH)
        return sanitized.ifBlank { "download" }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    /**
     * Cancel active calls/jobs and synchronously clean known local artifacts.
     *
     * This method intentionally does not join worker coroutines: AutoCloseable is
     * synchronous and may be called from arbitrary application threads, while a
     * worker may resume on a different thread. OkHttp cancellation closes the
     * network call; each worker's token/finally path prevents stale completion.
     */
    override fun close() {
        val activeEntries: List<DownloadRepository.DownloadEntry>
        synchronized(downloadLock) {
            if (closed) return
            closed = true
            activeEntries = repository.getAll().filter { it.isActive }
            activeEntries.forEach { attemptTokens.remove(it.id) }
            activeCalls.values.forEach { it.cancel() }
            activeJobs.values.forEach { it.cancel() }
            temporaryFiles.values.forEach { it.delete() }
            activeEntries.forEach { entry ->
                val current = repository.get(entry.id)
                if (current?.isActive == true) {
                    cleanupDownloadFiles(current)
                    repository.update(current.id, status = DownloadRepository.DownloadStatus.ERROR)
                }
            }
            refreshState()
        }
        scope.cancel()
        httpClient.dispatcher.cancelAll()
    }

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_FILENAME_LENGTH = 160
    }
}

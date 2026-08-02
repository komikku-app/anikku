package app.anikku.macos.platform.download

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import eu.kanade.tachiyomi.animesource.model.SEpisode
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
    @Volatile
    private var closed = false
    private val _downloads = MutableStateFlow(repository.getAll())
    open val downloads: StateFlow<List<DownloadRepository.DownloadEntry>> = _downloads.asStateFlow()

    var maxConcurrentDownloads: Int = MAX_CONCURRENT_DOWNLOADS
        set(value) {
            field = value.coerceIn(1, 10)
            // The fixed safety limit is intentionally retained for this manager;
            // changing it cannot safely resize permits held by active jobs.
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
            )
            refreshState()
            processDownload(entry)
            return entry
        }
    }

    /** Pause an active download and discard its non-resumable partial file. */
    open fun pause(id: Long) {
        synchronized(downloadLock) {
            val entry = repository.get(id) ?: return
            if (!entry.isActive) return
            attemptTokens.remove(id)
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancel()
            cleanupTemporaryFile(id, entry)
            repository.update(id, status = DownloadRepository.DownloadStatus.PAUSED)
            refreshState()
        }
    }

    /** Resume a paused download from a fresh temporary file. */
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

        val videoUrl = resolveVideoUrl(entry)
        requireCurrentAttempt(entry.id, token)
        require(videoUrl.isNotBlank()) { "Video URL is empty" }

        val downloadsDir = prepareDownloadsDirectory()
        val extension = extractExtension(videoUrl)
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
        synchronized(downloadLock) {
            requireCurrentAttempt(entry.id, token)
            Files.deleteIfExists(tempFile.toPath())
            Files.createFile(tempFile.toPath())
            temporaryFiles[entry.id] = tempFile
            repository.update(
                id = entry.id,
                videoUrl = videoUrl,
                filePath = finalFile.absolutePath,
                fileName = finalFile.name,
            )
            refreshState()
        }

        var completed = false
        try {
            val request = Request.Builder().url(videoUrl).get().build()
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
                    if (!response.isSuccessful) {
                        throw IOException("Download failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Download response has no body")
                    val contentLength = body.contentLength()
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var totalRead = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                requireCurrentAttempt(entry.id, token)
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                updateIfCurrent(entry.id, token) {
                                    repository.update(
                                        id = entry.id,
                                        downloadedBytes = totalRead,
                                        totalBytes = if (contentLength > 0) contentLength else totalRead,
                                        progress = if (contentLength > 0) {
                                            (totalRead.toDouble() / contentLength).toFloat().coerceIn(0f, 1f)
                                        } else 0f,
                                    )
                                }
                            }
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
            Files.deleteIfExists(tempFile.toPath())
            temporaryFiles.remove(entry.id, tempFile)
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
    protected open suspend fun resolveVideoUrl(entry: DownloadRepository.DownloadEntry): String {
        val source = extensionManager.getSource(entry.sourceId)
            ?: throw IllegalStateException("Source not found for ID ${entry.sourceId}")
        val episode = SEpisode.create().apply { url = entry.episodeUrl ?: "" }
        return source.getVideoList(episode).firstOrNull()?.videoUrl
            ?: throw IllegalStateException("No video URLs returned for episode")
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
            temporaryFiles.remove(entry.id)?.let { temporary ->
                if (isManagedDownloadPath(temporary)) temporary.delete()
            }
            repository.get(entry.id)?.filePath?.let { path -> safeManagedDownloadFile(path)?.delete() }
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

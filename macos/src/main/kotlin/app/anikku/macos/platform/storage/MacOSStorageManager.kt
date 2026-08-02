package app.anikku.macos.platform.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import java.io.File

/**
 * macOS-specific StorageManager that replaces the Android version's
 * UniFile/Context dependencies with java.io.File via MacOSStorageProvider.
 *
 * The Android StorageManager uses UniFile.fromUri(context, ...) for file operations.
 * On macOS, we use java.io.File directly via MacOSStorageProvider.
 */
class MacOSStorageManager(
    private val storageProvider: MacOSStorageProvider,
    storagePreferences: MacOSStoragePreferences? = null,
) : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var closed = false

    private var baseDir: File = storageProvider.directory()

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        // Set up storage preferences watcher if provided
        storagePreferences?.baseStorageDirectory()?.changes()
            ?.drop(1)
            ?.distinctUntilChanged()
            ?.onEach { path ->
                if (!closed) {
                    baseDir = File(path).canonicalFile
                    require(baseDir.exists() || baseDir.mkdirs()) {
                        "Unable to create storage directory: ${baseDir.path}"
                    }
                    createDirectories(baseDir)
                    _changes.send(Unit)
                }
            }
            ?.launchIn(scope)

        // Create initial directories
        createDirectories(baseDir)
    }

    private fun createDirectories(parent: File) {
        getAutomaticBackupsDirectory(parent)?.mkdirs()
        getDownloadsDirectory(parent)?.mkdirs()
        getLocalSourceDirectory(parent)?.mkdirs()
        getMPVConfigDirectory(parent)?.let { mpvDir ->
            mpvDir.mkdirs()
            File(mpvDir, FONTS_PATH).mkdirs()
            File(mpvDir, SCRIPTS_PATH).mkdirs()
            File(mpvDir, SCRIPT_OPTS_PATH).mkdirs()
        }
        getLogsDirectory(parent)?.mkdirs()
    }

    fun getAutomaticBackupsDirectory(): File? = getAutomaticBackupsDirectory(baseDir)
    fun getDownloadsDirectory(): File? = getDownloadsDirectory(baseDir)
    fun getLocalSourceDirectory(): File? = getLocalSourceDirectory(baseDir)
    fun getFontsDirectory(): File? = getMPVConfigDirectory(baseDir)?.let { File(it, FONTS_PATH) }
    fun getScriptsDirectory(): File? = getMPVConfigDirectory(baseDir)?.let { File(it, SCRIPTS_PATH) }
    fun getScriptOptsDirectory(): File? = getMPVConfigDirectory(baseDir)?.let { File(it, SCRIPT_OPTS_PATH) }
    fun getMPVConfigDirectory(): File? = getMPVConfigDirectory(baseDir)
    fun getLogsDirectory(): File? = getLogsDirectory(baseDir)

    private fun getAutomaticBackupsDirectory(parent: File): File? = ensureDir(parent, AUTOMATIC_BACKUPS_PATH)
    private fun getDownloadsDirectory(parent: File): File? = ensureDir(parent, DOWNLOADS_PATH)
    private fun getLocalSourceDirectory(parent: File): File? = ensureDir(parent, LOCAL_SOURCE_PATH)
    private fun getMPVConfigDirectory(parent: File): File? = ensureDir(parent, MPV_CONFIG_PATH)
    private fun getLogsDirectory(parent: File): File? = ensureDir(parent, LOGS_PATH)

    private fun ensureDir(parent: File, name: String): File? {
        val dir = File(parent, name)
        return if (dir.exists() || dir.mkdirs()) {
            require(dir.isDirectory) { "Storage path is not a directory: ${dir.path}" }
            dir
        } else {
            throw IllegalStateException("Unable to create storage directory: ${dir.path}")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        _changes.close()
    }

    companion object {
        private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
        private const val DOWNLOADS_PATH = "downloads"
        private const val LOCAL_SOURCE_PATH = "local"
        private const val MPV_CONFIG_PATH = "mpv-config"
        private const val FONTS_PATH = "fonts"
        const val SCRIPTS_PATH = "scripts"
        const val SCRIPT_OPTS_PATH = "script-opts"
        private const val LOGS_PATH = "logs"
    }
}

/**
 * Minimal storage preferences interface for macOS.
 * Mirrors the Android StoragePreferences.baseStorageDirectory() API.
 */
interface MacOSStoragePreferences {
    fun baseStorageDirectory(): tachiyomi.core.common.preference.Preference<String>
}

package app.anikku.macos.platform.storage

import tachiyomi.core.common.storage.FolderProvider
import java.io.File

/**
 * macOS storage provider implementing FolderProvider.
 *
 * Base directory: ~/Library/Application Support/Anikku/
 * Subdirectories: downloads/, backups/, extensions/, logs/, covers/, data/
 */
open class MacOSStorageProvider : FolderProvider {

    override open fun directory(): File = baseDirectory

    override fun path(): String = directory().toURI().toString()

    /**
     * User-chosen downloads folder (Settings > Downloads > Download location).
     * Null = the default Application Support/Anikku/downloads directory. Only
     * NEW downloads go here; existing files are never moved.
     */
    @Volatile
    var customDownloadsDirectory: String? = null

    val downloadsDirectory: File
        get() = customDownloadsDirectory?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(directory(), "downloads")
    val backupsDirectory: File get() = File(directory(), "backups")
    val extensionsDirectory: File get() = File(directory(), "extensions")
    val logsDirectory: File get() = File(directory(), "logs")
    val coversDirectory: File get() = File(directory(), "covers")
    val dataDirectory: File get() = File(directory(), "data")
    val cacheDirectory: File get() = File(directory(), "cache")
    val imageCacheDirectory: File get() = File(cacheDirectory, "images")

    fun ensureDirectories() {
        listOf(
            downloadsDirectory,
            backupsDirectory,
            extensionsDirectory,
            logsDirectory,
            coversDirectory,
            dataDirectory,
            cacheDirectory,
        ).forEach { directory ->
            require(directory.exists() || directory.mkdirs()) {
                "Unable to create storage directory: ${directory.path}"
            }
            require(directory.isDirectory) {
                "Storage path is not a directory: ${directory.path}"
            }
        }
    }

    companion object {
        val baseDirectory: File by lazy {
            baseDirectoryFor(System.getProperty("user.home"))
        }

        /** The app data root for a given home directory (single source of truth). */
        internal fun baseDirectoryFor(userHome: String): File =
            File(
                userHome,
                "Library${File.separator}Application Support${File.separator}Anikku",
            )
    }
}

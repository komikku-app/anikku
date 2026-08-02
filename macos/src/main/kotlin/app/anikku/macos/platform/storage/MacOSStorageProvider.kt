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

    val downloadsDirectory: File get() = File(directory(), "downloads")
    val backupsDirectory: File get() = File(directory(), "backups")
    val extensionsDirectory: File get() = File(directory(), "extensions")
    val logsDirectory: File get() = File(directory(), "logs")
    val coversDirectory: File get() = File(directory(), "covers")
    val dataDirectory: File get() = File(directory(), "data")
    val cacheDirectory: File get() = File(directory(), "cache")

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
            File(
                System.getProperty("user.home"),
                "Library${File.separator}Application Support${File.separator}Anikku",
            )
        }
    }
}

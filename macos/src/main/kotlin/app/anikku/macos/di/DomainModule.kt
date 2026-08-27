package app.anikku.macos.di

import app.anikku.macos.AnikkuApplication
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.storage.MacOSStorageManager
import org.koin.dsl.module
import tachiyomi.domain.anime.repository.CustomAnimeRepository

/**
 * Domain & Data layer Koin module.
 *
 * Registers business logic and data repository implementations:
 * - Storage manager: file system operations (backups, downloads, mpv config)
 * - Custom anime repository: user-defined anime metadata edits
 * - Download repository + manager: offline episode downloads
 */
fun domainModule(app: AnikkuApplication) = module {

    // Phase 2: Storage & Data
    single<MacOSStorageManager> { app.storageManager }
    single<MacOSCustomAnimeRepository> { app.customAnimeRepository }
    single<CustomAnimeRepository> { app.customAnimeRepository }

    // Phase 7: Download pipeline
    single { DownloadRepository(app.storageProvider.dataDirectory) }
    single {
        MacOSDownloadManager(
            repository = get(),
            extensionManager = app.extensionManager,
            storageProvider = app.storageProvider,
            notifier = app.notificationManager,
        )
    }
}

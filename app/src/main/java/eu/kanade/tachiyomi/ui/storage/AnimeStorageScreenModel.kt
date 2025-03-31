package eu.kanade.tachiyomi.ui.storage

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeStorageScreenModel(
    downloadCache: DownloadCache = Injekt.get(),
    private val getLibraries: GetLibraryManga = Injekt.get(),
    getCategories: GetCategories = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : CommonStorageScreenModel<LibraryManga>(
    downloadCacheChanges = downloadCache.changes,
    downloadCacheIsInitializing = downloadCache.isInitializing,
    libraries = getLibraries.subscribe(),
    categories = { showHiddenCategories ->
        getCategories.subscribe()
            .map { categories ->
                categories.filterNot { !showHiddenCategories && it.hidden }
            }
    },
    getDownloadSize = { downloadManager.getDownloadSize(manga) },
    getDownloadCount = { downloadManager.getDownloadCount(manga) },
    getId = { id },
    getCategoryId = { category },
    getTitle = { manga.title },
    getThumbnail = { manga.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        screenModelScope.launchNonCancellable {
            val anime = getLibraries.await().find {
                it.id == id
            }?.manga ?: return@launchNonCancellable
            val source = sourceManager.get(anime.source) ?: return@launchNonCancellable
            downloadManager.deleteAnime(anime, source)
        }
    }
}

package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.manga.model.MangaLibraryGroup
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class SetSortModeForMangaCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: MangaCategoryRepository,
) {

    suspend fun await(
        categoryId: Long?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction
    ) {
        // SY -->
        if (preferences.groupMangaLibraryBy().get() != MangaLibraryGroup.BY_DEFAULT) {
            preferences.mangaSortingMode().set(MangaLibrarySort(type, direction))
            return
        }
        // SY <--
        val category = categoryId?.let { categoryRepository.getMangaCategory(it) }
        val flags = (category?.flags ?: 0) + type + direction
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartialMangaCategory(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.mangaSortingMode().set(MangaLibrarySort(type, direction))
            categoryRepository.updateAllMangaCategoryFlags(flags)
        }
    }

    suspend fun await(
        category: Category?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction
    ) {
        await(category?.id, type, direction)
    }
}

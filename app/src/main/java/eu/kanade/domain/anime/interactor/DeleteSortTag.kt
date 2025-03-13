package eu.kanade.domain.anime.interactor

import tachiyomi.domain.library.service.LibraryPreferences

class DeleteSortTag(
    private val preferences: LibraryPreferences,
    private val getSortTag: GetSortTag,
) {

    fun await(tag: String) {
        preferences.sortTagsForLibrary().set(
            (getSortTag.await() - tag).mapIndexed { index, s ->
                CreateSortTag.encodeTag(index, s)
            }.toSet(),
        )
    }
}

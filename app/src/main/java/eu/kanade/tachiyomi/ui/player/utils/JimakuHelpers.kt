package eu.kanade.tachiyomi.ui.player.utils

sealed interface JimakuUiError {
    data object AuthError : JimakuUiError
    data object RateLimited : JimakuUiError
    data object NetworkError : JimakuUiError
    data object NotFound : JimakuUiError
    data class Unknown(val message: String) : JimakuUiError
}

internal fun rankFileFormat(filename: String): Int {
    val lower = filename.lowercase()
    return when {
        lower.endsWith(".ass") -> 3
        lower.endsWith(".ssa") -> 2
        lower.endsWith(".srt") -> 1
        else -> 0
    }
}

internal fun entrySearchCacheKey(query: String): String = "query:${query.lowercase().trim()}"
internal fun entryIdCacheKey(anilistId: Long): String = "anilist:$anilistId"
internal fun fileListCacheKey(entryId: Long, episode: Int?): String = "$entryId:${episode ?: "all"}"

data class JimakuState(
    val files: List<JimakuFile> = emptyList(),
    val loading: Boolean = false,
    val error: JimakuUiError? = null,
    val addedUrls: Set<String> = emptySet(),
    val isAllFiles: Boolean = false,
    val enabled: Boolean = false,
)

data class JimakuCallbacks(
    val onFetch: (forceRefresh: Boolean) -> Unit,
    val onSearch: (String) -> Unit,
    val onAddSubtitle: (JimakuFile) -> Unit,
)

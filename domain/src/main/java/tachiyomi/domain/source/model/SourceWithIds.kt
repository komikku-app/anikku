package tachiyomi.domain.source.model

data class SourceWithIds(
    val source: Source,
    val ids: List<Long>,
    val orphaned: List<Long>,
) {
    val count: Long
        get() = ids.size.toLong()

    val id: Long
        get() = source.id

    val name: String
        get() = source.name
}


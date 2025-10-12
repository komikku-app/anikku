package tachiyomi.domain.source.model

data class SourceWithIds(
    val source: Source,
    // AY -->
    val ids: List<Long>,
    val orphaned: List<Long>,
    // <-- AY
) {
    // AY -->
    val count: Long
        get() = ids.size.toLong()
    // <-- AY

    val id: Long
        get() = source.id

    val name: String
        get() = source.name
}

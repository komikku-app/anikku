package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import exh.metadata.metadata.RaisedSearchMetadata

typealias MangasPage = AnimesPage

// SY -->
class MetadataMangasPage(
    override val animes: List<SAnime>,
    override val hasNextPage: Boolean,
    val mangasMetadata: List<RaisedSearchMetadata>,
    val nextKey: Long? = null,
) : AnimesPage(animes, hasNextPage) {
    fun copy(
        mangas: List<SManga> = this.animes,
        hasNextPage: Boolean = this.hasNextPage,
        mangasMetadata: List<RaisedSearchMetadata> = this.mangasMetadata,
        nextKey: Long? = this.nextKey,
    ): MangasPage {
        return MetadataMangasPage(mangas, hasNextPage, mangasMetadata, nextKey)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MetadataMangasPage

        if (mangas != other.mangas) return false
        if (hasNextPage != other.hasNextPage) return false
        if (mangasMetadata != other.mangasMetadata) return false
        if (nextKey != other.nextKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + mangas.hashCode()
        result = 31 * result + hasNextPage.hashCode()
        result = 31 * result + mangasMetadata.hashCode()
        result = 31 * result + nextKey.hashCode()
        return result
    }

    override fun toString(): String {
        return "MetadataMangasPage(" +
            "mangas=$mangas, " +
            "hasNextPage=$hasNextPage, " +
            "mangasMetadata=$mangasMetadata, " +
            "nextKey=$nextKey" +
            ")"
    }
}
// SY <--

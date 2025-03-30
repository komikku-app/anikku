package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override suspend fun getAnimeDetails(anime: SManga): SManga =
        throw SourceNotInstalledException()

    override suspend fun getEpisodeList(anime: SManga): List<SChapter> =
        throw SourceNotInstalledException()

    override suspend fun getVideoList(episode: SChapter): List<Video> =
        throw SourceNotInstalledException()

    // KMK -->
    override suspend fun getRelatedAnimeList(
        anime: SManga,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) = throw SourceNotInstalledException()
    // KMK <--

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: Source): StubSource {
            return StubSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()

package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.copyFromSEpisode
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSAnime
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.data.episode.EpisodeSanitizer
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import java.lang.Long.max
import java.time.ZonedDateTime
import java.util.TreeSet

class SyncChaptersWithSource(
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val chapterRepository: ChapterRepository,
    private val shouldUpdateDbChapter: ShouldUpdateDbChapter,
    private val updateManga: UpdateManga,
    private val updateChapter: UpdateChapter,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    /**
     * Method to synchronize db episodes with source ones
     *
     * @param rawSourceEpisodes the episodes from the source.
     * @param manga the anime the episodes belong to.
     * @param source the source the anime belongs to.
     * @return Newly added episodes
     */
    suspend fun await(
        rawSourceEpisodes: List<SChapter>,
        manga: Manga,
        source: Source,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Chapter> {
        if (rawSourceEpisodes.isEmpty() && !source.isLocal()) {
            throw NoResultsException()
        }

        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()

        val sourceChapters = rawSourceEpisodes
            .distinctBy { it.url }
            .mapIndexed { i, sEpisode ->
                Chapter.create()
                    .copyFromSEpisode(sEpisode)
                    .copy(name = with(EpisodeSanitizer) { sEpisode.name.sanitize(manga.title) })
                    .copy(animeId = manga.id, sourceOrder = i.toLong())
            }

        val dbEpisodes = getChaptersByMangaId.await(manga.id)

        val newChapters = mutableListOf<Chapter>()
        val updatedChapters = mutableListOf<Chapter>()
        val removedEpisodes = dbEpisodes.filterNot { dbEpisode ->
            sourceChapters.any { sourceEpisode ->
                dbEpisode.url == sourceEpisode.url
            }
        }

        // Used to not set upload date of older episodes
        // to a higher value than newer episodes
        var maxSeenUploadDate = 0L

        for (sourceEpisode in sourceChapters) {
            var episode = sourceEpisode

            // Update metadata from source if necessary.
            if (source is HttpSource) {
                val sEpisode = episode.toSChapter()
                source.prepareNewEpisode(sEpisode, manga.toSAnime())
                episode = episode.copyFromSEpisode(sEpisode)
            }

            // Recognize episode number for the episode.
            val episodeNumber = ChapterRecognition.parseEpisodeNumber(
                manga.title,
                episode.name,
                episode.episodeNumber,
            )
            episode = episode.copy(episodeNumber = episodeNumber)

            val dbEpisode = dbEpisodes.find { it.url == episode.url }

            if (dbEpisode == null) {
                val toAddEpisode = if (episode.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) nowMillis else maxSeenUploadDate
                    episode.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceEpisode.dateUpload)
                    episode
                }
                newChapters.add(toAddEpisode)
            } else {
                if (shouldUpdateDbChapter.await(dbEpisode, episode)) {
                    val shouldRenameEpisode = downloadProvider.isEpisodeDirNameChanged(
                        dbEpisode,
                        episode,
                    ) &&
                        downloadManager.isEpisodeDownloaded(
                            dbEpisode.name,
                            dbEpisode.scanlator,
                            // SY -->
                            manga.ogTitle,
                            // SY <--
                            manga.source,
                        )

                    if (shouldRenameEpisode) {
                        downloadManager.renameEpisode(source, manga, dbEpisode, episode)
                    }
                    var toChangeEpisode = dbEpisode.copy(
                        name = episode.name,
                        episodeNumber = episode.episodeNumber,
                        scanlator = episode.scanlator,
                        sourceOrder = episode.sourceOrder,
                    )
                    if (episode.dateUpload != 0L) {
                        toChangeEpisode = toChangeEpisode.copy(dateUpload = episode.dateUpload)
                    }
                    updatedChapters.add(toChangeEpisode)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newChapters.isEmpty() && removedEpisodes.isEmpty() && updatedChapters.isEmpty()) {
            if (manualFetch || manga.fetchInterval == 0 || manga.nextUpdate < fetchWindow.first) {
                updateManga.awaitUpdateFetchInterval(
                    manga,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val reAdded = mutableListOf<Chapter>()

        val deletedEpisodeNumbers = TreeSet<Double>()
        val deletedSeenEpisodeNumbers = TreeSet<Double>()
        val deletedBookmarkedEpisodeNumbers = TreeSet<Double>()

        removedEpisodes.forEach { episode ->
            if (episode.seen) deletedSeenEpisodeNumbers.add(episode.episodeNumber)
            if (episode.bookmark) deletedBookmarkedEpisodeNumbers.add(episode.episodeNumber)
            deletedEpisodeNumbers.add(episode.episodeNumber)
        }

        val deletedEpisodeNumberDateFetchMap = removedEpisodes.sortedByDescending { it.dateFetch }
            .associate { it.episodeNumber to it.dateFetch }

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the episodes from most to less recent, which is common.
        var itemCount = newChapters.size
        var updatedToAdd = newChapters.map { toAddItem ->
            var episode = toAddItem.copy(dateFetch = nowMillis + itemCount--)

            if (!episode.isRecognizedNumber || episode.episodeNumber !in deletedEpisodeNumbers) return@map episode

            episode = episode.copy(
                seen = episode.episodeNumber in deletedSeenEpisodeNumbers,
                bookmark = episode.episodeNumber in deletedBookmarkedEpisodeNumbers,
            )

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedEpisodeNumberDateFetchMap[episode.episodeNumber]?.let {
                episode = episode.copy(dateFetch = it)
            }

            reAdded.add(episode)

            episode
        }

        if (removedEpisodes.isNotEmpty()) {
            val toDeleteIds = removedEpisodes.map { it.id }
            chapterRepository.removeEpisodesWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = chapterRepository.addAll(updatedToAdd)
        }

        if (updatedChapters.isNotEmpty()) {
            val episodeUpdates = updatedChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(episodeUpdates)
        }
        updateManga.awaitUpdateFetchInterval(manga, now, fetchWindow)

        // Set this anime as updated since episodes were changed
        // Note that last_update actually represents last time the episode list changed at all
        updateManga.awaitUpdateLastUpdate(manga.id)

        val reAddedUrls = reAdded.map { it.url }.toHashSet()

        return updatedToAdd.filterNot { it.url in reAddedUrls }
    }
}

package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Class used to keep a list of episodes for future deletion.
 *
 * @param context the application context.
 */
class DownloadPendingDeleter(
    context: Context,
    private val json: Json = Injekt.get(),
) {

    /**
     * Preferences used to store the list of episodes to delete.
     */
    private val preferences = context.getSharedPreferences(
        "episodes_to_delete",
        Context.MODE_PRIVATE,
    )

    /**
     * Last added episode, used to avoid decoding from the preference too often.
     */
    private var lastAddedEntry: Entry? = null

    /**
     * Adds a list of episodes for future deletion.
     *
     * @param chapters the episodes to be deleted.
     * @param manga the manga of the episodes.
     */
    @Synchronized
    fun addEpisodes(chapters: List<Chapter>, manga: Manga) {
        val lastEntry = lastAddedEntry

        val newEntry = if (lastEntry != null && lastEntry.anime.id == manga.id) {
            // Append new episodes
            val newEpisodes = lastEntry.episodes.addUniqueById(chapters)

            // If no episodes were added, do nothing
            if (newEpisodes.size == lastEntry.episodes.size) return

            // Last entry matches the manga, reuse it to avoid decoding json from preferences
            lastEntry.copy(episodes = newEpisodes)
        } else {
            val existingEntry = preferences.getString(manga.id.toString(), null)
            if (existingEntry != null) {
                // Existing entry found on preferences, decode json and add the new episode
                val savedEntry = json.decodeFromString<Entry>(existingEntry)

                // Append new episodes
                val newEpisodes = savedEntry.episodes.addUniqueById(chapters)

                // If no episodes were added, do nothing
                if (newEpisodes.size == savedEntry.episodes.size) return

                savedEntry.copy(episodes = newEpisodes)
            } else {
                // No entry has been found yet, create a new one
                Entry(chapters.map { it.toEntry() }, manga.toEntry())
            }
        }

        // Save current state
        val json = json.encodeToString(newEntry)
        preferences.edit {
            putString(newEntry.anime.id.toString(), json)
        }
        lastAddedEntry = newEntry
    }

    /**
     * Returns the list of episodes to be deleted grouped by its manga.
     *
     * Note: the returned list of manga and episodes only contain basic information needed by the
     * downloader, so don't use them for anything else.
     */
    @Synchronized
    fun getPendingEpisodes(): Map<Manga, List<Chapter>> {
        val entries = decodeAll()
        preferences.edit {
            clear()
        }
        lastAddedEntry = null

        return entries.associate { (episodes, anime) ->
            anime.toModel() to episodes.map { it.toModel() }
        }
    }

    /**
     * Decodes all the episodes from preferences.
     */
    private fun decodeAll(): List<Entry> {
        return preferences.all.values.mapNotNull { rawEntry ->
            try {
                (rawEntry as? String)?.let { json.decodeFromString<Entry>(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Returns a copy of episode entries ensuring no duplicates by episode id.
     */
    private fun List<EpisodeEntry>.addUniqueById(chapters: List<Chapter>): List<EpisodeEntry> {
        val newList = toMutableList()
        for (episode in chapters) {
            if (none { it.id == episode.id }) {
                newList.add(episode.toEntry())
            }
        }
        return newList
    }

    /**
     * Returns a manga entry from a manga model.
     */
    private fun Manga.toEntry() = AnimeEntry(id, url, title, source)

    /**
     * Returns a episode entry from a episode model.
     */
    private fun Chapter.toEntry() = EpisodeEntry(id, url, name, scanlator)

    /**
     * Returns a manga model from a manga entry.
     */
    private fun AnimeEntry.toModel() = Manga.create().copy(
        url = url,
        // SY -->
        ogTitle = title,
        // SY <--
        source = source,
        id = id,
    )

    /**
     * Returns a episode model from a episode entry.
     */
    private fun EpisodeEntry.toModel() = Chapter.create().copy(
        id = id,
        url = url,
        name = name,
        scanlator = scanlator,
    )

    /**
     * Class used to save an entry of episodes with their manga into preferences.
     */
    @Serializable
    private data class Entry(
        val episodes: List<EpisodeEntry>,
        val anime: AnimeEntry,
    )

    /**
     * Class used to save an entry for an episode into preferences.
     */
    @Serializable
    private data class EpisodeEntry(
        val id: Long,
        val url: String,
        val name: String,
        val scanlator: String? = null,
    )

    /**
     * Class used to save an entry for an manga into preferences.
     */
    @Serializable
    private data class AnimeEntry(
        val id: Long,
        val url: String,
        val title: String,
        val source: Long,
    )
}

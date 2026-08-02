package app.anikku.macos.platform.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.anikku.macos.platform.storage.MacOSAtomicFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val libraryLogger = KotlinLogging.logger {}

/**
 * Default category ID for uncategorized library entries.
 * Matches the Android domain's [Category.UNCATEGORIZED_ID].
 */
const val CATEGORY_DEFAULT_ID = 0L

/**
 * Preset category names used on first run.
 */
val DEFAULT_CATEGORIES = listOf(
    CategoryEntry(id = 0L, name = "Default", order = 0, isDefault = true),
    CategoryEntry(id = 1L, name = "Watching", order = 1),
    CategoryEntry(id = 2L, name = "Completed", order = 2),
    CategoryEntry(id = 3L, name = "Dropped", order = 3),
    CategoryEntry(id = 4L, name = "Plan to Watch", order = 4),
)

/**
 * A user-defined category for organizing library entries.
 */
@Serializable
data class CategoryEntry(
    val id: Long,
    val name: String,
    val order: Long = 0L,
    val isDefault: Boolean = false,
    val hidden: Boolean = false,
)

/**
 * JSON-backed repository for the user's anime library (favorites)
 * with category support.
 *
 * Stores anime the user has favorited/added to their library,
 * organized into user-defined categories.
 *
 * Data files:
 * - ~/Library/Application Support/Anikku/data/library.json (entries)
 * - ~/Library/Application Support/Anikku/data/categories.json
 */
class LibraryRepository(private val dataDir: File) {

    private val libraryFile = File(dataDir, "library.json")
    private val categoriesFile = File(dataDir, "categories.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class LibraryEntry(
        val animeId: Long,
        val title: String,
        val sourceId: Long = 0L,
        val url: String? = null,
        val thumbnailUrl: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Int = 0,
        val categoryId: Long = CATEGORY_DEFAULT_ID,
        val lastSecondSeen: Long = 0L,
        val totalSeconds: Long = 0L,
        val latestEpisodeNumber: Double = 0.0,
        val latestEpisodeName: String? = null,
        val unseenEpisodeCount: Int = 0,
        val addedAt: Long = System.currentTimeMillis(),
        val lastUpdatedAt: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private var categories: MutableList<CategoryEntry> = loadCategoriesFromFile()
    private var entries: MutableList<LibraryEntry> = loadFromFile()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        // Seed default categories on first run
        if (categories.isEmpty()) {
            categories = DEFAULT_CATEGORIES.toMutableList()
            saveCategoriesToFile()
        }
    }

    // -----------------------------------------------------------------------
    // Entry CRUD
    // -----------------------------------------------------------------------

    @Synchronized
    fun getAll(): List<LibraryEntry> = entries.toList()

    @Synchronized
    fun get(animeId: Long): LibraryEntry? = entries.find { it.animeId == animeId }

    @Synchronized
    fun isInLibrary(animeId: Long): Boolean = entries.any { it.animeId == animeId }

    @Synchronized
    fun add(entry: LibraryEntry) {
        val existing = entries.indexOfFirst { it.animeId == entry.animeId }
        if (existing >= 0) {
            entries[existing] = entry.copy(
                lastUpdatedAt = System.currentTimeMillis(),
            )
        } else {
            entries.add(entry)
        }
        saveToFile()
        _revision.value++
    }

    @Synchronized
    fun remove(animeId: Long): Boolean {
        val removed = entries.removeAll { it.animeId == animeId }
        if (removed) {
            saveToFile()
            _revision.value++
        }
        return removed
    }

    fun toggle(entry: LibraryEntry): Boolean {
        return if (isInLibrary(entry.animeId)) {
            remove(entry.animeId)
            false
        } else {
            add(entry)
            true
        }
    }

    fun count(): Int = entries.size

    /**
     * Update the resume position for an anime in the library.
     */
    @Synchronized
    fun updateProgress(animeId: Long, lastSecondSeen: Long, totalSeconds: Long) {
        val index = entries.indexOfFirst { it.animeId == animeId }
        if (index >= 0) {
            entries[index] = entries[index].copy(
                lastSecondSeen = lastSecondSeen,
                totalSeconds = totalSeconds,
                lastUpdatedAt = System.currentTimeMillis(),
            )
            saveToFile()
            _revision.value++
        }
    }

    /**
     * Move an entry to a different category.
     */
    @Synchronized
    fun moveToCategory(animeId: Long, categoryId: Long) {
        val index = entries.indexOfFirst { it.animeId == animeId }
        if (index >= 0) {
            entries[index] = entries[index].copy(
                categoryId = categoryId,
                lastUpdatedAt = System.currentTimeMillis(),
            )
            saveToFile()
            _revision.value++
        }
    }

    // -----------------------------------------------------------------------
    // Category CRUD
    // -----------------------------------------------------------------------

    fun getCategories(): List<CategoryEntry> = categories.toList()

    fun getCategory(id: Long): CategoryEntry? = categories.find { it.id == id }

    @Synchronized
    fun addCategory(name: String): CategoryEntry {
        val newId = (categories.maxOfOrNull { it.id } ?: 0L) + 1L
        val entry = CategoryEntry(id = newId, name = name, order = categories.size.toLong())
        categories.add(entry)
        saveCategoriesToFile()
        _revision.value++
        return entry
    }

    @Synchronized
    fun renameCategory(id: Long, newName: String): Boolean {
        val index = categories.indexOfFirst { it.id == id }
        if (index < 0 || categories[index].isDefault) return false
        categories[index] = categories[index].copy(name = newName)
        saveCategoriesToFile()
        _revision.value++
        return true
    }

    /**
     * Remove a category. Entries in that category move to Default.
     */
    @Synchronized
    fun removeCategory(id: Long): Boolean {
        val cat = categories.find { it.id == id } ?: return false
        if (cat.isDefault) return false
        categories.removeAll { it.id == id }
        // Move orphaned entries to Default
        entries = entries.map { entry ->
            if (entry.categoryId == id) entry.copy(categoryId = CATEGORY_DEFAULT_ID) else entry
        }.toMutableList()
        saveCategoriesToFile()
        saveToFile()
        _revision.value++
        return true
    }

    fun getEntriesByCategory(categoryId: Long): List<LibraryEntry> =
        entries.filter { it.categoryId == categoryId }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private fun loadFromFile(): MutableList<LibraryEntry> {
        if (!libraryFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<LibraryList>(libraryFile.readText())
            list.entries.toMutableList()
        } catch (error: Exception) {
            val backup = MacOSAtomicFile.preserveMalformed(libraryFile)
            libraryLogger.warn(error) {
                "Library JSON is malformed; starting with empty state" +
                    (backup?.let { ", preserved at ${it.name}" } ?: "")
            }
            mutableListOf()
        }
    }

    private fun saveToFile() {
        synchronized(this) {
            MacOSAtomicFile.writeText(libraryFile, json.encodeToString(LibraryList(entries)))
        }
    }

    private fun loadCategoriesFromFile(): MutableList<CategoryEntry> {
        if (!categoriesFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<CategoryList>(categoriesFile.readText())
            list.categories.toMutableList()
        } catch (error: Exception) {
            val backup = MacOSAtomicFile.preserveMalformed(categoriesFile)
            libraryLogger.warn(error) {
                "Categories JSON is malformed; starting with defaults" +
                    (backup?.let { ", preserved at ${it.name}" } ?: "")
            }
            mutableListOf()
        }
    }

    private fun saveCategoriesToFile() {
        synchronized(this) {
            MacOSAtomicFile.writeText(categoriesFile, json.encodeToString(CategoryList(categories)))
        }
    }

    @Serializable
    private data class LibraryList(val entries: List<LibraryEntry>)

    @Serializable
    private data class CategoryList(val categories: List<CategoryEntry>)
}

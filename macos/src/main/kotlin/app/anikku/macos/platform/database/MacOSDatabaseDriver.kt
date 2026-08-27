package app.anikku.macos.platform.database

import app.anikku.macos.platform.storage.MacOSStorageProvider
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Provides a JDBC-based SQLDelight SqlDriver for macOS desktop.
 * Database file: ~/Library/Application Support/Anikku/data/anime.db
 *
 * Creates the full database schema matching the shared data module's
 * SQLDelight definitions (animes, episodes, history, categories, etc.).
 *
 * Schema version tracked in the database via user_version pragma.
 * Current schema version: 129 (matches the shared data module).
 */
class MacOSDatabaseDriver(
    private val storageProvider: MacOSStorageProvider,
) {

    companion object {
        const val SCHEMA_VERSION = 129
    }

    fun createDriver(): SqlDriver {
        val dbDir = storageProvider.dataDirectory
        dbDir.mkdirs()

        val dbFile = File(dbDir, "anime.db")
        val dbPath = dbFile.absolutePath

        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")

        // Enable WAL mode and foreign keys BEFORE creating tables
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)

        // Create all tables, indices, triggers, and views.
        // All CREATE statements use IF NOT EXISTS so this is safe to run
        // every time — schema creation is idempotent.
        logger.info { "Creating database schema (version $SCHEMA_VERSION)..." }
        createSchema(driver)
        logger.info { "Database schema ready (version $SCHEMA_VERSION)" }

        return driver
    }

    private fun createSchema(driver: SqlDriver) {
        // =====================================================================
        // Table: anime_sync — tracker synchronization data
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS anime_sync(
                _id INTEGER NOT NULL PRIMARY KEY,
                anime_id INTEGER NOT NULL,
                sync_id INTEGER NOT NULL,
                remote_id INTEGER NOT NULL,
                library_id INTEGER,
                title TEXT NOT NULL,
                last_episode_seen REAL NOT NULL,
                total_episodes INTEGER NOT NULL,
                status INTEGER NOT NULL,
                score REAL NOT NULL,
                remote_url TEXT NOT NULL,
                start_date INTEGER NOT NULL,
                finish_date INTEGER NOT NULL,
                UNIQUE (anime_id, sync_id) ON CONFLICT REPLACE
            )
        """.trimIndent(), 0)

        // =====================================================================
        // Table: categories — library categories
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS categories(
                _id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                sort INTEGER NOT NULL,
                flags INTEGER NOT NULL,
                hidden INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent(), 0)

        // Insert system category
        driver.execute(null, """
            INSERT OR IGNORE INTO categories(_id, name, sort, flags, hidden)
            VALUES (0, '', -1, 0, 0)
        """.trimIndent(), 0)

        // System category delete protection
        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS system_category_delete_trigger BEFORE DELETE
            ON categories
            BEGIN SELECT CASE
                WHEN old._id <= 0 THEN
                    RAISE(ABORT, "System category can't be deleted")
                END;
            END
        """.trimIndent(), 0)

        // =====================================================================
        // Table: animes_categories — many-to-many anime-category mapping
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS animes_categories(
                anime_id INTEGER NOT NULL,
                category_id INTEGER NOT NULL,
                PRIMARY KEY (anime_id, category_id)
            )
        """.trimIndent(), 0)

        // =====================================================================
        // Table: animes — core anime library
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS animes(
                _id INTEGER NOT NULL PRIMARY KEY,
                source INTEGER NOT NULL,
                url TEXT NOT NULL,
                artist TEXT,
                author TEXT,
                description TEXT,
                genre TEXT,
                title TEXT NOT NULL,
                status INTEGER NOT NULL,
                thumbnail_url TEXT,
                favorite INTEGER NOT NULL DEFAULT 0,
                last_update INTEGER,
                next_update INTEGER,
                initialized INTEGER NOT NULL DEFAULT 0,
                viewer INTEGER NOT NULL DEFAULT 0,
                episode_flags INTEGER NOT NULL DEFAULT 0,
                cover_last_modified INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL DEFAULT 0,
                filtered_scanlators TEXT,
                update_strategy INTEGER NOT NULL DEFAULT 0,
                calculate_interval INTEGER NOT NULL DEFAULT 0,
                last_modified_at INTEGER NOT NULL DEFAULT 0,
                favorite_modified_at INTEGER,
                version INTEGER NOT NULL DEFAULT 0,
                is_syncing INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent(), 0)

        // Indices
        driver.execute(null, "CREATE INDEX IF NOT EXISTS library_favorite_index ON animes(favorite) WHERE favorite = 1", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS animes_url_index ON animes(url)", 0)

        // Triggers
        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS update_last_favorite_at_animes
            AFTER UPDATE OF favorite ON animes
            BEGIN
                UPDATE animes SET favorite_modified_at = CAST(strftime('%s', 'now') AS INTEGER) WHERE _id = new._id;
            END
        """.trimIndent(), 0)

        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS update_last_modified_at_animes
            AFTER UPDATE ON animes
            FOR EACH ROW
            BEGIN
                UPDATE animes SET last_modified_at = CAST(strftime('%s', 'now') AS INTEGER) WHERE _id = new._id;
            END
        """.trimIndent(), 0)

        // =====================================================================
        // Table: episodes
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS episodes(
                _id INTEGER NOT NULL PRIMARY KEY,
                anime_id INTEGER NOT NULL,
                url TEXT NOT NULL,
                name TEXT NOT NULL,
                scanlator TEXT,
                seen INTEGER NOT NULL DEFAULT 0,
                bookmark INTEGER NOT NULL DEFAULT 0,
                fillermark INTEGER NOT NULL DEFAULT 0,
                last_second_seen INTEGER NOT NULL DEFAULT 0,
                total_seconds INTEGER NOT NULL DEFAULT 0,
                episode_number REAL NOT NULL DEFAULT 0,
                source_order INTEGER NOT NULL DEFAULT 0,
                date_fetch INTEGER NOT NULL DEFAULT 0,
                date_upload INTEGER NOT NULL DEFAULT 0,
                last_modified_at INTEGER NOT NULL DEFAULT 0,
                version INTEGER NOT NULL DEFAULT 0,
                is_syncing INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(anime_id) REFERENCES animes(_id) ON DELETE CASCADE
            )
        """.trimIndent(), 0)

        driver.execute(null, "CREATE INDEX IF NOT EXISTS episodes_anime_id_index ON episodes(anime_id)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS episodes_unseen_by_anime_index ON episodes(anime_id, seen) WHERE seen = 0", 0)

        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS update_last_modified_at_episodes
            AFTER UPDATE ON episodes
            FOR EACH ROW
            BEGIN
                UPDATE episodes SET last_modified_at = CAST(strftime('%s', 'now') AS INTEGER) WHERE _id = new._id;
            END
        """.trimIndent(), 0)

        // =====================================================================
        // Table: history — watch history
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS history(
                _id INTEGER NOT NULL PRIMARY KEY,
                episode_id INTEGER NOT NULL UNIQUE,
                last_seen INTEGER,
                time_watch INTEGER NOT NULL,
                FOREIGN KEY(episode_id) REFERENCES episodes(_id) ON DELETE CASCADE
            )
        """.trimIndent(), 0)

        driver.execute(null, "CREATE INDEX IF NOT EXISTS history_episode_id_index ON history(episode_id)", 0)

        // =====================================================================
        // Table: sources — installed extension sources
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS sources(
                _id INTEGER NOT NULL PRIMARY KEY,
                lang TEXT NOT NULL,
                name TEXT NOT NULL
            )
        """.trimIndent(), 0)

        // =====================================================================
        // Table: merged — anime merge/mapping data
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS merged(
                anime_id INTEGER NOT NULL,
                merge_id INTEGER NOT NULL,
                PRIMARY KEY (anime_id, merge_id)
            )
        """.trimIndent(), 0)

        // =====================================================================
        // Table: excluded_scanlators — per-anime excluded scanlators
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS excluded_scanlators(
                anime_id INTEGER NOT NULL,
                scanlator TEXT NOT NULL,
                PRIMARY KEY (anime_id, scanlator)
            )
        """.trimIndent(), 0)

        // =====================================================================
        // Table: custom_buttons — mpv custom skip buttons
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS custom_buttons(
                _id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                sortIndex INTEGER NOT NULL DEFAULT 0,
                content TEXT NOT NULL,
                longPressContent TEXT NOT NULL,
                onStartup TEXT NOT NULL
            )
        """.trimIndent(), 0)

        // =====================================================================
        // Table: extension_repos — custom extension repositories
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS extension_repos(
                _id INTEGER NOT NULL PRIMARY KEY,
                url TEXT NOT NULL UNIQUE,
                name TEXT
            )
        """.trimIndent(), 0)

        // Insert default extension repo — macOS-optimized JAR repo
        driver.execute(null, """
            INSERT OR IGNORE INTO extension_repos(_id, url, name)
            VALUES (0, 'https://raw.githubusercontent.com/ErnestHysa/anikku-extensions-jar/main/', 'Anikku macOS Extensions')
        """.trimIndent(), 0)

        // =====================================================================
        // Table: library_update_error — library update error tracking
        // =====================================================================
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS library_update_error(
                _id INTEGER NOT NULL PRIMARY KEY,
                anime_id INTEGER NOT NULL,
                error_message TEXT,
                error_count INTEGER NOT NULL DEFAULT 0,
                last_error_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent(), 0)

        // =====================================================================
        // View: libraryView — library query view
        // =====================================================================
        driver.execute(null, """
            CREATE VIEW IF NOT EXISTS libraryView AS
            SELECT a.*, c.name AS category
            FROM animes a
            LEFT JOIN animes_categories ac ON a._id = ac.anime_id
            LEFT JOIN categories c ON ac.category_id = c._id
            WHERE a.favorite = 1
        """.trimIndent(), 0)

        // =====================================================================
        // View: historyView — history query view
        // =====================================================================
        driver.execute(null, """
            CREATE VIEW IF NOT EXISTS historyView AS
            SELECT h.*, e.anime_id, e.name AS episode_name, e.url AS episode_url,
                   e.episode_number, a.title AS anime_title, a.source
            FROM history h
            JOIN episodes e ON h.episode_id = e._id
            JOIN animes a ON e.anime_id = a._id
            ORDER BY h.last_seen DESC
        """.trimIndent(), 0)

        // =====================================================================
        // View: updatesView — recent updates view
        // =====================================================================
        driver.execute(null, """
            CREATE VIEW IF NOT EXISTS updatesView AS
            SELECT e._id AS episode_id, e.*, a.title AS anime_title,
                   a.source, a.thumbnail_url
            FROM episodes e
            JOIN animes a ON e.anime_id = a._id
            WHERE a.favorite = 1
            ORDER BY e.date_upload DESC
        """.trimIndent(), 0)

        logger.info { "All database tables and views created successfully" }
    }
}

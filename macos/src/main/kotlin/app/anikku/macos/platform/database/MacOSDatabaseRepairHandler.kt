package app.anikku.macos.platform.database

import app.anikku.macos.platform.logging.CrashReporter
import app.anikku.macos.platform.storage.MacOSStorageProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Handles database corruption detection, repair, and recovery.
 *
 * Uses JDBC directly for integrity checks (simpler than SQLDelight's
 * abstract QueryResult API). All database operations are synchronous
 * and should be called from a background coroutine.
 *
 * ## Usage
 *
 * ```kotlin
 * val repairHandler = MacOSDatabaseRepairHandler(storageProvider)
 *
 * when (repairHandler.checkDatabaseIntegrity()) {
 *     DatabaseIntegrity.OK -> { /* proceed normally */ }
 *     DatabaseIntegrity.CORRUPT -> {
 *         repairHandler.repairOrReset()
 *     }
 * }
 * ```
 */
class MacOSDatabaseRepairHandler(
    private val storageProvider: MacOSStorageProvider,
) {

    private val dbFile: File get() = File(storageProvider.dataDirectory, "anime.db")
    private val backupsDir: File get() = File(storageProvider.dataDirectory, "db_backups")
    private val dbUrl: String get() = "jdbc:sqlite:${dbFile.absolutePath}"

    /**
     * Result of a database integrity check.
     */
    enum class DatabaseIntegrity {
        /** Database is healthy and usable. */
        OK,
        /** Database file does not exist (first launch or after reset). */
        NOT_FOUND,
        /** Database is corrupted and needs repair. */
        CORRUPT,
        /** Database file cannot be accessed (permission issue). */
        ACCESS_DENIED,
    }

    /**
     * Result of a repair attempt.
     */
    sealed class RepairResult {
        /** Database was repaired successfully. */
        data object Repaired : RepairResult()
        /** Database was reset to a fresh state. */
        data object Reset : RepairResult()
        /** Repair failed and manual intervention is needed. */
        data class Failed(val reason: String) : RepairResult()
    }

    /**
     * Run a quick integrity check on the database using PRAGMA integrity_check.
     *
     * Opens a JDBC connection directly to avoid SQLDelight API complexities.
     *
     * @return The integrity status.
     */
    fun checkDatabaseIntegrity(): DatabaseIntegrity {
        if (!dbFile.exists()) {
            return DatabaseIntegrity.NOT_FOUND
        }

        if (!dbFile.canRead()) {
            CrashReporter.logError("Database", "Database file not readable: ${dbFile.absolutePath}")
            return DatabaseIntegrity.ACCESS_DENIED
        }

        return try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("PRAGMA integrity_check")
                    val result = if (rs.next()) rs.getString(1) else "ok"
                    if (result == "ok") {
                        DatabaseIntegrity.OK
                    } else {
                        CrashReporter.logError("Database", "Integrity check failed: $result")
                        DatabaseIntegrity.CORRUPT
                    }
                }
            }
        } catch (e: SQLException) {
            CrashReporter.logError("Database", "Integrity check threw exception", e)
            DatabaseIntegrity.CORRUPT
        }
    }

    /**
     * Attempt to repair the database or reset it if repair fails.
     *
     * Steps:
     * 1. Create a backup of the corrupted database
     * 2. Attempt `VACUUM` to rebuild and repair
     * 3. Run integrity check again
     * 4. If still corrupted, initialize a fresh database
     *
     * @return The result of the repair attempt.
     */
    fun repairOrReset(): RepairResult {
        if (!dbFile.exists()) {
            return RepairResult.Reset
        }

        // Step 1: Backup the corrupted database
        backupCorruptedDatabase()

        // Step 2: Attempt VACUUM to rebuild
        try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("VACUUM")
                }
            }

            // Step 3: Verify repair
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("PRAGMA integrity_check")
                    val result = if (rs.next()) rs.getString(1) else "ok"
                    if (result == "ok") {
                        logger.info { "Database repaired via VACUUM" }
                        return RepairResult.Repaired
                    }
                }
            }
        } catch (e: SQLException) {
            CrashReporter.logError("Database", "VACUUM repair failed", e)
        }

        // Step 4: If repair failed, reset to fresh database
        return try {
            resetDatabase()
            RepairResult.Reset
        } catch (e: Exception) {
            RepairResult.Failed("Failed to reset database: ${e.message}")
        }
    }

    /**
     * Create a dated backup of the current database file.
     *
     * @return The backup file path, or null if backup failed.
     */
    fun backupCorruptedDatabase(): File? {
        return try {
            backupsDir.mkdirs()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val backupFile = File(backupsDir, "anime-corrupted-$timestamp.db")
            java.nio.file.Files.copy(
                dbFile.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            CrashReporter.logEvent("Database backup created", backupFile.absolutePath)
            logger.info { "Corrupted database backed up to: ${backupFile.absolutePath}" }
            backupFile
        } catch (e: Exception) {
            CrashReporter.logError("Database", "Failed to backup corrupted database", e)
            null
        }
    }

    /**
     * Reset the database by deleting the current file.
     * A fresh database will be created on next app launch.
     */
    fun resetDatabase() {
        try {
            if (dbFile.exists()) {
                dbFile.delete()
                logger.info { "Database file deleted for fresh initialization" }
            }

            // Also delete WAL and SHM files
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()

            CrashReporter.logEvent("Database reset", "Fresh database will be created on next launch")
        } catch (e: Exception) {
            CrashReporter.logError("Database", "Failed to reset database", e)
            throw e
        }
    }

    /**
     * List available backups in the backups directory.
     */
    fun listBackups(): List<File> {
        if (!backupsDir.exists()) return emptyList()
        return backupsDir.listFiles()
            ?.filter { it.name.startsWith("anime-") && it.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Restore a database from a backup file.
     *
     * @param backupFile The backup file to restore from.
     * @return true if restore succeeded.
     */
    fun restoreFromBackup(backupFile: File): Boolean {
        return try {
            resetDatabase()
            java.nio.file.Files.copy(
                backupFile.toPath(),
                dbFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            logger.info { "Database restored from backup: ${backupFile.absolutePath}" }
            CrashReporter.logEvent("Database restored from backup", backupFile.absolutePath)
            true
        } catch (e: Exception) {
            CrashReporter.logError("Database", "Failed to restore from backup", e)
            false
        }
    }
}

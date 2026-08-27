package app.anikku.macos.platform.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MacOSDatabaseDriverTest {

    @Test
    fun `create JdbcSqliteDriver with temp directory succeeds`(@TempDir tempDir: File) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(tempDir, "test.db").absolutePath}")
        assertNotNull(driver)
    }

    @Test
    fun `driver can execute CREATE TABLE`(@TempDir tempDir: File) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(tempDir, "create.db").absolutePath}")
        assertDoesNotThrow {
            driver.execute(null, "CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY)", 0)
        }
    }

    @Test
    fun `driver can execute INSERT`(@TempDir tempDir: File) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(tempDir, "insert.db").absolutePath}")
        driver.execute(null, "CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY)", 0)
        assertDoesNotThrow {
            driver.execute(null, "INSERT OR IGNORE INTO test VALUES (1)", 0)
        }
    }

    @Test
    fun `driver can execute PRAGMA statements`(@TempDir tempDir: File) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(tempDir, "pragma.db").absolutePath}")
        driver.execute(null, "CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY)", 0)
        assertDoesNotThrow {
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        }
    }

    @Test
    fun `driver can enable WAL mode`(@TempDir tempDir: File) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(tempDir, "wal.db").absolutePath}")
        assertDoesNotThrow {
            driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        }
    }
}

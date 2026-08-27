package app.anikku.macos.platform.logging

import app.anikku.macos.platform.storage.MacOSStorageProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class TerminalErrorLoggerTest {

    @BeforeEach
    fun setup() {
        TerminalErrorLogger.clear()
    }

    @AfterEach
    fun tearDown() {
        TerminalErrorLogger.clear()
    }

    @Test
    fun `logUiError stores error with sequential id`() {
        TerminalErrorLogger.logUiError("Something failed")
        TerminalErrorLogger.logUiError("Another failure")

        val errors = TerminalErrorLogger.errors
        assertEquals(2, errors.size)
        assertEquals(1L, errors[0].id)
        assertEquals(2L, errors[1].id)
    }

    @Test
    fun `logUiError captures source and location`() {
        val exception = RuntimeException("Boom")
        TerminalErrorLogger.logUiError(
            message = "Extension failed",
            source = "TestExtension",
            throwable = exception,
            location = "TestScreen.load",
        )

        val error = TerminalErrorLogger.errors.single()
        assertEquals("Extension failed", error.message)
        assertEquals("TestExtension", error.source)
        assertEquals("TestScreen.load", error.location)
        assertNotNull(error.throwable)
        assertEquals("Boom", error.throwable?.message)
        assertNotNull(error.stackTrace)
    }

    @Test
    fun `errorCount returns number of captured errors`() {
        assertEquals(0, TerminalErrorLogger.errorCount)
        TerminalErrorLogger.logUiError("One")
        TerminalErrorLogger.logUiError("Two")
        assertEquals(2, TerminalErrorLogger.errorCount)
    }

    @Test
    fun `clear removes all errors and resets id`() {
        TerminalErrorLogger.logUiError("One")
        TerminalErrorLogger.logUiError("Two")
        TerminalErrorLogger.clear()

        assertEquals(0, TerminalErrorLogger.errorCount)

        TerminalErrorLogger.logUiError("Three")
        assertEquals(1L, TerminalErrorLogger.errors.single().id)
    }

    @Test
    fun `printShutdownSummary does not throw when no errors`() {
        TerminalErrorLogger.printShutdownSummary()
        assert(true)
    }

    @Test
    fun `printShutdownSummary does not throw with errors`() {
        TerminalErrorLogger.logUiError("Failure", source = "TestSource")
        TerminalErrorLogger.printShutdownSummary()
        assert(true)
    }

    @Test
    fun `printShutdownSummary writes summary to crash log file`() {
        // Initialize CrashReporter so logBlock() writes to a real file.
        // CrashReporter writes to ~/Library/Logs/Anikku/ by default.
        CrashReporter.initialize(MacOSStorageProvider(), version = "test")

        TerminalErrorLogger.logUiError("Test error", source = "TestSource", location = "Test.location")
        TerminalErrorLogger.logUiError("Another error", source = "TestSource", location = "Test.location2")

        TerminalErrorLogger.printShutdownSummary()

        // Verify the crash log file exists and contains the summary.
        val crashLogs = CrashReporter.getRecentCrashLogs()
        assertTrue(crashLogs.isNotEmpty(), "Expected at least one crash log file to exist")

        val crashLogContent = crashLogs.first().readText()
        assertTrue(
            crashLogContent.contains("UI_ERROR_SUMMARY"),
            "Crash log should contain the UI error summary block",
        )
        assertTrue(
            crashLogContent.contains("ANIKKU MACOS — UI ERROR SUMMARY"),
            "Crash log should contain the summary header",
        )
        assertTrue(
            crashLogContent.contains("TestSource"),
            "Crash log summary should contain the error source",
        )

        // Cleanup — remove crash log files created by this test.
        crashLogs.forEach { it.delete() }
    }

}


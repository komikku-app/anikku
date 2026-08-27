package app.anikku.macos.platform.notification

import org.junit.jupiter.api.Test

/**
 * Tests for MacOSNotificationManager.
 *
 * Note: System tray tests are difficult in headless CI environments.
 * These tests verify the API surface and edge cases without relying
 * on the actual macOS Notification Center.
 */
class MacOSNotificationManagerTest {

    @Test
    fun `create notification manager does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        // No exception means creation succeeded
        assert(true)
    }

    @Test
    fun `show notification does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        // In headless environment, this should gracefully fall back
        manager.showNotification("Test Title", "Test Message", NotificationType.INFO)
        assert(true)
    }

    @Test
    fun `show error does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        manager.showError("Error Title", "Error Message")
        assert(true)
    }

    @Test
    fun `show download complete does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        manager.showDownloadComplete("Anime Title", "Episode 1")
        assert(true)
    }

    @Test
    fun `show library update does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        manager.showLibraryUpdate(5)
        assert(true)
    }

    @Test
    fun `show backup reminder does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        manager.showBackupReminder()
        assert(true)
    }

    @Test
    fun `shutdown does not throw`() {
        val manager = MacOSNotificationManager(appName = "TestApp")
        manager.shutdown()
        assert(true)
    }
}

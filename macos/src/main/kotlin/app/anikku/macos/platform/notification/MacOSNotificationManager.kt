package app.anikku.macos.platform.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit
import java.net.URI
import javax.swing.SwingUtilities

private val logger = KotlinLogging.logger {}

/**
 * macOS notification manager.
 *
 * Replaces Android's NotificationManager + NotificationChannel system.
 * Uses macOS Notification Center via:
 * 1. `java.awt.TrayIcon.displayMessage()` (primary method)
 * 2. `osascript` / `terminal-notifier` (fallback with richer notifications)
 *
 * ## Usage
 *
 * ```kotlin
 * val notifier = MacOSNotificationManager(appName = "Anikku")
 *
 * // Simple notification
 * notifier.showNotification("Download Complete", "Attack on Titan - Episode 3")
 *
 * // Notification with action
 * notifier.showNotification(
 *     title = "Library Update",
 *     message = "12 new episodes available",
 *     type = NotificationType.INFO,
 *     onClick = { onOpenLibrary() }
 * )
 * ```
 */
class MacOSNotificationManager(
    private val appName: String = "Anikku",
) {

    private var trayIcon: TrayIcon? = null
    private var clickCallback: (() -> Unit)? = null

    /**
     * Initialize the notification manager.
     * Sets up the system tray icon (required for `TrayIcon.displayMessage()` on macOS).
     */
    fun initialize() {
        if (!SystemTray.isSupported()) {
            logger.warn { "System tray not supported on this platform" }
            return
        }

        try {
            SwingUtilities.invokeLater {
                if (SystemTray.getSystemTray().trayIcons.isEmpty()) {
                    // Create a 16x16 transparent icon (required for tray access)
                    val image = createTrayIcon()
                    val icon = TrayIcon(image, appName).apply {
                        isImageAutoSize = true
                        toolTip = appName
                        addActionListener {
                            clickCallback?.invoke()
                        }
                    }
                    SystemTray.getSystemTray().add(icon)
                    trayIcon = icon
                    logger.info { "Notification manager initialized" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to initialize system tray for notifications" }
        }
    }

    /**
     * Show a notification in macOS Notification Center.
     *
     * @param title The notification title.
     * @param message The notification body.
     * @param type The notification type (affects icon/emphasis).
     * @param onClick Optional callback when the notification is clicked.
     */
    fun showNotification(
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFO,
        onClick: (() -> Unit)? = null,
    ) {
        clickCallback = onClick

        // Try system tray notification first
        val traySuccess = showTrayNotification(title, message, type)

        // If tray failed, try AppleScript fallback
        if (!traySuccess) {
            showAppleScriptNotification(title, message)
        }

        logger.info { "Notification: [$type] $title — $message" }
    }

    /**
     * Show an error notification.
     */
    fun showError(title: String, message: String) {
        showNotification(title, message, NotificationType.ERROR)
    }

    /**
     * Show a download complete notification.
     *
     * @param animeTitle The anime name.
     * @param episodeName The episode name/number.
     */
    fun showDownloadComplete(animeTitle: String, episodeName: String) {
        showNotification(
            title = "Download Complete",
            message = "$animeTitle — $episodeName",
            type = NotificationType.SUCCESS,
        )
    }

    /**
     * Show a library update notification.
     *
     * @param newCount Number of new episodes available.
     */
    fun showLibraryUpdate(newCount: Int) {
        showNotification(
            title = "Library Update",
            message = "$newCount new episode${if (newCount != 1) "s" else ""} available",
            type = NotificationType.INFO,
        )
    }

    /**
     * Show a backup reminder notification.
     */
    fun showBackupReminder() {
        showNotification(
            title = "Backup Reminder",
            message = "It's been a while since your last backup",
            type = NotificationType.WARNING,
        )
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun showTrayNotification(
        title: String,
        message: String,
        type: NotificationType,
    ): Boolean {
        val icon = trayIcon ?: return false

        return try {
            val messageType = when (type) {
                NotificationType.INFO -> TrayIcon.MessageType.INFO
                NotificationType.SUCCESS -> TrayIcon.MessageType.NONE
                NotificationType.WARNING -> TrayIcon.MessageType.WARNING
                NotificationType.ERROR -> TrayIcon.MessageType.ERROR
            }
            icon.displayMessage(title, message, messageType)
            true
        } catch (e: Exception) {
            logger.warn(e) { "Tray notification failed" }
            false
        }
    }

    /**
     * Fallback notification method using AppleScript.
     * This produces richer macOS notifications that appear in Notification Center.
     */
    private fun showAppleScriptNotification(title: String, message: String) {
        try {
            val script = """
                display notification "$message" with title "$appName" subtitle "$title"
            """.trimIndent()

            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            process.waitFor()
        } catch (e: Exception) {
            logger.warn(e) { "AppleScript notification failed" }
        }
    }

    /**
     * Open a URL when the notification is clicked.
     */
    fun setNotificationUrl(url: String) {
        clickCallback = {
            try {
                java.awt.Desktop.getDesktop().browse(URI(url))
            } catch (e: Exception) {
                logger.warn(e) { "Failed to open notification URL: $url" }
            }
        }
    }

    /**
     * Clean up resources on shutdown.
     */
    fun shutdown() {
        try {
            SwingUtilities.invokeLater {
                trayIcon?.let {
                    SystemTray.getSystemTray().remove(it)
                }
                trayIcon = null
            }
        } catch (_: Exception) {
            // Safe to ignore during shutdown
        }
    }

    /**
     * Create minimal 16x16 icon bytes (a transparent PNG).
     */
    private fun createTrayIcon(): java.awt.Image {
        // Create a 1x1 transparent icon directly without PNG decoding.
        // Using BufferedImage avoids AWT's asynchronous PNGImageDecoder
        // which can produce CRC corruption warnings in some JVM versions.
        val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        // Ensure fully transparent
        image.setRGB(0, 0, 0x00000000.toInt())
        return image
    }
}

/**
 * Notification type affecting display style.
 */
enum class NotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

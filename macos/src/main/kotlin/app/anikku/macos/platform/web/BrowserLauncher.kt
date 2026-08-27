package app.anikku.macos.platform.web

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.net.URI

/**
 * Centralized browser launcher for opening external URLs in the system's
 * default web browser.
 *
 * Replaces Android's [WebView] / [CustomTabsIntent] with the standard
 * macOS practice of opening the system browser (Safari, Chrome, etc.).
 *
 * ## Usage
 *
 * ```kotlin
 * // Open an external URL
 * BrowserLauncher.open("https://github.com/ErnestHysa/anikku")
 *
 * // Open a URI
 * BrowserLauncher.open(URI("https://anilist.co"))
 *
 * // Open safely (handles URIs that may fail parsing)
 * BrowserLauncher.openSafe("not a valid url")
 * ```
 *
 * ## Thread Safety
 *
 * All methods are safe to call from any thread.
 * `java.awt.Desktop.browse()` must be called on the EDT.
 * The [open] and [openSafe] methods handle this automatically by
 * dispatching to the EDT via [java.awt.EventQueue.invokeLater]
 * when called from a non-EDT thread.
 */
object BrowserLauncher {

    private val logger = KotlinLogging.logger {}

    /** Whether the system browser is available (i.e., Desktop is supported). */
    val isAvailable: Boolean by lazy {
        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
    }

    // -----------------------------------------------------------------------
    // Test support — set testMode = true to capture URIs without opening
    // the system browser during unit tests.
    // -----------------------------------------------------------------------

    /** When true, captures [lastOpenedUri] instead of calling Desktop.browse(). */
    internal var testMode: Boolean = false

    /** The last URI that would have been opened (populated in testMode). */
    internal var lastOpenedUri: URI? = null

    /**
     * Opens the given URL in the system's default web browser.
     *
     * @param url The URL to open. Must be a well-formed absolute URL.
     * @throws IllegalArgumentException if [url] cannot be parsed as a URI.
     * @throws java.awt.HeadlessException if the environment has no display.
     * @throws java.io.IOException if the browser fails to launch.
     */
    fun open(url: String) {
        open(URI(url))
    }

    /**
     * Opens the given URI in the system's default web browser.
     *
     * @param uri The URI to open.
     * @throws java.awt.HeadlessException if the environment has no display.
     * @throws java.io.IOException if the browser fails to launch.
     */
    fun open(uri: URI) {
        if (testMode) {
            doBrowse(uri)
            return
        }
        if (!isAvailable) {
            logger.warn { "Desktop browse not available in this environment" }
            return
        }

        if (java.awt.EventQueue.isDispatchThread()) {
            doBrowse(uri)
        } else {
            java.awt.EventQueue.invokeLater { doBrowse(uri) }
        }
    }

    /**
     * Safely opens a URL, catching any exceptions silently.
     * Use this when the URL may be malformed or the browser unavailable.
     *
     * @param url The URL to open. Invalid URLs are silently ignored.
     * @return true if the URL was successfully opened.
     */
    fun openSafe(url: String): Boolean {
        return try {
            open(URI(url))
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to open URL: $url" }
            false
        }
    }

    /**
     * Safely opens a URI, catching any exceptions silently.
     *
     * @param uri The URI to open.
     * @return true if the URI was successfully opened.
     */
    fun openSafe(uri: URI): Boolean {
        return try {
            open(uri)
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to open URI: $uri" }
            false
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun doBrowse(uri: URI) {
        if (testMode) {
            lastOpenedUri = uri
            logger.debug { "Test mode — would have opened: $uri" }
            return
        }
        try {
            Desktop.getDesktop().browse(uri)
            logger.debug { "Opened browser: $uri" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to open browser: $uri" }
        }
    }
}

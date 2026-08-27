package app.anikku.macos.platform

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URLDecoder
import javax.swing.SwingUtilities

private val logger = KotlinLogging.logger {}

/**
 * macOS URL-scheme deep-link handling (`anikku://…`).
 *
 * Registers a lightweight NSObject subclass as the NSApplication delegate so
 * `application:openURLs:`/`application:openURL:` fire while the app is already
 * running (the Watch Together join page uses this to open a magnet room's
 * episode straight into the player). The parsed target is delivered through
 * [onWatchDeepLink] — MainWindow installs the handler that pushes the player.
 */
object MacOSDeepLinkHandler {

    /** A `anikku://watch` target parsed from a deep link. */
    data class WatchTarget(
        val animeId: Long,
        val episodeId: Long,
        val sourceId: Long? = null,
        val episodeUrl: String? = null,
        val animeTitle: String? = null,
        val episodeName: String? = null,
        val episodeNumber: Double? = null,
        val coverUrl: String? = null,
    )

    /** Invoked (on the AWT event thread) when the app receives an `anikku://` link. */
    @Volatile
    var onWatchDeepLink: ((WatchTarget) -> Unit)? = null

    private fun interface OpenUrlsCallback : Callback {
        fun invoke(self: Pointer, cmd: Pointer, app: Pointer, urls: Pointer): Long
    }

    private fun interface OpenUrlCallback : Callback {
        fun invoke(self: Pointer, cmd: Pointer, app: Pointer, url: Pointer): Long
    }

    private val openUrlsCallback = OpenUrlsCallback { _, _, _, urls ->
        dispatchUrls(urls)
        0L
    }
    private val openUrlCallback = OpenUrlCallback { _, _, _, url ->
        dispatchUrl(url)
        0L
    }

    /**
     * Install the delegate. Idempotent-ish: each call creates a fresh delegate
     * class — call once at startup.
     */
    fun install() {
        try {
            val nsObject = ObjC.objc_getClass("NSObject")
            if (Pointer.nativeValue(nsObject) == 0L) return
            val delegateClass = ObjC.objc_allocateClassPair(nsObject, "AnikkuDeepLinkDelegate")
            if (Pointer.nativeValue(delegateClass) == 0L) return

            val addedOpenUrls = ObjC.class_addMethod(
                delegateClass,
                ObjC.sel_registerName("application:openURLs:"),
                CallbackReference.getFunctionPointer(openUrlsCallback),
                "v@:@@",
            )
            val addedOpenUrl = ObjC.class_addMethod(
                delegateClass,
                ObjC.sel_registerName("application:openURL:"),
                CallbackReference.getFunctionPointer(openUrlCallback),
                "v@:@@",
            )
            if (!addedOpenUrls && !addedOpenUrl) return
            ObjC.objc_registerClassPair(delegateClass)

            val delegate = ObjC.objc_msgSend(delegateClass, ObjC.sel_registerName("new"))
            if (delegate == null || Pointer.nativeValue(delegate) == 0L) return
            // NSApplication's delegate is not retained — keep ours alive.
            ObjC.objc_msgSend_void(delegate, ObjC.sel_registerName("retain"))

            val nsApp = ObjC.objc_getClass("NSApplication")
            val sharedApp = ObjC.objc_msgSend(nsApp, ObjC.sel_registerName("sharedApplication"))
            if (sharedApp == null || Pointer.nativeValue(sharedApp) == 0L) return
            ObjC.objc_msgSend_void(sharedApp, ObjC.sel_registerName("setDelegate:"), delegate)
            logger.info { "Deep-link handler installed (anikku:// scheme)" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to install deep-link handler" }
        }
    }

    /**
     * Parse a `anikku://watch?...` URL into a [WatchTarget], or null when the
     * link isn't a watch link or is missing required fields.
     */
    fun parseWatchLink(url: String): WatchTarget? {
        if (!url.startsWith("anikku://", ignoreCase = true)) return null
        val queryStart = url.indexOf('?')
        val path = if (queryStart >= 0) url.substring(0, queryStart) else url
        val action = path.removePrefix("anikku://").substringBefore('/')
        if (!action.equals("watch", ignoreCase = true)) return null
        if (queryStart < 0) return null

        val params = mutableMapOf<String, String>()
        url.substring(queryStart + 1).split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = pair.substring(0, eq)
                val value = runCatching { URLDecoder.decode(pair.substring(eq + 1), "UTF-8") }
                    .getOrDefault(pair.substring(eq + 1))
                params[key] = value
            }
        }
        val animeId = params["animeId"]?.toLongOrNull() ?: return null
        val episodeId = params["episodeId"]?.toLongOrNull() ?: return null
        return WatchTarget(
            animeId = animeId,
            episodeId = episodeId,
            sourceId = params["sourceId"]?.toLongOrNull(),
            episodeUrl = params["episodeUrl"],
            animeTitle = params["animeTitle"],
            episodeName = params["episodeName"],
            episodeNumber = params["episodeNumber"]?.toDoubleOrNull(),
            coverUrl = params["coverUrl"],
        )
    }

    // -----------------------------------------------------------------------
    // Internal — native callbacks
    // -----------------------------------------------------------------------

    private fun dispatchUrls(urls: Pointer) {
        if (Pointer.nativeValue(urls) == 0L) return
        val count = ObjC.objc_msgSend_long(urls, ObjC.sel_registerName("count"))
        for (i in 0 until count) {
            val url = Pointer(ObjC.objc_msgSend_long(urls, ObjC.sel_registerName("objectAtIndex:"), i.toLong()))
            dispatchUrl(url)
        }
    }

    private fun dispatchUrl(url: Pointer) {
        if (url == null || Pointer.nativeValue(url) == 0L) return
        val absSel = ObjC.sel_registerName("absoluteString")
        val nsString = ObjC.objc_msgSend(url, absSel)
        if (nsString == null || Pointer.nativeValue(nsString) == 0L) return
        val utf8 = ObjC.objc_msgSend(nsString, ObjC.sel_registerName("UTF8String"))
        if (utf8 == null || Pointer.nativeValue(utf8) == 0L) return
        val link = utf8.getString(0)
        logger.info { "Deep link received: $link" }
        // The AppKit callback runs on the main thread; deliver on the AWT EDT.
        SwingUtilities.invokeLater { deliver(link) }
    }

    private fun deliver(link: String) {
        val target = parseWatchLink(link)
        if (target != null) {
            onWatchDeepLink?.invoke(target)
        } else {
            logger.warn { "Ignoring unrecognized deep link: $link" }
        }
    }
}

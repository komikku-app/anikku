package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Cloudflare bypass via Chrome DevTools Protocol (CDP).
 *
 * Maintains a **persistent headless Chrome instance** that stays alive across
 * all bypass requests. This eliminates the 1-3s per-request Chrome launch
 * overhead and allows the browser to maintain TLS session state, DNS cache,
 * and cookie state between navigation cycles.
 *
 * ## Why persistent instead of per-request launch
 *
 * - **No launch overhead** — Chrome starts once, stays running
 * - **TLS session reuse** — Cloudflare sees a consistent browser fingerprint
 * - **Faster challenges** — the browser already has warmed TLS + JS caches
 * - **Automatic reconnection** — if Chrome crashes, the next request restarts it
 * - **Graceful shutdown** — call [shutdown] when the app exits
 *
 * ## Thread safety
 *
 * Only one bypass attempt runs at a time. Concurrent bypasses from multiple
 * extensions queue up — only the first actually uses Chrome, subsequent ones
 * wait and reuse cached cookies from [MacOSCookieJar].
 */
object ChromeCDPClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lock = ReentrantLock()

    // ── Persistent Chrome state ────────────────────────────────────────

    /** Persistent Chrome process — launched once, kept alive across calls. */
    @Volatile
    private var chromeProcess: Process? = null

    /** Debug port the persistent Chrome is listening on. */
    @Volatile
    private var chromePort: Int = -1

    /** WebSocket URL for the persistent Chrome's browser-level debugger. */
    @Volatile
    private var chromeWsUrl: String? = null

    /** Whether we've ever successfully started Chrome (to avoid re-logging startup messages). */
    private var hasEverStartedChrome = false

    /**
     * Custom Chrome/Chromium executable path. Set before any bypass call.
     * If empty, auto-detects from standard install locations:
     * 1. /Applications/Google Chrome.app (default)
     * 2. /opt/homebrew/bin/chromium (Homebrew Chromium)
     * 3. /Applications/Brave Browser.app
     * 4. /Applications/Microsoft Edge.app
     */
    @Volatile
    var customChromePath: String = ""

    /**
     * CDP debug mode — when enabled, every WebSocket message sent/received
     * is logged at INFO level for troubleshooting WAF bypass issues.
     * Set from Network Settings to inspect the raw CDP traffic.
     */
    @Volatile
    var debugMode: Boolean = false

    /** Ring buffer of captured CDP debug messages. Thread-safe, bounded at MAX_DEBUG_MESSAGES. */
    private val debugLog = ConcurrentLinkedQueue<String>()
    private const val MAX_DEBUG_MESSAGES = 500

    /**
     * Return all captured CDP debug messages as a timestamped, newline-joined string.
     * Used by Network Settings to export the log to a file for support purposes.
     */
    fun getDebugLog(): String = debugLog.joinToString("\n")

    /**
     * Clear the in-memory debug log buffer.
     * Called after a successful export so the next export starts fresh.
     */
    fun clearDebugLog() { debugLog.clear() }

    /**
     * Is Chrome (or alternative browser) installed?
     * Computed property (not lazy) so [customChromePath] set after object init is respected.
     */
    val isChromeInstalled: Boolean get() = chromeExecutable().isFile

    /**
     * Is the persistent Chrome instance currently running and responding?
     * Used by diagnostics to show Chrome lifecycle status.
     */
    val isRunning: Boolean get() {
        val proc = chromeProcess
        if (proc == null || !proc.isAlive) return false
        val port = chromePort
        if (port < 0) return false
        // Quick check: try to hit the /json/version endpoint
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/json/version")
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    /**
     * Maximum number of retry attempts for a single Cloudflare bypass.
     * Some sites return intermediate challenge pages that require multiple
     * navigation cycles to resolve (e.g., Turnstile, reCAPTCHA overlay).
     */
    private const val MAX_BYPASS_RETRIES = 3

    /**
     * Fetch Cloudflare bypass cookies for a URL.
     * Thread-safe — only one bypass runs at a time.
     *
     * Uses the persistent Chrome instance. If Chrome has not been started yet,
     * or has crashed since last use, it is (re)started automatically.
     *
     * @param url The Cloudflare-protected URL to visit
     * @param userAgent The User-Agent to use
     * @param timeoutSeconds Max time to wait for challenge resolution (default 60s)
     * @param referer Optional Referer header to pass during navigation
     * @return Map of cookie name → value, or empty map on failure
     */
    fun fetchCloudflareCookies(
        url: String,
        userAgent: String,
        timeoutSeconds: Long = 60,
        referer: String? = null,
    ): Map<String, String> {
        if (!isChromeInstalled) {
            logger.warn { "Chrome not found — Cloudflare bypass unavailable" }
            return emptyMap()
        }

        lock.withLock {
            // Determine URLs to try. Primary URL first; if it's a resource URL (video, m3u8,
            // image, etc.) that won't serve a Cloudflare challenge page, also queue the domain
            // root URL as a fallback. The domain root WILL serve a proper challenge page that
            // Chrome can solve, and the resulting cookies apply to all subdomains.
            val urlsToTry = mutableListOf(url)
            if (!isPageUrl(url)) {
                val origin = extractOrigin(url)
                if (origin != url) {
                    urlsToTry.add(origin)
                    logger.debug { "Added domain root fallback URL: $origin (primary $url is a resource URL)" }
                }
            }

            for (attempt in 1..MAX_BYPASS_RETRIES) {
                val targetUrl = urlsToTry[(attempt - 1) % urlsToTry.size]

                try {
                    // Ensure persistent Chrome is running. If it died, this restarts it.
                    if (!ensureChromeRunning()) {
                        logger.warn { "Failed to start persistent Chrome (attempt $attempt/$MAX_BYPASS_RETRIES)" }
                        if (attempt < MAX_BYPASS_RETRIES) {
                            try { Thread.sleep(2000L * attempt) } catch (_: Exception) {}
                        }
                        continue
                    }

                    val wsUrl = chromeWsUrl ?: run {
                        logger.warn { "No WebSocket URL available for persistent Chrome (attempt $attempt/$MAX_BYPASS_RETRIES)" }
                        // Try to recover — Chrome may have been restarted but WS URL not captured
                        refreshWsUrl()
                        if (chromeWsUrl == null) continue
                        chromeWsUrl!!
                    }

                    // Navigate to URL and wait for Cloudflare challenge to resolve
                    val cookies = navigateAndWait(wsUrl, targetUrl, timeoutSeconds, userAgent, referer)
                    if (cookies.isNotEmpty()) {
                        val urlLabel = if (targetUrl == url) "primary" else "domain-root"
                        logger.info { "✅ Cloudflare bypass succeeded on attempt $attempt/$MAX_BYPASS_RETRIES ($urlLabel) — got ${cookies.size} cookie(s)" }
                        return cookies
                    }

                    val urlLabel = if (targetUrl == url) "primary" else "fallback"
                    logger.warn { "No Cloudflare cookies found on attempt $attempt/$MAX_BYPASS_RETRIES ($urlLabel) — challenge may have failed" }

                    // If the WebSocket connection died, refresh the WS URL for next attempt
                    refreshWsUrl()
                } catch (e: Exception) {
                    logger.error(e) { "Cloudflare bypass failed on attempt $attempt/$MAX_BYPASS_RETRIES" }
                    // Any exception during CDP interaction means Chrome is in an
                    // unknown state — reset for clean restart on next attempt.
                    chromeProcess?.destroyForcibly()
                    chromeProcess = null
                    chromeWsUrl = null
                    chromePort = -1
                }

                // Brief pause between retries
                if (attempt < MAX_BYPASS_RETRIES) {
                    try { Thread.sleep(2000L * attempt) } catch (_: Exception) {}
                }
            }

            logger.warn { "❌ Cloudflare bypass failed after $MAX_BYPASS_RETRIES attempts" }
            return emptyMap()
        }
    }

    /**
     * Gracefully shut down the persistent Chrome process.
     * Call this when the app exits to clean up system resources.
     *
     * Safe to call multiple times — checks if Chrome is alive first.
     */
    fun shutdown() {
        lock.withLock {
            val proc = chromeProcess ?: return
            if (proc.isAlive) {
                logger.info { "Shutting down persistent Chrome (PID ${proc.pid()})..." }
                proc.destroyForcibly()
                try { proc.waitFor(3, TimeUnit.SECONDS) } catch (_: Exception) {}
                logger.info { "Persistent Chrome shut down." }
            }
            chromeProcess = null
            chromePort = -1
            chromeWsUrl = null
            hasEverStartedChrome = false
        }
    }

    // ── Persistent Chrome lifecycle ────────────────────────────────────

    /**
     * Ensure the persistent Chrome instance is running and responsive.
     * If it's not running or has crashed, starts a new instance.
     *
     * @return true if Chrome is running and ready
     */
    private fun ensureChromeRunning(): Boolean {
        // Fast path: Chrome is alive and responding
        if (chromeProcess?.isAlive == true && chromePort > 0) {
            // Quick health check via /json/version
            if (isRunning) return true

            // Port is stale — Chrome may have crashed. Reset state.
            logger.warn { "Persistent Chrome health check failed — restarting..." }
            chromeProcess?.destroyForcibly()
            chromeProcess = null
            chromePort = -1
            chromeWsUrl = null
        }

        // Launch a new Chrome instance
        return try {
            cleanupStaleProfile()
            val (process, port) = launchChromeWithPort()
            chromeProcess = process
            chromePort = port

            // Get the WebSocket debugger URL
            val wsUrl = getDebuggerUrl(port)
            chromeWsUrl = wsUrl

            if (wsUrl == null) {
                logger.warn { "Failed to get Chrome DevTools URL after launch" }
                chromeProcess?.destroyForcibly()
                chromeProcess = null
                chromePort = -1
                return false
            }

            if (!hasEverStartedChrome) {
                logger.info { "🚀 Persistent Chrome started (PID ${process.pid()}, port $port)" }
                hasEverStartedChrome = true
            } else {
                logger.info { "♻️ Persistent Chrome restarted (PID ${process.pid()}, port $port)" }
            }

            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to launch persistent Chrome" }
            chromeProcess = null
            chromePort = -1
            chromeWsUrl = null
            false
        }
    }

    /**
     * Refresh the cached WebSocket URL from the running Chrome instance.
     * Called when a WebSocket connection fails — the debugger URL may have changed
     * (e.g., Chrome restarted a DevTools session), so we refetch it from /json/version.
     */
    private fun refreshWsUrl() {
        val port = chromePort
        if (port <= 0) return
        chromeWsUrl = getDebuggerUrl(port)
    }

    // ── Chrome process management ──────────────────────────────────────

    private fun chromeExecutable(): File {
        if (customChromePath.isNotBlank()) {
            val customFile = File(customChromePath)
            if (customFile.isFile) return customFile
        }
        val standardPath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        if (File(standardPath).isFile) return File(standardPath)
        val chromiumPath = "/opt/homebrew/bin/chromium"
        if (File(chromiumPath).isFile) return File(chromiumPath)
        val bravePath = "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
        if (File(bravePath).isFile) return File(bravePath)
        val edgePath = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
        if (File(edgePath).isFile) return File(edgePath)
        return File(standardPath)
    }

    /**
     * Clear anything that would stop a fresh Chrome launch from binding its
     * profile. A force-quit of the app (or a crash) leaves the persistent
     * Chrome instance + its Singleton* lock files behind; every subsequent
     * launch then fails to acquire the profile and never prints the DevTools
     * port, so `launchChromeWithPort` times out after 15s.
     *
     * Only runs when our own tracked process is dead, so a healthy instance
     * is never disturbed. Kills orphaned processes that still reference the
     * profile dir, then removes the stale lock files.
     */
    private fun cleanupStaleProfile() {
        if (chromeProcess?.isAlive == true) return
        try {
            val profileName = "anikku-chrome-cdp-profile"
            ProcessHandle.allProcesses().forEach { ph ->
                val cmd = ph.info().commandLine().orElse("")
                if (cmd.contains(profileName)) {
                    try { ph.destroyForcibly() } catch (_: Exception) {}
                }
            }
            val profileDir = File(System.getProperty("java.io.tmpdir"), profileName)
            profileDir.listFiles()
                ?.filter { it.name.startsWith("Singleton") }
                ?.forEach { runCatching { it.delete() } }
        } catch (_: Exception) {
            // Best-effort cleanup — the launch below may still work.
        }
    }

    /**
     * Launch Chrome with `--remote-debugging-port=0` and parse the assigned
     * port from stderr.
     */
    private fun launchChromeWithPort(): Pair<Process, Int> {
        // Use a persistent user-data-dir so session state (cookies, localStorage,
        // TLS cache) survives across navigation cycles within the same Chrome instance.
        val userDataDir = File(
            System.getProperty("java.io.tmpdir"),
            "anikku-chrome-cdp-profile"
        )
        userDataDir.mkdirs()

        val builder = ProcessBuilder(
            chromeExecutable().absolutePath,
            "--remote-debugging-port=0",
            "--headless=new",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--disable-extensions",
            "--disable-background-networking",
            "--disable-sync",
            "--no-sandbox",
            "--user-data-dir=${userDataDir.absolutePath}",
            "--disable-blink-features=AutomationControlled",
            "about:blank",
        )
        builder.environment()["DISPLAY"] = ""
        val process = builder.start()

        val portRef = AtomicInteger(-1)
        val stderrThread = Thread({
            try {
                val reader = process.errorStream.bufferedReader()
                var line: String? = reader.readLine()
                while (line != null && portRef.get() < 0) {
                    if (line.contains("DevTools listening on ws://")) {
                        Regex("""ws://127\.0\.0\.1:(\d+)""").find(line)
                            ?.groupValues?.get(1)?.toIntOrNull()?.let { portRef.set(it) }
                    }
                    line = reader.readLine()
                }
            } catch (_: Exception) {}
        }, "chrome-stderr-reader")
        stderrThread.isDaemon = true
        stderrThread.start()

        val startTime = System.currentTimeMillis()
        val timeoutMs = 15000L
        while (portRef.get() < 0 && System.currentTimeMillis() - startTime < timeoutMs) {
            Thread.sleep(100)
        }
        stderrThread.interrupt()

        val port = portRef.get()
        if (port < 0) {
            process.destroyForcibly()
            throw IllegalStateException("Failed to get Chrome DevTools port within ${timeoutMs}ms")
        }

        Thread.sleep(300)

        return Pair(process, port)
    }

    private fun getDebuggerUrl(port: Int): String? {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/json/version")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val obj = json.parseToJsonElement(body).jsonObject
            obj["webSocketDebuggerUrl"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            logger.error(e) { "Failed to get DevTools WebSocket URL on port $port" }
            null
        }
    }

    /**
     * Navigate to a URL via CDP WebSocket, wait for page load, extract cookies.
     *
     * Uses a multi-phase approach:
     * 1. Wait for Page.loadEventFired (initial page load complete)
     * 2. Schedule first cookie fetch after 1.5s
     * 3. If failed, wait up to [timeoutSeconds] polling Network.getCookies
     *    every 2s (handles slow challenges that need multiple attempts)
     */
    private fun navigateAndWait(
        wsUrl: String,
        targetUrl: String,
        timeoutSeconds: Long,
        userAgent: String,
        referer: String? = null,
    ): Map<String, String> {
        val latch = CountDownLatch(1)
        val cookies = ConcurrentHashMap<String, String>()
        var messageId = 0
        val pageLoaded = AtomicBoolean(false)
        val wsFailed = AtomicBoolean(false)

        val ws = httpClient.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    messageId++
                    val enablePage = """{"id":$messageId,"method":"Page.enable"}"""
                    logDebug(">>> $enablePage")
                    webSocket.send(enablePage)
                    messageId++
                    val enableNet = """{"id":$messageId,"method":"Network.enable"}"""
                    logDebug(">>> $enableNet")
                    webSocket.send(enableNet)
                    // User-Agent override
                    messageId++
                    val escapedUA = userAgent.replace("\\", "\\\\").replace("\"", "\\\"")
                    val setUA = """{"id":$messageId,"method":"Network.setUserAgentOverride","params":{"userAgent":"$escapedUA"}}"""
                    logDebug(">>> $setUA")
                    webSocket.send(setUA)
                    // Anti-detection scripts
                    val antiDetectJS = """
                        Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                        Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});
                    """.trimIndent().replace("\n", " ").replace("\"", "\\\"")
                    messageId++
                    val antiDetect = """{"id":$messageId,"method":"Page.addScriptToEvaluateOnNewDocument","params":{"source":"$antiDetectJS"}}"""
                    logDebug(">>> $antiDetect")
                    webSocket.send(antiDetect)
                    messageId++
                    val navigateParams = StringBuilder(""""url":"$targetUrl"""")
                    if (!referer.isNullOrBlank()) {
                        val escapedReferer = referer.replace("\\", "\\\\").replace("\"", "\\\"")
                        navigateParams.append(""","referrer":"$escapedReferer"""")
                    }
                    val navigate = """{"id":$messageId,"method":"Page.navigate","params":{$navigateParams}}"""
                    logDebug(">>> $navigate")
                    webSocket.send(navigate)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    logDebug("<<< ${text.take(500)}${if (text.length > 500) "..." else ""}")
                    try {
                        val msg = json.parseToJsonElement(text).jsonObject
                        val method = msg["method"]?.jsonPrimitive?.content

                        if (method == "Page.loadEventFired") {
                            pageLoaded.set(true)
                            scheduleCookieFetch(webSocket, targetUrl, delayMs = 1500)
                        }

                        val result = msg["result"]
                        val msgId = msg["id"]
                        if (result != null && msgId != null) {
                            val cookiesArray = result.jsonObject["cookies"]?.jsonArray
                            if (cookiesArray != null && cookiesArray.isNotEmpty()) {
                                var found = false
                                for (cookieElement in cookiesArray) {
                                    val cookie = cookieElement.jsonObject
                                    val name = cookie["name"]?.jsonPrimitive?.content ?: continue
                                    val value = cookie["value"]?.jsonPrimitive?.content ?: continue
                                    if (name in CLOUDFLARE_COOKIE_NAMES) {
                                        cookies[name] = value
                                        found = true
                                    }
                                }
                                if (found) {
                                    latch.countDown()
                                    webSocket.close(1000, "Cookies extracted")
                                }
                            }
                        }

                        if (method == "Page.frameStoppedLoading") {
                            scheduleCookieFetch(webSocket, targetUrl, delayMs = 1000)
                        }
                    } catch (e: Exception) {
                        logDebug("<<< [MALFORMED] ${text.take(500)} — ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logger.warn(t) { "CDP WebSocket connection failed" }
                    wsFailed.set(true)
                    latch.countDown()
                }
            },
        )

        // Wait for initial cookie fetch or timeout
        val success = latch.await(timeoutSeconds, TimeUnit.SECONDS)

        // If page loaded but no cookies yet, try polling
        if (cookies.isEmpty() && pageLoaded.get() && !wsFailed.get()) {
            logger.debug { "Page loaded but no CF cookies yet — polling for more time..." }
            val remainingMs = (timeoutSeconds * 1000) - (System.currentTimeMillis() % (timeoutSeconds * 1000))
            val maxPollMs = remainingMs.coerceAtMost(15000L)
            val startPoll = System.currentTimeMillis()

            while (cookies.isEmpty() && System.currentTimeMillis() - startPoll < maxPollMs && !wsFailed.get()) {
                val pollId = cookieFetchId.incrementAndGet()
                val pollMsg = """{"id":$pollId,"method":"Network.getCookies","params":{"urls":["$targetUrl"]}}"""
                logDebug(">>> $pollMsg")
                ws.send(pollMsg)
                try { Thread.sleep(2000) } catch (_: Exception) { break }
            }
        }

        if (!success && !wsFailed.get()) {
            logger.warn { "Timed out waiting for Cloudflare challenge after ${timeoutSeconds}s" }
        }

        // Close the WebSocket (don't close if it already failed)
        if (!wsFailed.get()) {
            ws.close(1000, "Done")
        }

        // If WebSocket failed, signal the caller to refresh WS URL
        if (wsFailed.get()) {
            throw WebSocketOrChromeFailure("CDP WebSocket connection lost")
        }

        return cookies.toMap()
    }

    /**
     * Check if a URL looks like a navigable page (HTML) vs a direct resource.
     */
    private fun isPageUrl(url: String): Boolean {
        val path = url.substringAfter("://").substringAfter("/").substringBefore("?").substringBefore("#")
        if (path.isEmpty() || path == "/") return true
        val lowerPath = path.lowercase()
        val resourcePathPatterns = listOf(
            "/stream/", "/api/", "/video/", "/hls/", "/manifest/",
            "/ajax/", "/get/", "/source/", "/playlist/",
        )
        if (resourcePathPatterns.any { lowerPath.contains(it) }) return false
        val extension = path.substringAfterLast(".")
        val nonPageExtensions = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "flv", "ts",
            "m3u8", "m3u", "mpd",
            "jpg", "jpeg", "png", "gif", "webp",
            "zip", "rar", "7z",
            "pdf", "doc", "docx",
            "json", "xml",
        )
        return extension.lowercase() !in nonPageExtensions
    }

    /**
     * Extract the origin (scheme + host) from a URL.
     */
    private fun extractOrigin(url: String): String {
        val match = Regex("""^(https?://[^/]+)""").find(url)
        return match?.groupValues?.get(1) ?: url
    }

    /**
     * Schedule a [Network.getCookies] request after a delay.
     */
    private fun scheduleCookieFetch(
        webSocket: WebSocket,
        targetUrl: String,
        delayMs: Long,
    ) {
        val thread = Thread({
            Thread.sleep(delayMs)
            val id = cookieFetchId.incrementAndGet()
            val fetchMsg = """{"id":$id,"method":"Network.getCookies","params":{"urls":["$targetUrl"]}}"""
            logDebug(">>> $fetchMsg")
            webSocket.send(fetchMsg)
        }, "chrome-cdp-cookie-fetch")
        thread.isDaemon = true
        thread.start()
    }

    /** Monotonically increasing message ID for scheduled cookie fetch requests. */
    private val cookieFetchId = AtomicInteger(1000)

    /**
     * Log a debug message only when [debugMode] is enabled.
     */
    private fun logDebug(message: String) {
        if (debugMode) {
            val entry = "[${Instant.now()}] $message"
            logger.info { "[CDP-DEBUG] $entry" }
            debugLog.add(entry)
            while (debugLog.size > MAX_DEBUG_MESSAGES) {
                debugLog.poll()
            }
        }
    }

    /**
     * Exception thrown when the CDP WebSocket connection fails.
     * Signals the caller to refresh the WS URL and potentially restart Chrome.
     */
    private class WebSocketOrChromeFailure(message: String) : Exception(message)

    private val CLOUDFLARE_COOKIE_NAMES = setOf(
        "cf_clearance",
        "__cf_bm",
        "cf_chl_2",
        "cf_chl_3",
        "cf_chl_rc_ni",
        "cf_chl_rc_m",
        "cf_chl_seq",
        "cf_chl_prog",
        "cf_chl_cc",
        "cf_ob_info",
        "cf_use_ob",
        "__cflb",
        "__cfruid",
        "__cfwaitingroom",
        "dd_testcookie",
        "ak_bmsc",
        "bm_sz",
        "_abck",
        "reese84",
        "incap_ses",
        "visid_incap",
    )
}

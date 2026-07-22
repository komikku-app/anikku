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

private val logger = KotlinLogging.logger {}

/**
 * Cloudflare bypass via Chrome DevTools Protocol (CDP).
 *
 * Launches the user's installed Chrome in headless mode, navigates to a
 * Cloudflare-protected URL, waits for the JavaScript challenge to resolve,
 * and extracts the `cf_clearance` and `__cf_bm` cookies.
 *
 * ## Why CDP instead of Selenium/Playwright/FlareSolverr
 *
 * - **Zero new dependencies** — uses the user's installed Chrome + OkHttp WebSocket
 * - **Lightweight** — no 100MB+ browser bundles, no Python, no Docker
 * - **Reliable** — real Chrome with native TLS + JS = passes all Cloudflare checks
 * - **Fast after first use** — cookies cached per domain in MacOSCookieJar
 *
 * ## Thread safety
 *
 * Only one bypass attempt runs at a time (synchronized on the object).
 * Concurrent bypasses from multiple extensions queue up — only the first
 * actually launches Chrome, subsequent ones wait and reuse cached cookies.
 */
object ChromeCDPClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Custom Chrome/Chromium executable path. Set before calling [fetchCloudflareCookies].
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
     * Fetch Cloudflare bypass cookies for a URL.
     * Thread-safe — only one bypass runs at a time.
     *
     * @param url The Cloudflare-protected URL to visit
     * @param userAgent The User-Agent to use
     * @param timeoutSeconds Max time to wait for challenge resolution
     * @return Map of cookie name → value, or empty map on failure
     */
    /**
     * Maximum number of retry attempts for a single Cloudflare bypass.
     * Some sites return intermediate challenge pages that require multiple
     * navigation cycles to resolve (e.g., Turnstile, reCAPTCHA overlay).
     */
    private const val MAX_BYPASS_RETRIES = 3

    @Synchronized
    fun fetchCloudflareCookies(
        url: String,
        userAgent: String,
        timeoutSeconds: Long = 30,
    ): Map<String, String> {
        if (!isChromeInstalled) {
            logger.warn { "Chrome not found — Cloudflare bypass unavailable" }
            return emptyMap()
        }

        // Try up to MAX_BYPASS_RETRIES times with fresh Chrome instances
        for (attempt in 1..MAX_BYPASS_RETRIES) {
            var process: Process? = null
            try {
                // Launch Chrome with remote debugging, parse port from stderr
                val (chromeProcess, debugPort) = launchChromeWithPort()
                process = chromeProcess

                // Get the WebSocket debugger URL
                val wsUrl = getDebuggerUrl(debugPort)
                if (wsUrl == null) {
                    logger.warn { "Failed to get Chrome DevTools URL on port $debugPort (attempt $attempt/$MAX_BYPASS_RETRIES)" }
                    continue
                }

                // Navigate to URL and wait for Cloudflare challenge to resolve
                val cookies = navigateAndWait(wsUrl, url, timeoutSeconds, userAgent)
                if (cookies.isNotEmpty()) {
                    logger.info { "✅ Cloudflare bypass succeeded on attempt $attempt/$MAX_BYPASS_RETRIES — got ${cookies.size} cookie(s)" }
                    return cookies
                }

                logger.warn { "No Cloudflare cookies found on attempt $attempt/$MAX_BYPASS_RETRIES — challenge may have failed" }
            } catch (e: Exception) {
                logger.error(e) { "Cloudflare bypass failed on attempt $attempt/$MAX_BYPASS_RETRIES" }
            } finally {
                process?.let {
                    it.destroyForcibly()
                    try { it.waitFor(2, TimeUnit.SECONDS) } catch (_: Exception) {}
                }
            }

            // Brief pause between retries
            if (attempt < MAX_BYPASS_RETRIES) {
                try { Thread.sleep(1500L * attempt) } catch (_: Exception) {}
            }
        }

        logger.warn { "❌ Cloudflare bypass failed after $MAX_BYPASS_RETRIES attempts" }
        return emptyMap()
    }

    // ── Internal ──────────────────────────────────────────────────────

    private fun chromeExecutable(): File {
        // Custom path takes priority
        if (customChromePath.isNotBlank()) {
            val customFile = File(customChromePath)
            if (customFile.isFile) return customFile
        }
        // Standard macOS Chrome location
        val standardPath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        if (File(standardPath).isFile) return File(standardPath)
        // Chromium via Homebrew
        val chromiumPath = "/opt/homebrew/bin/chromium"
        if (File(chromiumPath).isFile) return File(chromiumPath)
        // Brave
        val bravePath = "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
        if (File(bravePath).isFile) return File(bravePath)
        // Microsoft Edge
        val edgePath = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
        if (File(edgePath).isFile) return File(edgePath)
        // Fallback
        return File(standardPath)
    }

    /**
     * Launch Chrome with `--remote-debugging-port=0` and parse the assigned
     * port from stderr. Returns a single Chrome instance — no double launch.
     */
    private fun launchChromeWithPort(): Pair<Process, Int> {
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
            "--disable-blink-features=AutomationControlled",
            "about:blank",
        )
        builder.environment()["DISPLAY"] = ""
        val process = builder.start()

        // Parse port from stderr: "DevTools listening on ws://127.0.0.1:PORT/devtools/browser/HASH"
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

        // Wait for the port with a timeout
        val startTime = System.currentTimeMillis()
        val timeoutMs = 10000L
        while (portRef.get() < 0 && System.currentTimeMillis() - startTime < timeoutMs) {
            Thread.sleep(100)
        }
        stderrThread.interrupt()

        val port = portRef.get()
        if (port < 0) {
            process.destroyForcibly()
            throw IllegalStateException("Failed to get Chrome DevTools port within ${timeoutMs}ms")
        }

        // Give Chrome a moment to finish initializing
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
     * Navigate to a URL via CDP WebSocket, wait for page load (Cloudflare
     * JS challenge included), then extract cookies via Network.getCookies.
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
    ): Map<String, String> {
        val latch = CountDownLatch(1)
        val cookies = ConcurrentHashMap<String, String>()
        var messageId = 0
        val pageLoaded = AtomicBoolean(false)

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
                    // User-Agent override: Chrome CDP uses its own headless UA by default
                    // which Cloudflare detects. Set the real desktop UA the extension uses.
                    messageId++
                    val escapedUA = userAgent.replace("\\", "\\\\").replace("\"", "\\\"")
                    val setUA = """{"id":$messageId,"method":"Network.setUserAgentOverride","params":{"userAgent":"$escapedUA"}}"""
                    logDebug(">>> $setUA")
                    webSocket.send(setUA)
                    // Hide navigator.webdriver flag — Cloudflare checks this to detect automation
                    messageId++
                    val hideWd = """{"id":$messageId,"method":"Page.addScriptToEvaluateOnNewDocument","params":{"source":"Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"}}"""
                    logDebug(">>> $hideWd")
                    webSocket.send(hideWd)
                    messageId++
                    val navigate = """{"id":$messageId,"method":"Page.navigate","params":{"url":"$targetUrl"}}"""
                    logDebug(">>> $navigate")
                    webSocket.send(navigate)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Log truncated incoming CDP message for debug
                    logDebug("<<< ${text.take(500)}${if (text.length > 500) "..." else ""}")
                    try {
                        val msg = json.parseToJsonElement(text).jsonObject
                        val method = msg["method"]?.jsonPrimitive?.content

                        // Page fully loaded → Cloudflare JS challenge resolved.
                        if (method == "Page.loadEventFired") {
                            pageLoaded.set(true)
                            // First cookie fetch: wait 1.5s for post-load CF JS
                            scheduleCookieFetch(webSocket, targetUrl, delayMs = 1500)
                        }

                        // Process Network.getCookies response
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

                        // Page load error or stopped — still try to get cookies
                        if (method == "Page.frameStoppedLoading") {
                            scheduleCookieFetch(webSocket, targetUrl, delayMs = 1000)
                        }
                    } catch (e: Exception) {
                        // Malformed CDP message — in debug mode, log it so the user can diagnose
                        logDebug("<<< [MALFORMED] ${text.take(500)} — ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logger.warn(t) { "CDP WebSocket connection failed" }
                    latch.countDown()
                }
            },
        )

        // Wait for initial cookie fetch or timeout
        val success = latch.await(timeoutSeconds, TimeUnit.SECONDS)

        // If page loaded but no cookies yet, try a few more polling rounds
        if (cookies.isEmpty() && pageLoaded.get()) {
            logger.debug { "Page loaded but no CF cookies yet — polling for more time..." }
            val pollLatch = CountDownLatch(1)
            val remainingMs = (timeoutSeconds * 1000) - (System.currentTimeMillis() % (timeoutSeconds * 1000))
            val maxPollMs = remainingMs.coerceAtMost(15000L)
            val startPoll = System.currentTimeMillis()

            while (cookies.isEmpty() && System.currentTimeMillis() - startPoll < maxPollMs) {
                val pollId = cookieFetchId.incrementAndGet()
                val pollMsg = """{"id":$pollId,"method":"Network.getCookies","params":{"urls":["$targetUrl"]}}"""
                logDebug(">>> $pollMsg")
                ws.send(pollMsg)
                try { Thread.sleep(2000) } catch (_: Exception) { break }
            }
        }

        if (!success) {
            logger.warn { "Timed out waiting for Cloudflare challenge after ${timeoutSeconds}s" }
            ws.close(1000, "Timeout")
        }

        return cookies.toMap()
    }

    /**
     * Schedule a [Network.getCookies] request after a delay, without blocking
     * the WebSocket dispatch thread.
     */
    private fun scheduleCookieFetch(
        webSocket: WebSocket,
        targetUrl: String,
        delayMs: Long,
    ) {
        // Use an incremental ID tracked outside the WebSocketListener
        // to avoid closure issues with the local "messageId" variable.
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
     * Uses INFO level so these messages appear in the default log configuration.
     * Also captures the message in an in-memory ring buffer for file export.
     */
    private fun logDebug(message: String) {
        if (debugMode) {
            val entry = "[${Instant.now()}] $message"
            logger.info { "[CDP-DEBUG] $entry" }
            debugLog.add(entry)
            // Prune ring buffer if over capacity
            while (debugLog.size > MAX_DEBUG_MESSAGES) {
                debugLog.poll()
            }
        }
    }

    private val CLOUDFLARE_COOKIE_NAMES = setOf(
        "cf_clearance",
        "__cf_bm",
        "cf_chl_2",
        "cf_chl_3",
        "cf_chl_rc_ni",
        "cf_chl_rc_m",
        "cf_chl_seq",                    // Cloudflare challenge sequence
        "cf_chl_prog",                   // Cloudflare challenge progress
        "cf_chl_cc",                     // Cloudflare challenge captcha
        "cf_ob_info",                    // Cloudflare observability
        "cf_use_ob",                     // Cloudflare observability flag
        "__cflb",                        // Cloudflare load balancer
        "__cfruid",                      // Cloudflare rate-limiting UID
        "__cfwaitingroom",               // Cloudflare Waiting Room
        // Non-Cloudflare WAF cookies (Chrome CDP can solve these too)
        "dd_testcookie",                 // DataDome test
        "ak_bmsc",                       // Akamai bot manager
        "bm_sz",                         // Akamai bot manager session
        "_abck",                         // Akamai sensor data
        "reese84",                       // Imperva/Incapsula
        "incap_ses",                     // Imperva session
        "visid_incap",                   // Imperva visitor ID
    )
}

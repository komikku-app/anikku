package app.anikku.macos.platform.extension

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.koin.core.context.stopKoin
import java.util.concurrent.TimeUnit
import app.anikku.macos.platform.network.CloudflareInterceptor
import app.anikku.macos.platform.network.CloudflareInterceptor.DiagnosticEntry
import app.anikku.macos.platform.network.DiagnosticLoggingInterceptor
import app.anikku.macos.platform.network.HttpRetryInterceptor
import app.anikku.macos.platform.network.MacOSCookieJar
import app.anikku.macos.platform.network.FallbackDns
import app.anikku.macos.platform.network.UserAgentInterceptor
import okhttp3.brotli.BrotliInterceptor
import org.koin.core.context.startKoin
import org.koin.dsl.module
import tachiyomi.core.common.preference.PreferenceStore
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.time.Instant

/**
 * Comprehensive extension compatibility test.
 *
 * Loads ALL 45 extension JARs and tests each one across four stages:
 * - **Load**: Can the JAR be loaded and trusted?
 * - **Browse**: Does getPopularAnime return actual results?
 * - **Episodes**: Does getEpisodeList return episodes for the first popular anime?
 * - **Video**: Does getVideoList return playable video URLs for the first episode?
 *
 * Results are printed to stdout in a table format AND saved to
 * /tmp/anikku_extension_report.html for easy viewing.
 *
 * Stage code:
 *   ✅ = PASS (returned data)
 *   ⏱  = Timeout (request > 20s)
 *   ❌ = FAIL (error or empty result)
 *   ⚠  = SKIP (missing extension JAR or unsupported API)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class ExtensionCompatibilityTest {

    companion object {
        private const val EXTENSIONS_DIR = "Library/Application Support/Anikku/extensions"
        private const val TIMEOUT_MS = 30_000L
        private const val REPORT_PATH = "/tmp/anikku_extension_report.html"

        private fun ensureUrl(sAnime: SAnime, fallbackUrl: String) {
            try {
                if (sAnime.url.isBlank()) sAnime.url = fallbackUrl
            } catch (_: UninitializedPropertyAccessException) {
                sAnime.url = fallbackUrl
            }
        }

        private fun ensureEpisodeUrl(episode: SEpisode, fallbackUrl: String) {
            try {
                if (episode.url.isBlank()) episode.url = fallbackUrl
            } catch (_: UninitializedPropertyAccessException) {
                episode.url = fallbackUrl
            }
        }

        private fun safeUrl(anime: SAnime): String = try { anime.url } catch (_: Exception) { "" }
        private fun safeTitle(anime: SAnime): String = try { anime.title } catch (_: Exception) { "" }
        private fun safeName(episode: SEpisode): String = try { episode.name } catch (_: Exception) { "" }
    }

    private data class ExtensionResult(
        val name: String,
        val pkgName: String,
        val loadStatus: String,
        val browseStatus: String = "—",
        val browseCount: Int = 0,
        val episodeStatus: String = "—",
        val episodeCount: Int = 0,
        val videoStatus: String = "—",
        val videoCount: Int = 0,
        val firstAnimeTitle: String = "",
        val firstEpisodeName: String = "",
        val videoQualities: String = "",
        val videoSampleUrl: String = "",
        val errorDetail: String = "",
        /** CDP/WAF diagnostic summary (e.g. "CF:3 ✅1" = 3 blocks, 1 bypassed) */
        val wafStatus: String = "—",
        /** Detailed CDP diagnostic entries for this extension */
        val cfDiagnostics: List<DiagnosticEntry> = emptyList(),
    )

    private val results = mutableListOf<ExtensionResult>()
    private val extensionsDir = File(System.getProperty("user.home"), EXTENSIONS_DIR)
    private var startTime: Long = 0

    @BeforeAll
    fun setup() {
        startTime = System.currentTimeMillis()
        Assertions.assertTrue(extensionsDir.isDirectory, "Extensions directory not found at ${extensionsDir.absolutePath}")

        // Initialize Koin once before all tests
        try { stopKoin() } catch (_: Throwable) {}
        val testPrefsFile = File.createTempFile("anikku-test-prefs", ".json")
        testPrefsFile.deleteOnExit()

        // Temp cookie file — shared cookie store between OkHttp and CloudflareInterceptor.
        // This is how headless Chrome extracts cf_clearance cookies and injects them
        // into the OkHttp client for subsequent extension requests.
        val cookieFile = File.createTempFile("anikku-test-cookies", ".json")
        cookieFile.deleteOnExit()
        val cookieJar = MacOSCookieJar(cookieFile)

        startKoin {
            modules(module {
                single<Json> { Json { ignoreUnknownKeys = true } }
                single<android.app.Application> { android.app.Application() }
                single<android.content.Context> { android.app.Application() }
                single<PreferenceStore> {
                    app.anikku.macos.platform.preference.MacOSPreferenceStore(testPrefsFile)
                }
                // Full Cloudflare bypass pipeline — matches the production
                // NetworkHelper in PlatformModule.kt. This ensures extension
                // HTTP requests get:
                //   - Chrome-like User-Agent (reduces challenge rate)
                //   - HTTP retry with exponential backoff (3 retries)
                //   - Cloudflare bypass via headless Chrome (solves JS challenges)
                //   - Diagnostic logging (non-2xx responses)
                //   - Brotli decompression (brotli-encoded responses)
                //   - DNS-over-HTTPS fallback (when system DNS fails)
                single {
                    eu.kanade.tachiyomi.network.NetworkHelper(
                        preferences = eu.kanade.tachiyomi.network.NetworkPreferences(get<PreferenceStore>()),
                        isDebugBuild = true,
                        cookieJar = cookieJar,
                        dns = FallbackDns,
                        extraInterceptors = listOf(
                            UserAgentInterceptor {
                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                            },
                            HttpRetryInterceptor(maxRetries = 3, baseDelayMs = 1000),
                            BrotliInterceptor,
                            CloudflareInterceptor(cookieJar) {
                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                            },
                            DiagnosticLoggingInterceptor(isDebugBuild = true),
                        ),
                    )
                }
                single { exh.pref.DelegateSourcePreferences(get<PreferenceStore>()) }
            })
        }

        // Install lenient SSL for extension requests
        app.anikku.macos.platform.network.InsecureSSLHelper.install()

        // Enable CDP debug mode to capture detailed WebSocket traffic for diagnostics
        app.anikku.macos.platform.network.ChromeCDPClient.debugMode = true

        println("  🌐 Cloudflare bypass: ${if (app.anikku.macos.platform.network.ChromeCDPClient.isChromeInstalled) "✅ Chrome detected" else "⚠ Chrome not found — bypass disabled"}")
        println("  🔍 CDP debug mode: ${if (app.anikku.macos.platform.network.ChromeCDPClient.debugMode) "ON" else "OFF"}")
        println()
    }

    @AfterAll
    fun tearDown() {
        MacOSExtensionLoader.closeAll()
        stopKoin()
        generateReport()
    }

    @Test
    fun `test all 45 extensions`() = runBlocking {
        val jarFiles = extensionsDir.listFiles()
            ?.filter { it.extension == "jar" }
            ?.sortedBy { it.name }
            ?: emptyList()

        println("=" .repeat(120))
        println("  🧪 EXTENSION COMPATIBILITY TEST — ${Instant.now()}")
        println("  📁 Testing ${jarFiles.size} extension(s) from ${extensionsDir.absolutePath}")
        println("  ⏱  Each stage has ${TIMEOUT_MS / 1000}s timeout")
        println("=" .repeat(120))
        println()

        var totalProcessed = 0
        var totalLoaded = 0
        var totalBrowsed = 0
        var totalEpisodes = 0
        var totalVideos = 0

        for (jarFile in jarFiles) {
            totalProcessed++
            val result = testExtension(jarFile)
            results.add(result)

            if (result.loadStatus == "✅") totalLoaded++
            if (result.browseStatus == "✅") totalBrowsed++
            if (result.episodeStatus == "✅") totalEpisodes++
            if (result.videoStatus == "✅") totalVideos++

            // Summary line for each extension
            val line = "  [%s] %-30s | Browse: %-2s %-3d | Episodes: %-2s %-3d | Video: %-2s %-3d | %s".format(
                result.loadStatus,
                result.name.take(30),
                result.browseStatus,
                result.browseCount,
                result.episodeStatus,
                result.episodeCount,
                result.videoStatus,
                result.videoCount,
                result.firstAnimeTitle.take(30)
            )
            println(line)
        }

        // Summary
        val elapsed = System.currentTimeMillis() - startTime
        println()
        println("=" .repeat(120))
        println("  📊 SUMMARY")
        println("  ────────────────────────────────────────────────────────────────")
        println("  Total extensions: $totalProcessed")
        println("  ✅ Loaded:         $totalLoaded")
        println("  ✅ Browse (popular): $totalBrowsed")
        println("  ✅ Episodes:       $totalEpisodes")
        println("  ✅ Video URLs:     $totalVideos")
        println("  ⏱  Elapsed: ${elapsed / 1000}s")
        println("=" .repeat(120))
        println()
        println("  📄 HTML report saved to: $REPORT_PATH")
        println()

        // Final assertion: at least one extension should work end-to-end
        Assertions.assertTrue(totalBrowsed > 0,
            "At least one extension should return browse results. " +
            "If all failed, check network connectivity and Cloudflare bypass settings.")
    }

    /**
     * Test a single extension across all four stages.
     */
    private fun testExtension(jarFile: File): ExtensionResult {
        val metadata = MacOSExtensionLoader.readMetadata(jarFile)
        val name = metadata?.name ?: jarFile.nameWithoutExtension
        val pkgName = metadata?.pkgName ?: jarFile.nameWithoutExtension

        // Clear CDP diagnostics at start to avoid cross-extension contamination
        CloudflareInterceptor.clearDiagnostics()

        // Stage 1: Load
        val loadResult = loadExtension(jarFile, pkgName)
        if (loadResult == null) {
            return ExtensionResult(
                name = name,
                pkgName = pkgName,
                loadStatus = "❌",
                errorDetail = "Failed to load extension (not a JAR with source classes)"
            )
        }

        val source = loadResult.source
        val loadOk = loadResult.loadStatus == "✅"

        // Stages 2-4 require a loaded source
        if (!loadOk || source == null) {
            return ExtensionResult(
                name = name,
                pkgName = pkgName,
                loadStatus = "❌",
                errorDetail = loadResult.errorDetail
            )
        }

        // Stage 2: Browse (getPopularAnime)
        val browseResult = testBrowse(source, pkgName)

        // Stage 3: Episodes (getEpisodeList)
        val episodeResult = if (browseResult.anime != null) {
            testEpisodes(source, browseResult.anime!!, pkgName)
        } else {
            EpisodeResult("⚠", 0, null, "")
        }

        // Stage 4: Video (getVideoList)
        val videoResult = if (episodeResult.episode != null) {
            testVideoList(source, episodeResult.episode!!, pkgName)
        } else {
            VideoResult("⚠", 0, "", "")
        }

        // Close the classloader to prevent resource leaks across 45 iterations
        MacOSExtensionLoader.closeClassLoader(pkgName)

        // Capture CDP diagnostics after ALL stages (browse, episodes, video)
        val cfDiags = CloudflareInterceptor.getDiagnostics()
        val wafStatus = if (cfDiags.isNotEmpty()) CloudflareInterceptor.diagnosticSummary() else "—"

        return ExtensionResult(
            name = name,
            pkgName = pkgName,
            loadStatus = "✅",
            browseStatus = browseResult.status,
            browseCount = browseResult.count,
            episodeStatus = episodeResult.status,
            episodeCount = episodeResult.count,
            videoStatus = videoResult.status,
            videoCount = videoResult.count,
            firstAnimeTitle = browseResult.firstTitle,
            firstEpisodeName = episodeResult.firstName,
            videoQualities = videoResult.qualities,
            videoSampleUrl = videoResult.sampleUrl,
            wafStatus = wafStatus,
            cfDiagnostics = cfDiags,
        )
    }

    // ── Stage 1: Load ─────────────────────────────────────────────────────

    private data class LoadResultData(
        val source: CatalogueSource?,
        val loadStatus: String,
        val errorDetail: String = "",
    )

    private fun loadExtension(jarFile: File, pkgName: String): LoadResultData {
        return try {
            val hash = MacOSExtensionLoader.computeSha256(jarFile)
            val metadata = MacOSExtensionLoader.readMetadata(jarFile)
                ?: return LoadResultData(null, "❌", "No metadata")

            val trustEntry = MacOSExtensionLoader.TrustEntry(
                pkgName = pkgName,
                versionCode = metadata.versionCode,
                signatureHash = hash,
            )

            val result = MacOSExtensionLoader.loadExtension(
                jarFile = jarFile,
                libsDir = extensionsDir.parentFile,
                trustStore = mapOf(pkgName to listOf(trustEntry)),
            )

            when (result) {
                is LoadResult.Success -> {
                    val sources = result.extension.sources.filterIsInstance<CatalogueSource>()
                    if (sources.isEmpty()) {
                        LoadResultData(null, "⚠", "Loaded but no CatalogueSource found")
                    } else {
                        LoadResultData(sources.first(), "✅")
                    }
                }
                is LoadResult.Untrusted -> {
                    LoadResultData(null, "❌", "Untrusted extension")
                }
                is LoadResult.Error -> {
                    LoadResultData(null, "❌", "Load error")
                }
            }
        } catch (e: Exception) {
            LoadResultData(null, "❌", "${e::class.simpleName}: ${e.message?.take(100)}")
        }
    }

    // ── Stage 2: Browse ───────────────────────────────────────────────────

    private data class BrowseResult(
        val status: String,
        val count: Int,
        val anime: SAnime?,
        val firstTitle: String,
    )

    private fun testBrowse(source: CatalogueSource, pkgName: String): BrowseResult {
        return try {
            val page = runBlocking {
                withContext(Dispatchers.IO) {
                    withTimeout(TIMEOUT_MS) {
                        source.getPopularAnime(page = 1)
                    }
                }
            }

            val validAnime = page.animes.filter { a ->
                safeTitle(a).isNotBlank()
            }

            if (validAnime.isEmpty()) {
                BrowseResult("⚠", 0, null, "")
            } else {
                val first = validAnime.first()
                BrowseResult("✅", validAnime.size, first, safeTitle(first).take(40))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            BrowseResult("⏱", 0, null, "Timeout")
        } catch (e: Exception) {
            BrowseResult("❌", 0, null, "${e::class.simpleName}")
        }
    }

    // ── Stage 3: Episodes ─────────────────────────────────────────────────

    private data class EpisodeResult(
        val status: String,
        val count: Int,
        val episode: SEpisode?,
        val firstName: String,
    )

    private fun testEpisodes(source: CatalogueSource, anime: SAnime, pkgName: String): EpisodeResult {
        return try {
            val details = runBlocking {
                withContext(Dispatchers.IO) {
                    withTimeout(TIMEOUT_MS) {
                        source.getAnimeDetails(anime)
                    }
                }
            }
            ensureUrl(details, safeUrl(anime))

            val episodes = runBlocking {
                withContext(Dispatchers.IO) {
                    withTimeout(TIMEOUT_MS) {
                        source.getEpisodeList(details)
                    }
                }
            }

            val validEpisodes = episodes.filter { e ->
                try { e.url.isNotBlank() } catch (_: Exception) { false }
            }

            if (validEpisodes.isEmpty()) {
                EpisodeResult("⚠", 0, null, "")
            } else {
                val first = validEpisodes.first()
                EpisodeResult("✅", validEpisodes.size, first, safeName(first).take(30))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            EpisodeResult("⏱", 0, null, "Timeout")
        } catch (e: Exception) {
            EpisodeResult("❌", 0, null, "${e::class.simpleName}")
        }
    }

    // ── Stage 4: Video ────────────────────────────────────────────────────

    private data class VideoResult(
        val status: String,
        val count: Int,
        val qualities: String,
        val sampleUrl: String,
    )

    private fun testVideoList(source: CatalogueSource, episode: SEpisode, pkgName: String): VideoResult {
        return try {
            ensureEpisodeUrl(episode, "/episode/1")

            val videos: List<Video> = runBlocking {
                withContext(Dispatchers.IO) {
                    withTimeout(TIMEOUT_MS) {
                        source.getVideoList(episode)
                    }
                }
            }

            if (videos.isEmpty()) {
                VideoResult("⚠", 0, "", "")
            } else {
                val qualities = videos.mapNotNull { v ->
                    try { v.videoTitle } catch (_: Exception) { null }
                }.filter { it.isNotBlank() }.take(5).joinToString(", ")
                val sampleUrl = try {
                    videos.first().videoUrl?.take(60) ?: ""
                } catch (_: Exception) { "" }
                VideoResult("✅", videos.size, qualities, sampleUrl)
            }
        } catch (e: NoSuchMethodError) {
            VideoResult("⚠", 0, "NoSuchMethod", "")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            VideoResult("⏱", 0, "", "Timeout")
        } catch (e: Exception) {
            VideoResult("❌", 0, "", "${e::class.simpleName}")
        }
    }

    // ── Report Generator ──────────────────────────────────────────────────

    private fun generateReport() {
        val elapsed = System.currentTimeMillis() - startTime
        val total = results.size
        val loaded = results.count { it.loadStatus == "✅" }
        val browsed = results.count { it.browseStatus == "✅" }
        val episodes = results.count { it.episodeStatus == "✅" }
        val videos = results.count { it.videoStatus == "✅" }

        val html = buildString {
            appendLine("""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Anikku Extension Compatibility Report</title>
<style>
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; background: #1a1a2e; color: #e0e0e0; }
h1 { color: #e94560; }
h2 { color: #0f3460; margin-top: 30px; }
table { border-collapse: collapse; width: 100%; margin-top: 16px; font-size: 13px; }
th { background: #16213e; color: #e94560; padding: 10px 8px; text-align: left; font-weight: 600; }
td { padding: 6px 8px; border-bottom: 1px solid #0f3460; max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
tr:hover { background: #16213e; }
.pass { color: #4ecca3; font-weight: bold; }
.fail { color: #e94560; font-weight: bold; }
.warn { color: #ffc107; font-weight: bold; }
.timeout { color: #ff9800; font-weight: bold; }
.summary { background: #16213e; padding: 16px; border-radius: 8px; margin: 16px 0; }
.summary td { border: none; }
.bar { height: 20px; border-radius: 4px; margin: 2px 0; }
.bar-container { background: #0f3460; border-radius: 4px; margin: 4px 0; }
</style>
</head>
<body>
<h1>🧪 Anikku Extension Compatibility Report</h1>
<p>Generated: ${Instant.now()} | Elapsed: ${elapsed / 1000}s | Tested: $total extensions</p>

<div class="summary">
<table>
<tr><td><strong>✅ Loaded:</strong></td><td>$loaded / $total</td><td><strong>✅ Browse:</strong></td><td>$browsed / $total</td></tr>
<tr><td><strong>✅ Episodes:</strong></td><td>$episodes / $total</td><td><strong>✅ Video URLs:</strong></td><td>$videos / $total</td></tr>
</table>
</div>

<div class="bar-container" style="display:flex; height:24px;">
<div class="bar" style="width:${if (total > 0) loaded * 100 / total else 0}%; background:#4ecca3;" title="Loaded: $loaded"></div>
<div class="bar" style="width:${if (total > 0) (browsed - loaded).coerceAtLeast(0) * 100 / total else 0}%; background:#ffc107;" title="Browse errors"></div>
<div class="bar" style="width:${if (total > 0) (total - browsed.coerceAtLeast(loaded)).coerceAtLeast(0) * 100 / total else 0}%; background:#e94560;" title="Failed: ${total - browsed}"></div>
</div>

<table>
<tr>
  <th>#</th>
  <th>Extension</th>
  <th>Load</th>
  <th>Browse</th>
  <th>#</th>
  <th>Episodes</th>
  <th>#</th>
  <th>Video</th>
  <th>#</th>
  <th>WAF</th>
  <th>First Anime</th>
  <th>Qualities</th>
</tr>""")

            results.forEachIndexed { i, r ->
                val rowClass = when (r.browseStatus) {
                    "✅" -> "pass"
                    "⏱" -> "timeout"
                    "❌" -> "fail"
                    else -> "warn"
                }
                appendLine("""
<tr>
  <td>${i + 1}</td>
  <td title="${r.pkgName}">${r.name}</td>
  <td class="${if (r.loadStatus == "✅") "pass" else "fail"}">${r.loadStatus}</td>
  <td class="$rowClass">${r.browseStatus}</td>
  <td>${r.browseCount}</td>
  <td class="${if (r.episodeStatus == "✅") "pass" else if (r.episodeStatus == "⚠") "warn" else if (r.episodeStatus == "⏱") "timeout" else "fail"}">${r.episodeStatus}</td>
  <td>${r.episodeCount}</td>
  <td class="${if (r.videoStatus == "✅") "pass" else if (r.videoStatus == "⚠") "warn" else if (r.videoStatus == "⏱") "timeout" else "fail"}">${r.videoStatus}</td>
  <td>${r.videoCount}</td>
  <td title="${r.cfDiagnostics.joinToString(", ") { "${it.host}:${it.httpCode} block=${it.wasBlockDetected} bypass=${it.bypassSucceeded}" }.take(200)}">${r.wafStatus}</td>
  <td title="${r.firstAnimeTitle}">${r.firstAnimeTitle.take(25)}</td>
  <td title="${r.videoSampleUrl}">${r.videoQualities.take(30)}</td>
</tr>""")
            }

            appendLine("""</table>

<h2>Working End-to-End Sources (Browse + Episodes + Video)</h2>
<ul>""")
            val working = results.filter { it.browseStatus == "✅" && it.episodeStatus == "✅" && it.videoStatus == "✅" }
            if (working.isEmpty()) {
                appendLine("  <li>None — no source passed all three stages</li>")
            } else {
                working.forEach { w ->
                    appendLine("  <li><strong>${w.name}</strong> — ${w.firstAnimeTitle} (${w.videoQualities})</li>")
                }
            }

            appendLine("""</ul>

<h2>Sources with Browse + Episodes (no video)</h2>
<ul>""")
            val partial = results.filter { it.browseStatus == "✅" && it.episodeStatus == "✅" && it.videoStatus != "✅" }
            if (partial.isEmpty()) {
                appendLine("  <li>None</li>")
            } else {
                partial.forEach { p ->
                    appendLine("  <li><strong>${p.name}</strong> — ${p.firstAnimeTitle} (video: ${p.videoStatus})</li>")
                }
            }

            appendLine("""</ul>

<h2>Browse Failure Categories</h2>
<table>
<tr><th>Category</th><th>Count</th><th>Extensions</th></tr>""")
            val browseFailed = results.filter { it.browseStatus == "❌" || it.browseStatus == "⏱" }
            // Combine both errorDetail (load errors) and firstAnimeTitle (browse errors) for matching
            fun errText(r: ExtensionResult) = "${r.errorDetail} ${r.firstAnimeTitle}"
            data class FailureCategory(val label: String, val cssClass: String, val match: (ExtensionResult) -> Boolean)
            val categories = listOf(
                FailureCategory("🛡 Cloudflare/WAF Block", "fail") { r ->
                    r.wafStatus != "—" && r.wafStatus != "No WAF"
                },
                FailureCategory("🌐 DNS Failure", "timeout") { r ->
                    errText(r).contains("UnknownHostException", ignoreCase = true)
                },
                FailureCategory("🔒 SSL Error", "fail") { r ->
                    errText(r).contains("SSL", ignoreCase = true) ||
                    errText(r).contains("certificate", ignoreCase = true)
                },
                FailureCategory("⏱ Timeout", "timeout") { r ->
                    r.browseStatus == "⏱"
                },
                FailureCategory("📡 Network Error", "fail") { r ->
                    errText(r).contains("IOException", ignoreCase = true) ||
                    errText(r).contains("HttpException", ignoreCase = true) ||
                    errText(r).contains("NoRouteToHost", ignoreCase = true) ||
                    errText(r).contains("ConnectException", ignoreCase = true)
                },
                FailureCategory("🔄 Extension Bug", "warn") { r ->
                    r.wafStatus == "—" || r.wafStatus == "No WAF"
                },
            )
            val categorized = mutableSetOf<String>()
            for (cat in categories) {
                val matches = browseFailed.filter { cat.match(it) && it.name !in categorized }
                if (matches.isNotEmpty()) {
                    categorized.addAll(matches.map { it.name })
                    appendLine("<tr><td>${cat.label}</td><td>${matches.size}</td><td>${matches.joinToString(", ") { it.name }.take(200)}</td></tr>")
                }
            }
            val uncategorized = browseFailed.filter { it.name !in categorized }
            if (uncategorized.isNotEmpty()) {
                appendLine("<tr><td>❓ Other</td><td>${uncategorized.size}</td><td>${uncategorized.joinToString(", ") { it.name }.take(200)}</td></tr>")
            }

            appendLine("""</table>

<h2>Failed to Load</h2>
<ul>""")
            val failed = results.filter { it.loadStatus != "✅" }
            if (failed.isEmpty()) {
                appendLine("  <li>None — all extensions loaded successfully</li>")
            } else {
                failed.forEach { f ->
                    appendLine("  <li><strong>${f.name}</strong> — ${f.errorDetail}</li>")
                }
            }

            appendLine("""
</ul>
</body>
</html>""")
        }

        try {
            FileWriter(REPORT_PATH).use { it.write(html) }
        } catch (e: Exception) {
            println("⚠ Failed to write HTML report: ${e.message}")
        }
    }
}

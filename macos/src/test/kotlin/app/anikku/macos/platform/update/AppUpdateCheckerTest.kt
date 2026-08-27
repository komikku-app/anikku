package app.anikku.macos.platform.update

import app.anikku.macos.platform.web.BrowserLauncher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppUpdateCheckerTest {

    private var lastInterceptor: LastCallInterceptor? = null
    private lateinit var checker: AppUpdateChecker

    class LastCallInterceptor : Interceptor {
        var statusCode: Int = 200
        var responseBody: String = "{}"
        var calls: Int = 0
        var lastRequest: okhttp3.Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            val request = chain.request()
            lastRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode in 200..299) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    @BeforeEach
    fun setUp() {
        BrowserLauncher.testMode = false
        lastInterceptor = LastCallInterceptor()
        checker = AppUpdateChecker(
            currentVersion = "1.0.0",
            client = OkHttpClient.Builder().addInterceptor(lastInterceptor!!).build(),
            githubApiBase = "http://mock/api",
            allowInsecureEndpointForTests = true,
        )
    }

    @AfterEach
    fun tearDown() {
        BrowserLauncher.testMode = false
        BrowserLauncher.lastOpenedUri = null
    }

    @Test
    fun `default updater endpoint uses HTTPS`() {
        val interceptor = LastCallInterceptor()
        val secureChecker = AppUpdateChecker(
            currentVersion = "1.0.0",
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )

        secureChecker.checkForUpdateSync()

        assertEquals("https", interceptor.lastRequest?.url?.scheme)
        assertEquals("api.github.com", interceptor.lastRequest?.url?.host)
    }

    @Test
    fun `insecure endpoint is rejected outside explicit test opt in`() {
        val insecureChecker = AppUpdateChecker(
            currentVersion = "1.0.0",
            client = OkHttpClient.Builder().build(),
            githubApiBase = "http://mock/api",
        )

        val result = insecureChecker.checkForUpdateSync()

        val failure = result as? UpdateCheckResult.Failed
            ?: error("Expected Failed result, got $result")
        assertTrue(failure.reason.contains("HTTPS"))
    }

    @Test
    fun `returns update when newer version available`() {
        lastInterceptor!!.responseBody = """
        {
            "tag_name": "v2.0.0",
            "html_url": "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            "assets": [{
                "name": "Anikku-2.0.0.dmg",
                "browser_download_url": "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg"
            }]
        }
        """.trimIndent()

        val result = checker.checkForUpdateSync()

        val available = result as? UpdateCheckResult.Available
            ?: error("Expected Available result, got $result")
        assertEquals("v2.0.0", available.update.tagName)
        assertEquals("2.0.0", available.update.versionName)
        assertEquals(
            "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            available.update.downloadUrl,
        )
    }

    @Test
    fun `semantic comparison handles multi digit components`() {
        assertTrue(AppUpdateChecker.compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.2.10", "1.2.9") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.2.9", "1.2.10") < 0)
    }

    @Test
    fun `semantic comparison orders prereleases before release`() {
        assertTrue(AppUpdateChecker.compareVersions("1.0.0", "1.0.0-rc.1") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.0.0-beta.2", "1.0.0-beta.11") < 0)
        assertEquals(0, AppUpdateChecker.compareVersions("v1.0.0+build.7", "1.0.0+build.8"))
    }

    @Test
    fun `malformed versions fail rather than compare lexicographically`() {
        assertTrue(
            runCatching { AppUpdateChecker.compareVersions("1.0", "1.0.0") }.isFailure,
        )

        val result = AppUpdateChecker(
            currentVersion = "not-a-version",
            client = OkHttpClient.Builder().build(),
            githubApiBase = "http://mock/api",
            allowInsecureEndpointForTests = true,
        ).checkForUpdateSync()

        assertTrue(result is UpdateCheckResult.Failed)
    }

    @Test
    fun `no update is distinct from API failure`() {
        lastInterceptor!!.responseBody = """{"tag_name":"v1.0.0","html_url":"https://github.com/ErnestHysa/anikku/releases/tag/v1.0.0"}"""
        assertTrue(checker.checkForUpdateSync() is UpdateCheckResult.NoUpdate)

        lastInterceptor!!.statusCode = 403
        assertTrue(checker.checkForUpdateSync() is UpdateCheckResult.Failed)
    }

    @Test
    fun `transient server failure is retried with bounded attempts`() {
        lastInterceptor!!.statusCode = 503
        val result = checker.checkForUpdateSync()

        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(2, lastInterceptor!!.calls)
    }

    @Test
    fun `insecure release links are rejected and secure release page is retained`() {
        lastInterceptor!!.responseBody = """
        {
            "tag_name": "v2.0.0",
            "html_url": "http://example.invalid/release",
            "assets": [{"name":"Anikku-2.0.0.dmg","browser_download_url":"http://example.invalid/app.dmg"}]
        }
        """.trimIndent()

        val result = checker.checkForUpdateSync()

        val failure = result as? UpdateCheckResult.Failed
            ?: error("Expected Failed result, got $result")
        assertTrue(failure.reason.contains("trusted", ignoreCase = true))
    }

    @Test
    fun `fallback always uses the secure release page without installation`() {
        lastInterceptor!!.responseBody = """
        {
            "tag_name": "v2.0.0",
            "html_url": "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            "assets": []
        }
        """.trimIndent()

        val result = checker.checkForUpdateSync() as? UpdateCheckResult.Available
            ?: error("Expected Available result")
        assertEquals("https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0", result.update.downloadUrl)
    }

    @Test
    fun `browser delegation works only for HTTPS update URL`() {
        BrowserLauncher.testMode = true
        val update = UpdateInfo(
            tagName = "v2.0.0",
            versionName = "2.0.0",
            htmlUrl = "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            downloadUrl = "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg",
        )

        checker.openDownloadPage(update)
        assertNotNull(BrowserLauncher.lastOpenedUri)
        assertFalse(BrowserLauncher.lastOpenedUri!!.scheme == "http")

        checker.openDownloadPage(update.copy(
            htmlUrl = "http://example.invalid/release",
            downloadUrl = "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg",
        ))
        assertEquals(
            "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            BrowserLauncher.lastOpenedUri?.toString(),
        )

        checker.openDownloadPage(update.copy(
            htmlUrl = "https://example.invalid/release",
            downloadUrl = "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg",
        ))
        assertEquals(
            "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            BrowserLauncher.lastOpenedUri?.toString(),
        )
    }
}

package app.anikku.macos.platform.update

import app.anikku.macos.platform.web.BrowserLauncher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Explicit result of an update check. A failed check must never be presented
 * as "up to date" because that hides network, parsing, and configuration
 * failures from the user.
 */
sealed interface UpdateCheckResult {
    data object NoUpdate : UpdateCheckResult
    /** Sparkle owns the native dialog; no update result is available to Kotlin. */
    data object SparkleDialogOpened : UpdateCheckResult
    data class Available(val update: UpdateInfo) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * GitHub-based informational updater for macOS.
 *
 * This fallback never installs an artifact. It only opens a trusted HTTPS
 * GitHub release page in the user's browser. Packaged automatic installation
 * remains Sparkle's responsibility, including Sparkle's Ed25519 verification.
 */
class AppUpdateChecker(
    private val currentVersion: String,
    private val repoOwner: String = "ErnestHysa",
    private val repoName: String = "anikku",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    /** For tests only. Production update endpoints must use HTTPS. */
    private val githubApiBase: String = "https://api.github.com",
    private val allowInsecureEndpointForTests: Boolean = false,
) {

    companion object {
        private const val MAX_ATTEMPTS = 2

        /** Compare two strict SemVer values, returning positive when [candidate] is newer. */
        internal fun compareVersions(candidate: String, current: String): Int {
            val candidateVersion = SemanticVersion.parse(candidate)
                ?: throw IllegalArgumentException("Malformed candidate version")
            val currentVersion = SemanticVersion.parse(current)
                ?: throw IllegalArgumentException("Malformed current version")
            return candidateVersion.compareTo(currentVersion)
        }
    }

    /** Check for updates asynchronously without conflating failure and no-update. */
    fun checkForUpdate(onResult: (UpdateCheckResult) -> Unit) {
        Thread {
            onResult(checkForUpdateSync())
        }.apply {
            isDaemon = true
            name = "anikku-update-check"
            start()
        }
    }

    /**
     * Check for updates synchronously.
     *
     * No network or parsing failure is reported as [UpdateCheckResult.Failed].
     * The fallback is informational only; it never downloads or installs a DMG.
     */
    fun checkForUpdateSync(): UpdateCheckResult {
        val current = SemanticVersion.parse(currentVersion)
            ?: return UpdateCheckResult.Failed("Current application version is malformed")
        val apiBase = validateApiBase() ?: return UpdateCheckResult.Failed("Updater endpoint must use HTTPS")
        val request = try {
            Request.Builder()
                .url("$apiBase/repos/$repoOwner/$repoName/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Anikku-macOS/${current.normalized}")
                .build()
        } catch (_: IllegalArgumentException) {
            return UpdateCheckResult.Failed("Updater endpoint is invalid")
        }

        var lastFailure = "Unable to contact the update service"
        repeat(MAX_ATTEMPTS) { attemptIndex ->
            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (response.code in 500..599 && attemptIndex < MAX_ATTEMPTS - 1) {
                        lastFailure = "Update service returned HTTP ${response.code}"
                        return@use
                    }
                    if (!response.isSuccessful) {
                        return UpdateCheckResult.Failed("Update service returned HTTP ${response.code}")
                    }
                    if (bodyString.isBlank()) {
                        return UpdateCheckResult.Failed("Update service returned an empty response")
                    }

                    val release = try {
                        kotlinx.serialization.json.Json.parseToJsonElement(bodyString).jsonObject
                    } catch (_: Exception) {
                        return UpdateCheckResult.Failed("Update service returned invalid JSON")
                    }

                    val tagName = release["tag_name"]?.jsonPrimitive?.content
                        ?: return UpdateCheckResult.Failed("Update response did not contain a version")
                    val remote = SemanticVersion.parse(tagName)
                        ?: return UpdateCheckResult.Failed("Update response contained a malformed version")

                    if (remote <= current) {
                        logger.info { "App is up to date (${current.normalized})" }
                        return UpdateCheckResult.NoUpdate
                    }

                    val htmlUrl = release["html_url"]?.jsonPrimitive?.content
                        ?.takeIf(::isTrustedReleaseUrl)
                        ?: return UpdateCheckResult.Failed("Update response did not contain a trusted release URL")

                    return UpdateCheckResult.Available(
                        UpdateInfo(
                            tagName = tagName,
                            versionName = remote.normalized,
                            htmlUrl = htmlUrl,
                            // The fallback is informational only. Never direct the
                            // user to an unverified artifact; Sparkle owns verified
                            // installation in packaged builds.
                            downloadUrl = htmlUrl,
                            releaseBody = release["body"]?.jsonPrimitive?.content.orEmpty(),
                            publishedAt = release["published_at"]?.jsonPrimitive?.content.orEmpty(),
                        ),
                    )
                }
            } catch (e: IOException) {
                lastFailure = "Update request failed (${e::class.simpleName ?: "I/O error"})"
                if (attemptIndex == MAX_ATTEMPTS - 1) {
                    logger.warn { lastFailure }
                }
            } catch (e: Exception) {
                logger.warn { "Update check failed (${e::class.simpleName ?: "error"})" }
                return UpdateCheckResult.Failed("Update check failed")
            }
        }

        return UpdateCheckResult.Failed(lastFailure)
    }

    /** Open the trusted HTTPS release page; never install an artifact. */
    fun openDownloadPage(updateInfo: UpdateInfo) {
        openReleasePage(updateInfo)
    }

    fun openReleasePage(updateInfo: UpdateInfo) {
        if (!isTrustedReleaseUrl(updateInfo.htmlUrl)) {
            logger.warn { "Refusing to open an untrusted release URL" }
            return
        }
        BrowserLauncher.openSafe(updateInfo.htmlUrl)
    }

    /** Check and open the informational HTTPS page when an update is available. */
    fun checkAndPrompt(onResult: ((Boolean) -> Unit)? = null) {
        checkForUpdate { result ->
            if (result is UpdateCheckResult.Available) {
                openReleasePage(result.update)
                onResult?.invoke(true)
            } else {
                onResult?.invoke(false)
            }
        }
    }

    private fun validateApiBase(): String? {
        return try {
            val normalized = githubApiBase.trimEnd('/')
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase()
            val isTestEndpoint = allowInsecureEndpointForTests && scheme == "http"
            val isProductionEndpoint = scheme == "https" &&
                uri.host.equals("api.github.com", ignoreCase = true) &&
                uri.userInfo == null &&
                uri.fragment == null
            if ((isProductionEndpoint || isTestEndpoint) && !uri.host.isNullOrBlank()) {
                normalized
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isTrustedReleaseUrl(value: String): Boolean {
        return try {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                uri.fragment == null &&
                uri.host?.lowercase() in setOf("github.com", "www.github.com")
        } catch (_: Exception) {
            false
        }
    }

    private fun isHttpsUrl(value: String): Boolean {
        return try {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                uri.fragment == null &&
                !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}

/** Strict SemVer 2.0 value with prerelease ordering and ignored build metadata. */
private data class SemanticVersion(
    val major: BigInteger,
    val minor: BigInteger,
    val patch: BigInteger,
    val prerelease: List<String>,
    val normalized: String,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[index]
            val right = other.prerelease[index]
            if (left == right) continue
            val leftNumeric = left.toBigIntegerOrNull()
            val rightNumeric = right.toBigIntegerOrNull()
            if (leftNumeric != null && rightNumeric != null) return leftNumeric.compareTo(rightNumeric)
            if (leftNumeric != null) return -1
            if (rightNumeric != null) return 1
            return left.compareTo(right)
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex(
            "^(?:v|r)?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
        )

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val (major, minor, patch, prerelease) = match.destructured
            val pre = prerelease.takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (pre.any { it.isEmpty() || (it.length > 1 && it[0] == '0' && it.all(Char::isDigit)) }) return null
            val normalized = buildString {
                append(major).append('.').append(minor).append('.').append(patch)
                if (pre.isNotEmpty()) append('-').append(pre.joinToString("."))
            }
            return SemanticVersion(
                major = major.toBigInteger(),
                minor = minor.toBigInteger(),
                patch = patch.toBigInteger(),
                prerelease = pre,
                normalized = normalized,
            )
        }
    }
}

/**
 * Information about an available update. The fallback opens the release page
 * in the browser only; Sparkle handles verified automatic installation in packaged apps.
 */
@Serializable
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val htmlUrl: String,
    /** Retained for Sparkle/appcast metadata; fallback UI never opens this URL. */
    val downloadUrl: String,
    val releaseBody: String = "",
    val publishedAt: String = "",
)

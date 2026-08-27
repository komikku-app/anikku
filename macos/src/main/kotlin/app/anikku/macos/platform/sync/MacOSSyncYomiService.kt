package app.anikku.macos.platform.sync

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.storage.MacOSAtomicFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import java.io.File

private val syncYomiLogger = KotlinLogging.logger {}

enum class SyncYomiState { DISCONNECTED, READY, SYNCING, ERROR }

enum class SyncYomiOutcome { UPLOADED, MERGED, NOT_MODIFIED, CONFLICT }

data class SyncYomiResult(
    val success: Boolean,
    val outcome: SyncYomiOutcome? = null,
    val error: String? = null,
) {
    companion object {
        fun success(outcome: SyncYomiOutcome) = SyncYomiResult(true, outcome)
        fun failure(message: String) = SyncYomiResult(false, error = message)
    }
}

/**
 * SyncYomi's self-hosted ETag protocol using the macOS portable backup payload.
 *
 * The server treats the body as opaque `application/octet-stream` data. API
 * tokens live only in Keychain; preferences retain the non-secret host and
 * concurrency ETag. HTTPS is mandatory except for loopback development hosts.
 */
class MacOSSyncYomiService(
    private val httpClient: OkHttpClient,
    private val backupManager: MacOSBackupManager,
    private val cacheDirectory: File,
    private val secretStore: MacOSSecretStore,
    private val preferenceStore: MacOSPreferenceStore,
) {
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(SyncYomiState.DISCONNECTED)
    val state: StateFlow<SyncYomiState> = _state.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    @Volatile
    private var configured = false

    val host: String get() = preferenceStore.getString(KEY_HOST, "").get()
    val isConfigured: Boolean get() = configured

    fun restoreConfiguration(): Boolean {
        val ready = validatedEndpoint(host) != null && secretStore.retrieve(KEY_API_TOKEN) != null
        configured = ready
        _lastError.value = secretStore.lastError
        _state.value = if (ready) SyncYomiState.READY else SyncYomiState.DISCONNECTED
        return ready
    }

    fun configure(host: String, apiToken: String): SyncYomiResult {
        val normalizedHost = normalizedHost(host)
            ?: return fail("Use an HTTPS SyncYomi URL (HTTP is allowed only on loopback)")
        if (!secretStore.isAvailable) return fail("macOS Keychain is unavailable")
        val normalizedToken = apiToken.trim().ifBlank {
            secretStore.retrieve(KEY_API_TOKEN) ?: return fail("Enter a SyncYomi API token")
        }
        if (!secretStore.store(KEY_API_TOKEN, normalizedToken)) {
            return fail("Could not save the SyncYomi token in Keychain")
        }
        preferenceStore.getString(KEY_HOST, "").set(normalizedHost)
        preferenceStore.getString(KEY_ETAG, "").delete()
        _lastError.value = null
        configured = true
        _state.value = SyncYomiState.READY
        return SyncYomiResult.success(SyncYomiOutcome.NOT_MODIFIED)
    }

    fun disconnect(): Boolean {
        if (!secretStore.delete(KEY_API_TOKEN)) {
            fail("Could not remove the SyncYomi token from Keychain")
            return false
        }
        preferenceStore.getString(KEY_HOST, "").delete()
        preferenceStore.getString(KEY_ETAG, "").delete()
        configured = false
        _lastError.value = null
        _state.value = SyncYomiState.DISCONNECTED
        return true
    }

    suspend fun sync(): SyncYomiResult = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val endpoint = validatedEndpoint(host) ?: return@withContext fail("Configure SyncYomi first")
            val apiToken = secretStore.retrieve(KEY_API_TOKEN)
                ?: return@withContext fail(secretStore.lastError ?: "Configure SyncYomi first")
            _state.value = SyncYomiState.SYNCING
            _lastError.value = null

            cacheDirectory.mkdirs()
            val remoteFile = File(cacheDirectory, ".syncyomi-remote-${System.nanoTime()}.backup")
            val mergedFile = File(cacheDirectory, ".syncyomi-local-${System.nanoTime()}.backup")
            try {
                val pull = pull(endpoint, apiToken, remoteFile)
                val result = when (pull.status) {
                    PullStatus.NOT_MODIFIED -> {
                        if (!backupManager.exportTo(mergedFile)) return@withContext fail("Could not create sync backup")
                        push(endpoint, apiToken, mergedFile, pull.etag, SyncYomiOutcome.UPLOADED)
                    }
                    PullStatus.NOT_FOUND -> {
                        if (!backupManager.exportTo(mergedFile)) return@withContext fail("Could not create sync backup")
                        push(endpoint, apiToken, mergedFile, null, SyncYomiOutcome.UPLOADED)
                    }
                    PullStatus.DOWNLOADED -> {
                        val restore = backupManager.importFrom(remoteFile)
                        if (!restore.success) return@withContext fail(
                            "Remote SyncYomi data is not a compatible backup: ${restore.error}",
                        )
                        if (!backupManager.exportTo(mergedFile)) return@withContext fail("Could not create merged sync backup")
                        push(endpoint, apiToken, mergedFile, pull.etag, SyncYomiOutcome.MERGED)
                    }
                }
                if (result.success) {
                    preferenceStore.getLong(KEY_LAST_SYNC, 0L).set(System.currentTimeMillis())
                    _state.value = SyncYomiState.READY
                }
                result
            } catch (error: Exception) {
                syncYomiLogger.warn(error) { "SyncYomi sync failed" }
                fail("SyncYomi failed: ${error.message?.take(180) ?: error::class.simpleName}")
            } finally {
                remoteFile.delete()
                mergedFile.delete()
            }
        }
    }

    private fun pull(endpoint: HttpUrl, apiToken: String, destination: File): PullResult {
        val request = Request.Builder()
            .url(endpoint)
            .header(API_TOKEN_HEADER, apiToken)
            .apply {
                preferenceStore.getString(KEY_ETAG, "").get().takeIf(String::isNotBlank)?.let {
                    header("If-None-Match", it)
                }
            }
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            return when (response.code) {
                304 -> PullResult(
                    PullStatus.NOT_MODIFIED,
                    preferenceStore.getString(KEY_ETAG, "").get().takeIf(String::isNotBlank)
                        ?: error("SyncYomi returned 304 without a saved ETag"),
                )
                404 -> PullResult(PullStatus.NOT_FOUND)
                in 200..299 -> {
                    val etag = response.header("ETag")?.takeIf(String::isNotBlank)
                        ?: error("SyncYomi response is missing ETag")
                    val body = response.body ?: error("SyncYomi response has no body")
                    if (body.contentLength() > MAX_BACKUP_BYTES) error("Remote backup exceeds 100 MiB")
                    val buffer = Buffer()
                    body.source().use { source ->
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer, 64 * 1024L)
                            if (read == -1L) break
                            total += read
                            if (total > MAX_BACKUP_BYTES) error("Remote backup exceeds 100 MiB")
                        }
                    }
                    MacOSAtomicFile.writeText(destination, buffer.readUtf8())
                    PullResult(PullStatus.DOWNLOADED, etag)
                }
                else -> error("SyncYomi download returned HTTP ${response.code}: ${response.body?.string()?.take(160)}")
            }
        }
    }

    private fun push(
        endpoint: HttpUrl,
        apiToken: String,
        payload: File,
        etag: String?,
        successOutcome: SyncYomiOutcome,
    ): SyncYomiResult {
        val request = Request.Builder()
            .url(endpoint)
            .header(API_TOKEN_HEADER, apiToken)
            .apply { etag?.let { header("If-Match", it) } }
            .put(payload.asRequestBody(BINARY_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 412) {
                _state.value = SyncYomiState.READY
                return SyncYomiResult(
                    success = false,
                    outcome = SyncYomiOutcome.CONFLICT,
                    error = "Remote SyncYomi data changed; run sync again to merge it",
                )
            }
            if (!response.isSuccessful) {
                return fail("SyncYomi upload returned HTTP ${response.code}: ${response.body?.string()?.take(160)}")
            }
            val newEtag = response.header("ETag")?.takeIf(String::isNotBlank)
                ?: return fail("SyncYomi upload response is missing ETag")
            preferenceStore.getString(KEY_ETAG, "").set(newEtag)
            return SyncYomiResult.success(successOutcome)
        }
    }

    private fun normalizedHost(value: String): String? {
        val parsed = value.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (!isSecureOrLoopback(parsed)) return null
        if (parsed.query != null || parsed.fragment != null) return null
        return parsed.toString().trimEnd('/')
    }

    private fun validatedEndpoint(value: String): HttpUrl? {
        val normalized = normalizedHost(value) ?: return null
        return normalized.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("api/sync/content")
            ?.build()
    }

    private fun isSecureOrLoopback(url: HttpUrl): Boolean =
        url.isHttps || url.host == "localhost" || url.host == "127.0.0.1" || url.host == "::1"

    private fun fail(message: String): SyncYomiResult {
        _lastError.value = message
        _state.value = SyncYomiState.ERROR
        return SyncYomiResult.failure(message)
    }

    private enum class PullStatus { NOT_MODIFIED, NOT_FOUND, DOWNLOADED }
    private data class PullResult(val status: PullStatus, val etag: String? = null)

    companion object {
        private const val KEY_HOST = "syncyomi_host"
        private const val KEY_ETAG = "syncyomi_etag"
        private const val KEY_LAST_SYNC = "syncyomi_last_sync"
        private const val KEY_API_TOKEN = "api-token"
        private const val API_TOKEN_HEADER = "X-API-Token"
        private const val MAX_BACKUP_BYTES = 100L * 1024L * 1024L
        private val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}

val LocalSyncYomiService = compositionLocalOf<MacOSSyncYomiService?> { null }

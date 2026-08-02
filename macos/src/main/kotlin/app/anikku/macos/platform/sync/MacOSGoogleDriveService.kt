package app.anikku.macos.platform.sync

import androidx.compose.runtime.compositionLocalOf
import app.anikku.macos.platform.auth.OAuthServer
import app.anikku.macos.platform.backup.ImportResult
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.web.BrowserLauncher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

enum class GoogleDriveConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class GoogleDriveResult<T>(
    val value: T? = null,
    val error: String? = null,
) {
    val success: Boolean get() = error == null

    companion object {
        fun <T> success(value: T): GoogleDriveResult<T> = GoogleDriveResult(value = value)
        fun <T> failure(message: String): GoogleDriveResult<T> = GoogleDriveResult(error = message)
    }
}

/**
 * Coordinates secure desktop OAuth and local/cloud backup operations.
 *
 * Google tokens and the desktop OAuth client ID are stored as one versioned
 * record in macOS Keychain. The browser flow uses a random loopback port,
 * PKCE-S256, and a constant-time `state` check. No OAuth client secret is
 * embedded because installed desktop applications are public clients.
 */
class MacOSGoogleDriveService(
    private val driveClient: GoogleDriveRestClient,
    private val backupManager: MacOSBackupManager,
    private val backupsDirectory: File,
    private val secretStore: MacOSSecretStore,
    private val oauthServerFactory: () -> OAuthServer = { OAuthServer() },
    private val browserLauncher: (String) -> Boolean = BrowserLauncher::openSafe,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val operationMutex = Mutex()
    private val random = SecureRandom()
    private val _connectionState = MutableStateFlow(GoogleDriveConnectionState.DISCONNECTED)
    val connectionState: StateFlow<GoogleDriveConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var session: StoredGoogleDriveSession? = null

    val savedClientId: String? get() = session?.clientId
    val isConnected: Boolean get() = _connectionState.value == GoogleDriveConnectionState.CONNECTED

    /** Load and, when needed, refresh a Keychain-backed session. */
    suspend fun restoreSession(): GoogleDriveResult<Unit> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val encoded = secretStore.retrieve(KEY_SESSION)
                ?: return@withContext disconnectedFailure(
                    secretStore.lastError?.let { "Keychain unavailable: $it" } ?: "No Google Drive session",
                    isError = secretStore.lastError != null,
                )
            val stored = runCatching { json.decodeFromString<StoredGoogleDriveSession>(encoded) }
                .getOrElse { return@withContext fail("Stored Google Drive session is invalid") }
            session = stored
            authenticateOrRefresh(stored)
        }
    }

    /** Run the Google desktop OAuth flow in the system browser. */
    suspend fun connect(
        clientId: String,
        timeoutSeconds: Long = 180,
    ): GoogleDriveResult<Unit> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val normalizedClientId = clientId.trim()
            if (!isValidDesktopClientId(normalizedClientId)) {
                return@withContext fail("Enter a valid Google Desktop OAuth client ID")
            }
            if (!secretStore.isAvailable) return@withContext fail("macOS Keychain is unavailable")

            _connectionState.value = GoogleDriveConnectionState.CONNECTING
            _lastError.value = null
            val oauth = oauthServerFactory()
            try {
                val redirectUri = oauth.start(callbackPath = CALLBACK_PATH)
                val state = randomUrlSafe(32)
                val verifier = randomUrlSafe(64)
                val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
                )
                val authorizationUrl = buildAuthorizationUrl(
                    clientId = normalizedClientId,
                    redirectUri = redirectUri,
                    state = state,
                    codeChallenge = challenge,
                )
                if (!browserLauncher(authorizationUrl)) return@withContext fail("Could not open the system browser")

                val parameters = oauth.awaitCallback(timeoutSeconds.coerceIn(15, 600), TimeUnit.SECONDS)
                    ?: return@withContext fail("Google authorization timed out")
                parameters["error"]?.let { return@withContext fail("Google authorization failed: $it") }
                if (!constantTimeEquals(state, parameters["state"])) {
                    return@withContext fail("Google authorization state did not match")
                }
                val code = parameters["code"]?.takeIf(String::isNotBlank)
                    ?: return@withContext fail("Google did not return an authorization code")
                val token = driveClient.exchangeCode(
                    code = code,
                    clientId = normalizedClientId,
                    redirectUri = redirectUri,
                    codeVerifier = verifier,
                ) ?: return@withContext fail("Google token exchange failed")
                if (token.refreshToken.isBlank()) {
                    return@withContext fail("Google did not return an offline refresh token; revoke access and try again")
                }
                val stored = StoredGoogleDriveSession(
                    clientId = normalizedClientId,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    expiresAtMillis = expiryTime(token.expiresIn),
                )
                if (!persistSession(stored)) return@withContext fail("Could not save Google tokens in Keychain")
                session = stored
                driveClient.authenticate(stored.accessToken)
                connectedSuccess()
            } catch (e: Exception) {
                logger.warn(e) { "Google Drive connection failed" }
                fail("Google Drive connection failed: ${e.message?.take(160) ?: e::class.simpleName}")
            } finally {
                if (oauth.isRunning) oauth.stop()
            }
        }
    }

    /** Clear the Keychain session. Credentials are retained only if deletion fails. */
    suspend fun disconnect(): GoogleDriveResult<Unit> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!secretStore.delete(KEY_SESSION)) return@withContext fail("Could not remove Google tokens from Keychain")
            session = null
            driveClient.logout()
            _lastError.value = null
            _connectionState.value = GoogleDriveConnectionState.DISCONNECTED
            GoogleDriveResult.success(Unit)
        }
    }

    /** Export a fresh local backup and upload it into Anikku's Drive folder. */
    suspend fun uploadBackup(): GoogleDriveResult<GoogleDriveFile> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()?.let { return@withContext GoogleDriveResult.failure(it) }
            val backup = backupManager.exportToDir(backupsDirectory)
                ?: return@withContext fail("Could not create a local backup")
            val folderId = driveClient.getOrCreateBackupFolder()
                ?: return@withContext authAwareFailure("Could not access the Anikku Backups folder")
            val fileId = driveClient.uploadFile(backup, BACKUP_MIME_TYPE, folderId)
                ?: return@withContext authAwareFailure("Google Drive upload failed")
            GoogleDriveResult.success(
                GoogleDriveFile(
                    id = fileId,
                    name = backup.name,
                    mimeType = BACKUP_MIME_TYPE,
                    size = backup.length(),
                ),
            )
        }
    }

    suspend fun listBackups(): GoogleDriveResult<List<GoogleDriveFile>> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()?.let { return@withContext GoogleDriveResult.failure(it) }
            val folderId = driveClient.getOrCreateBackupFolder()
                ?: return@withContext authAwareFailure("Could not access the Anikku Backups folder")
            val files = driveClient.listFiles(
                folderId = folderId,
                query = "name contains 'anikku_backup_' and trashed=false",
            ).filter { it.name.endsWith(MacOSBackupManager.BACKUP_EXTENSION) }
                .sortedByDescending(GoogleDriveFile::modifiedTime)
            GoogleDriveResult.success(files)
        }
    }

    /** Download a cloud backup atomically into the local backup timeline. */
    suspend fun downloadBackup(file: GoogleDriveFile): GoogleDriveResult<File> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()?.let { return@withContext GoogleDriveResult.failure(it) }
            val localFile = File(backupsDirectory, sanitizeBackupName(file.name))
            if (!driveClient.downloadFile(file.id, localFile)) {
                return@withContext authAwareFailure("Google Drive download failed")
            }
            GoogleDriveResult.success(localFile)
        }
    }

    /** Explicitly download and import a cloud backup after UI confirmation. */
    suspend fun restoreBackup(file: GoogleDriveFile): GoogleDriveResult<ImportResult> {
        val downloaded = downloadBackup(file)
        val local = downloaded.value ?: return GoogleDriveResult.failure(downloaded.error ?: "Download failed")
        return withContext(Dispatchers.IO) {
            val result = backupManager.importFrom(local)
            if (result.success) GoogleDriveResult.success(result)
            else GoogleDriveResult.failure(result.error ?: "Backup restore failed")
        }
    }

    suspend fun deleteBackup(fileId: String): GoogleDriveResult<Unit> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()?.let { return@withContext GoogleDriveResult.failure(it) }
            if (driveClient.deleteFile(fileId)) GoogleDriveResult.success(Unit)
            else authAwareFailure("Could not delete the Google Drive backup")
        }
    }

    internal fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String = AUTHORIZATION_ENDPOINT.toHttpUrl().newBuilder()
        .addQueryParameter("client_id", clientId)
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("scope", DRIVE_FILE_SCOPE)
        .addQueryParameter("access_type", "offline")
        .addQueryParameter("prompt", "consent")
        .addQueryParameter("state", state)
        .addQueryParameter("code_challenge", codeChallenge)
        .addQueryParameter("code_challenge_method", "S256")
        .build()
        .toString()

    private fun authenticateOrRefresh(stored: StoredGoogleDriveSession): GoogleDriveResult<Unit> {
        if (stored.accessToken.isNotBlank() && stored.expiresAtMillis > clockMillis() + REFRESH_SKEW_MILLIS) {
            driveClient.authenticate(stored.accessToken)
            return connectedSuccess()
        }
        if (stored.refreshToken.isBlank()) return fail("Google Drive session has no refresh token")
        val token = driveClient.refreshAccessToken(stored.refreshToken, stored.clientId)
            ?: return fail("Google Drive session refresh failed")
        val refreshed = stored.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken.ifBlank { stored.refreshToken },
            expiresAtMillis = expiryTime(token.expiresIn),
        )
        if (!persistSession(refreshed)) return fail("Could not update Google tokens in Keychain")
        session = refreshed
        driveClient.authenticate(refreshed.accessToken)
        return connectedSuccess()
    }

    private fun ensureAuthenticated(): String? {
        val current = session ?: run {
            val encoded = secretStore.retrieve(KEY_SESSION) ?: return "Connect Google Drive first"
            runCatching { json.decodeFromString<StoredGoogleDriveSession>(encoded) }.getOrNull()
                ?: return "Stored Google Drive session is invalid"
        }
        session = current
        return authenticateOrRefresh(current).error
    }

    private fun persistSession(value: StoredGoogleDriveSession): Boolean =
        secretStore.store(KEY_SESSION, json.encodeToString(value))

    private fun expiryTime(expiresInSeconds: Long): Long =
        clockMillis() + expiresInSeconds.coerceIn(60, 86_400) * 1_000L

    private fun connectedSuccess(): GoogleDriveResult<Unit> {
        _lastError.value = null
        _connectionState.value = GoogleDriveConnectionState.CONNECTED
        return GoogleDriveResult.success(Unit)
    }

    private fun <T> authAwareFailure(message: String): GoogleDriveResult<T> {
        _lastError.value = message
        return GoogleDriveResult.failure(message)
    }

    private fun <T> fail(message: String): GoogleDriveResult<T> {
        _lastError.value = message
        _connectionState.value = GoogleDriveConnectionState.ERROR
        return GoogleDriveResult.failure(message)
    }

    private fun disconnectedFailure(message: String, isError: Boolean): GoogleDriveResult<Unit> {
        _lastError.value = if (isError) message else null
        _connectionState.value = if (isError) GoogleDriveConnectionState.ERROR else GoogleDriveConnectionState.DISCONNECTED
        return GoogleDriveResult.failure(message)
    }

    private fun randomUrlSafe(byteCount: Int): String = ByteArray(byteCount)
        .also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun constantTimeEquals(expected: String, actual: String?): Boolean = actual != null &&
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    private fun isValidDesktopClientId(value: String): Boolean =
        value.length in 20..255 && value.endsWith(".apps.googleusercontent.com") &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

    private fun sanitizeBackupName(name: String): String {
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .take(180)
            .ifBlank { "anikku_backup_cloud" }
        return if (safe.endsWith(MacOSBackupManager.BACKUP_EXTENSION)) safe
        else "$safe${MacOSBackupManager.BACKUP_EXTENSION}"
    }

    @Serializable
    internal data class StoredGoogleDriveSession(
        val version: Int = 1,
        val clientId: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long,
    )

    companion object {
        private const val KEY_SESSION = "google-drive-session-v1"
        private const val CALLBACK_PATH = "/oauth/google-drive"
        private const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val BACKUP_MIME_TYPE = "application/json"
        private const val REFRESH_SKEW_MILLIS = 60_000L
    }
}

val LocalGoogleDriveService = compositionLocalOf<MacOSGoogleDriveService?> { null }

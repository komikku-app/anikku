package app.anikku.macos.platform.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Google Drive REST API v3 client for JVM/macOS.
 *
 * Replaces the Android Google Drive integration that relies on
 * Google Play Services. Uses the raw REST API with OAuth 2.0,
 * which works from any JVM application.
 *
 * ## OAuth Flow
 *
 * 1. Obtain OAuth 2.0 credentials from Google Cloud Console
 * 2. Use [OAuthServer] to handle the local redirect callback
 * 3. Exchange the authorization code for access + refresh tokens
 * 4. Use the access token to make API calls
 *
 * ## Usage
 *
 * ```kotlin
 * val drive = GoogleDriveRestClient(httpClient)
 * drive.authenticate(accessToken)
 *
 * // Upload backup
 * drive.uploadFile(backupFile, "application/json", "application/vnd.google-apps.file")
 *
 * // Download backup
 * drive.downloadFile(fileId, downloadPath)
 * ```
 */
class GoogleDriveRestClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** For testing: override the Drive API base URL. */
    private val driveApiBase: String = "https://www.googleapis.com/drive/v3",
    /** For testing: override the upload API base URL. */
    private val uploadApiBase: String = "https://www.googleapis.com/upload/drive/v3",
    /** For testing: override the OAuth token URL. */
    private val oauthTokenUrl: String = "https://oauth2.googleapis.com/token",
) {

    companion object {
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private const val MIME_FILE = "application/octet-stream"
        private const val APP_FOLDER_NAME = "Anikku Backups"
    }

    /** Current access token for API requests. */
    private var accessToken: String? = null

    /** Whether the client is authenticated. */
    val isAuthenticated: Boolean get() = accessToken != null

    /**
     * Set the access token for API requests.
     */
    fun authenticate(token: String) {
        accessToken = token.trim().takeIf(String::isNotEmpty)
    }

    /**
     * Clear the access token.
     */
    fun logout() {
        accessToken = null
    }

    // -------------------------------------------------------------------------
    // OAuth Token Exchange
    // -------------------------------------------------------------------------

    /**
     * Exchange an authorization code for Google Drive OAuth tokens.
     *
     * @param code The authorization code from OAuth callback.
     * @param clientId Google OAuth client ID.
     * @param clientSecret Google OAuth client secret.
     * @param redirectUri The redirect URI used in the authorization request.
     * @return Token response with access/refresh tokens.
     */
    fun exchangeCode(
        code: String,
        clientId: String,
        clientSecret: String = "",
        redirectUri: String,
        codeVerifier: String? = null,
    ): GoogleTokenResponse? {
        return try {
            val form = FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .apply {
                    if (clientSecret.isNotBlank()) add("client_secret", clientSecret)
                    if (!codeVerifier.isNullOrBlank()) add("code_verifier", codeVerifier)
                }
                .build()

            val request = Request.Builder()
                .url(oauthTokenUrl)
                .post(form)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return null

                if (!response.isSuccessful) {
                    logger.warn { "Google token exchange failed: ${response.code} ${bodyString.take(200)}" }
                    return null
                }

                val jsonObj = json.parseToJsonElement(bodyString).jsonObject
                GoogleTokenResponse(
                    accessToken = jsonObj["access_token"]?.jsonPrimitive?.content ?: return null,
                    refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content ?: "",
                    expiresIn = jsonObj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600,
                    scope = jsonObj["scope"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Google token exchange failed" }
            null
        }
    }

    /**
     * Refresh an expired access token.
     */
    fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String = "",
    ): GoogleTokenResponse? {
        return try {
            val form = FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .apply { if (clientSecret.isNotBlank()) add("client_secret", clientSecret) }
                .build()

            val request = Request.Builder()
                .url(oauthTokenUrl)
                .post(form)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return null

                if (!response.isSuccessful) return null

                val jsonObj = json.parseToJsonElement(bodyString).jsonObject
                GoogleTokenResponse(
                    accessToken = jsonObj["access_token"]?.jsonPrimitive?.content ?: return null,
                    refreshToken = refreshToken,
                    expiresIn = jsonObj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600,
                    scope = "",
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Google token refresh failed" }
            null
        }
    }

    // -------------------------------------------------------------------------
    // File Operations
    // -------------------------------------------------------------------------

    /**
     * Upload a file to Google Drive.
     *
     * @param file The local file to upload.
     * @param mimeType MIME type of the file.
     * @param parentFolderId ID of the parent folder (null = root).
     * @return The uploaded file's Drive ID, or null on failure.
     */
    fun uploadFile(
        file: File,
        mimeType: String = "application/octet-stream",
        parentFolderId: String? = null,
    ): String? {
        val token = accessToken ?: return null

        return try {
            // Step 1: Resumable upload session
            if (!file.isFile) return null
            val metadata = buildJsonObject {
                put("name", file.name)
                if (!parentFolderId.isNullOrBlank()) {
                    put("parents", JsonArray(listOf(JsonPrimitive(parentFolderId))))
                }
            }.toString()

            val initRequest = Request.Builder()
                .url("$uploadApiBase/files?uploadType=resumable")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", "${file.length()}")
                .post(metadata.toRequestBody("application/json".toMediaType()))
                .build()

            val uploadUrl = client.newCall(initRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn { "Upload session creation failed: ${response.code}" }
                    return null
                }
                response.header("Location") ?: return null
            }

            // Step 2: Upload the file content
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .header("Content-Type", mimeType)
                .put(file.asRequestBody(mimeType.toMediaType()))
                .build()

            client.newCall(uploadRequest).execute().use { uploadResponse ->
                val bodyString = uploadResponse.body?.string() ?: return null

                if (!uploadResponse.isSuccessful) {
                    logger.warn { "Upload failed: ${uploadResponse.code} ${bodyString.take(200)}" }
                    return null
                }

                val jsonObj = json.parseToJsonElement(bodyString).jsonObject
                jsonObj["id"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to upload file to Google Drive" }
            null
        }
    }

    /**
     * Download a file from Google Drive.
     *
     * @param fileId The Drive file ID to download.
     * @param destination The local file path to save to.
     * @return true if the download succeeded.
     */
    fun downloadFile(fileId: String, destination: File): Boolean {
        val token = accessToken ?: return false

        return try {
            require(fileId.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "Invalid Google Drive file ID" }
            val request = Request.Builder()
                .url("$driveApiBase/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .build()

            val parent = destination.absoluteFile.parentFile ?: return false
            if (!parent.exists() && !parent.mkdirs()) return false
            val temporary = File(parent, ".${destination.name}.part-${UUID.randomUUID()}")
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logger.warn { "Download failed: ${response.code}" }
                        return false
                    }
                    val body = response.body ?: return false
                    temporary.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
                }
                runCatching {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }.recoverCatching {
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }.getOrThrow()
                true
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to download file from Google Drive" }
            false
        }
    }

    /**
     * Delete a file from Google Drive.
     */
    fun deleteFile(fileId: String): Boolean {
        val token = accessToken ?: return false

        return try {
            require(fileId.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "Invalid Google Drive file ID" }
            val request = Request.Builder()
                .url("$driveApiBase/files/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete file from Google Drive" }
            false
        }
    }

    /**
     * List files in a folder.
     *
     * @param folderId The folder ID (null = root).
     * @param query Additional query string (e.g., "mimeType='application/json'").
     * @return List of file metadata objects.
     */
    fun listFiles(folderId: String? = null, query: String? = null): List<GoogleDriveFile> {
        val token = accessToken ?: return emptyList()

        return try {
            val queryParts = mutableListOf<String>()
            if (folderId != null) {
                queryParts.add("'$folderId' in parents")
            }
            if (query != null) {
                queryParts.add(query)
            }
            val url = "$driveApiBase/files".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime,createdTime)")
                .apply {
                    if (queryParts.isNotEmpty()) addQueryParameter("q", queryParts.joinToString(" and "))
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return emptyList()

                if (!response.isSuccessful) return emptyList()

                val jsonObj = json.parseToJsonElement(bodyString).jsonObject
                jsonObj["files"]?.jsonArray?.mapNotNull { element ->
                    val file = element.jsonObject
                    val id = file["id"]?.jsonPrimitive?.content.orEmpty()
                    if (id.isBlank()) return@mapNotNull null
                    GoogleDriveFile(
                        id = id,
                        name = file["name"]?.jsonPrimitive?.content ?: "",
                        mimeType = file["mimeType"]?.jsonPrimitive?.content ?: "",
                        size = file["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        modifiedTime = file["modifiedTime"]?.jsonPrimitive?.content ?: "",
                        createdTime = file["createdTime"]?.jsonPrimitive?.content ?: "",
                    )
                } ?: emptyList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to list files" }
            emptyList()
        }
    }

    /**
     * Find or create the Anikku Backups folder.
     *
     * @return The folder ID.
     */
    fun getOrCreateBackupFolder(): String? {
        val token = accessToken ?: return null

        // Check if the folder already exists
        val safeFolderName = APP_FOLDER_NAME.replace("'", "\\'")
        val existingFolders = listFiles(query = "name='$safeFolderName' and mimeType='$MIME_FOLDER' and trashed=false")
        if (existingFolders.isNotEmpty()) {
            return existingFolders.first().id
        }

        // Create the folder
        return try {
            val metadata = buildJsonObject {
                put("name", APP_FOLDER_NAME)
                put("mimeType", MIME_FOLDER)
            }.toString()

            val request = Request.Builder()
                .url("$driveApiBase/files")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(metadata.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return null

                if (!response.isSuccessful) return null

                val jsonObj = json.parseToJsonElement(bodyString).jsonObject
                jsonObj["id"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create backup folder" }
            null
        }
    }
}

/**
 * Google Drive file metadata.
 */
@Serializable
data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0,
    val modifiedTime: String = "",
    val createdTime: String = "",
)

/**
 * Google OAuth token response.
 */
@Serializable
data class GoogleTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 3600,
    val scope: String = "",
)

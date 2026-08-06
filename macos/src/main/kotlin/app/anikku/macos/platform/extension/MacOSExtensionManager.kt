package app.anikku.macos.platform.extension

import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.storage.MacOSStorageProvider
import androidx.compose.runtime.compositionLocalOf
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.IOException
import java.io.IOException as JavaIOException
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger {}

/**
 * macOS extension manager.
 *
 * Manages the lifecycle of anime extensions as JAR files:
 * - Scanning installed extensions from the extensions directory
 * - Fetching available extensions from remote repositories
 * - Downloading and installing new or updated extensions
 * - Removing extensions
 * - Trust management (SHA-256 signature verification)
 *
 * Replaces the Android ExtensionManager which uses PackageManager + APK installs.
 */
class MacOSExtensionManager(
    private val storageProvider: MacOSStorageProvider,
    private val networkHelper: MacOSNetworkHelper,
) : AutoCloseable {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val extensionsDir: File get() = storageProvider.extensionsDirectory
    private val trustDir: File get() = File(storageProvider.dataDirectory, "trust")
    private val trustFile get() = File(trustDir, "trusted_extensions.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Timestamp of last extension repo check (for rate limiting) */
    private var lastExtensionCheck: Long = 0

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Installed>())
    val installedExtensionsFlow: StateFlow<List<Extension.Installed>> = installedExtensionsMapFlow
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Available>())
    val availableExtensionsFlow: StateFlow<List<Extension.Available>> = availableExtensionsMapFlow
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    /**
     * Last repo fetch error, or null when the most recent fetch succeeded.
     * Lets the UI distinguish "repo unreachable / malformed" from a genuinely
     * empty repository instead of showing a misleading empty state.
     */
    private val _availableFetchError = MutableStateFlow<String?>(null)
    val availableFetchError: StateFlow<String?> = _availableFetchError.asStateFlow()

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Untrusted>())
    val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>> = untrustedExtensionsMapFlow
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private var trustStore: MutableMap<String, MutableList<MacOSExtensionLoader.TrustEntry>> = mutableMapOf()

    /** Whether NSFW extensions should be loaded */
    var loadNsfwSource: Boolean = false

    init {
        ensureDirectories()
        loadTrustStore()
        initExtensions()
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    private fun ensureDirectories() {
        extensionsDir.mkdirs()
        trustDir.mkdirs()
    }

    /**
     * Scan the extensions directory and load all installed extensions.
     */
    private fun initExtensions() {
        val results = MacOSExtensionLoader.loadExtensions(
            extensionsDir = extensionsDir,
            trustStore = trustStore,
            loadNsfw = loadNsfwSource,
        )

        installedExtensionsMapFlow.value = results
            .filterIsInstance<LoadResult.Success>()
            .associate { it.extension.pkgName to it.extension }

        untrustedExtensionsMapFlow.value = results
            .filterIsInstance<LoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        _isInitialized.value = true
        logger.info {
            "Loaded ${installedExtensionsMapFlow.value.size} extensions, " +
                "${untrustedExtensionsMapFlow.value.size} untrusted"
        }
    }

    /**
     * Reload a single extension after install/update (avoids full rescan).
     */
    fun reloadExtension(pkgName: String): Boolean {
        require(PACKAGE_NAME_PATTERN.matches(pkgName)) { "Invalid extension package name" }
        val jarFile = safeExtensionFile(pkgName, ".jar")
        if (!jarFile.isFile) {
            installedExtensionsMapFlow.value -= pkgName
            untrustedExtensionsMapFlow.value -= pkgName
            MacOSExtensionLoader.closeClassLoader(pkgName)
            return true
        }

        val previousInstalled = installedExtensionsMapFlow.value[pkgName]
        val previousUntrusted = untrustedExtensionsMapFlow.value[pkgName]
        val occupiedSourceIds = installedExtensionsMapFlow.value
            .filterKeys { it != pkgName }
            .values
            .flatMap { it.sources }
            .associate { it.id to it.javaClass.name }

        val result = MacOSExtensionLoader.loadExtension(
            jarFile = jarFile,
            libsDir = extensionsDir,
            trustStore = trustStore,
            loadNsfw = loadNsfwSource,
            occupiedSourceIds = occupiedSourceIds,
        )

        return when (result) {
            is LoadResult.Success -> {
                installedExtensionsMapFlow.value += pkgName to result.extension
                untrustedExtensionsMapFlow.value -= pkgName
                true
            }
            is LoadResult.Untrusted -> {
                if (previousInstalled == null) {
                    untrustedExtensionsMapFlow.value += pkgName to result.extension
                }
                false
            }
            is LoadResult.Error -> {
                if (previousInstalled == null && previousUntrusted == null) {
                    untrustedExtensionsMapFlow.value -= pkgName
                }
                false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Available extensions (repository)
    // -------------------------------------------------------------------------

    /**
     * Fetch available extensions from a repository URL.
     * Rate-limited to once per day per URL (matches Android behavior).
     *
     * @param repoBaseUrl Base URL of the extension repository
     * @param force Bypass rate limiting
     * @return List of available extensions
     */
    suspend fun findAvailableExtensions(
        repoBaseUrl: String,
        force: Boolean = false,
    ): List<Extension.Available> {
        // Rate limit: once per day (matches Android ExtensionApi behavior)
        val now = Instant.now().toEpochMilli()
        if (!force && now < lastExtensionCheck + 1.days.inWholeMilliseconds) {
            logger.debug { "Rate limited extension check. Next allowed after ${lastExtensionCheck + 1.days.inWholeMilliseconds}" }
            return availableExtensionsFlow.value
        }

        return try {
            val indexUrl = "$repoBaseUrl/index.min.json"
            val request = Request.Builder()
                .url(indexUrl)
                .get()
                .build()

            val body = networkHelper.client.newCall(request).execute().use { response ->
                response.body?.string() ?: return emptyList()
            }

            val extList: List<ExtensionJsonObject> = json.decodeFromString(body)

            val extensions = extList
                .filter { it.isValidMetadata(repoBaseUrl) }
                .filter {
                    val libVersion = it.extractLibVersion()
                    libVersion >= MacOSExtensionLoader.LIB_VERSION_MIN &&
                        libVersion <= MacOSExtensionLoader.LIB_VERSION_MAX
                }
                .map { it.toExtension(repoBaseUrl) }

            availableExtensionsMapFlow.value = extensions.associateBy { it.pkgName }
            updateInstalledStatuses(extensions)
            lastExtensionCheck = now
            _availableFetchError.value = null

            extensions
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch extensions from $repoBaseUrl" }
            _availableFetchError.value =
                "Couldn't reach the repository${repoBaseUrl.takeIf { it.isNotBlank() }?.let { " at $it" } ?: ""}: " +
                    (e.message?.take(120) ?: e.javaClass.simpleName)
            emptyList()
        }
    }

    private fun updateInstalledStatuses(availableExtensions: List<Extension.Available>) {
        val installed = installedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in installed) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }
            if (availableExt == null && !extension.isObsolete) {
                installed[pkgName] = extension.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val hasUpdate = availableExt.versionCode > extension.versionCode ||
                    availableExt.libVersion > extension.libVersion
                if (extension.hasUpdate != hasUpdate || extension.repoUrl != availableExt.repoUrl) {
                    installed[pkgName] = extension.copy(
                        hasUpdate = hasUpdate,
                        repoUrl = availableExt.repoUrl,
                    )
                    changed = true
                }
            }
        }

        if (changed) {
            installedExtensionsMapFlow.value = installed
        }
    }

    // -------------------------------------------------------------------------
    // Install / Update / Remove
    // -------------------------------------------------------------------------

    /**
     * Download and install an extension.
     *
     * The preferred repo format serves pre-converted JVM JARs. These are
     * downloaded directly and loaded without any conversion.
     *
     * Legacy Android APK files are still supported as a fallback, but they
     * require jadx to be installed and are significantly slower/less reliable.
     * If jadx is not available, the install fails with a clear error message.
     */
    @Suppress("DEPRECATION")
    suspend fun installExtension(
        extension: Extension.Available,
        onProgress: ((InstallStep) -> Unit)? = null,
    ) {
        val isPreConvertedJar = extension.apkName.endsWith(".jar", ignoreCase = true)
        val paths = try {
            requireValidAvailableExtension(extension)
            InstallPaths(
                tmpFile = safeExtensionFile(extension.pkgName, ".download.tmp"),
                finalJar = safeExtensionFile(extension.pkgName, ".jar"),
                apkFile = safeExtensionFile(extension.pkgName, ".apk"),
                convertedJar = safeExtensionFile(extension.pkgName, ".converted.jar"),
            )
        } catch (e: Exception) {
            logger.error(e) { "Rejected extension install before download: ${extension.pkgName}" }
            onProgress?.invoke(InstallStep.Error(e.message ?: "Invalid extension artifact"))
            return
        }

        // Pre-converted JAR repos serve files at the root; legacy APK repos use /apk/
        val downloadUrl = if (isPreConvertedJar) {
            "${extension.repoUrl}/${extension.apkName}"
        } else {
            "${extension.repoUrl}/apk/${extension.apkName}"
        }

        val tmpFile = paths.tmpFile
        val finalJar = paths.finalJar
        val apkFile = paths.apkFile
        val convertedJar = paths.convertedJar

        try {
            onProgress?.invoke(InstallStep.Downloading(0f))

            // Never remove the currently installed artifact before the new
            // artifact has downloaded and passed validation.
            apkFile.delete()
            tmpFile.delete()
            convertedJar.delete()

            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()

            val response = networkHelper.client.newCall(request).execute()
            response.use { downloadedResponse ->
                if (!downloadedResponse.isSuccessful) {
                    throw IOException("Download failed: ${downloadedResponse.code} ${downloadedResponse.message}")
                }

                // Download to temp file
                downloadedResponse.body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    val contentLength = downloadedResponse.body!!.contentLength()
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress?.invoke(
                                InstallStep.Downloading(totalRead.toFloat() / contentLength)
                            )
                        }
                    }
                    }
                }
            }

            onProgress?.invoke(InstallStep.Installing)

            if (isPreConvertedJar) {
                validateDownloadedJar(tmpFile, extension)
                replaceAndReload(tmpFile, finalJar, extension.pkgName)
                logger.info { "Installed validated pre-converted JAR: ${extension.pkgName} (${finalJar.length()} bytes)" }
            } else {
                // Legacy APK path — move temp to .apk and attempt conversion
                moveOrCopy(tmpFile, apkFile)

                logger.warn { "Legacy APK extension detected: ${extension.pkgName}. Pre-converted JAR repos are recommended." }

                if (!DexClassLoader.isAvailable()) {
                    throw IOException(
                        "APK extensions require jadx to convert on macOS. " +
                            "Install with: brew install jadx, or switch to a pre-converted JAR repo."
                    )
                }

                logger.info { "Converting APK to JAR: ${extension.pkgName}" }
                val sourceApiJar = findSourceApiJar()
                val commonJvmJar = findCommonJvmJar()
                val success = DexClassLoader.convertToJar(apkFile, convertedJar, sourceApiJar, commonJvmJar)

                if (success && convertedJar.isFile && convertedJar.length() > 0) {
                    validateDownloadedJar(convertedJar, extension)
                    replaceAndReload(convertedJar, finalJar, extension.pkgName)
                    apkFile.delete()
                    logger.info { "Converted and validated ${extension.pkgName} to JAR (${finalJar.length()} bytes)" }
                } else {
                    // Conversion failed — clean up only the new temporary files;
                    // preserve the currently installed JAR.
                    apkFile.delete()
                    convertedJar.delete()
                    logger.warn { "APK conversion failed for ${extension.pkgName}." }
                    throw IOException("APK conversion failed. Try a pre-converted JAR repo instead.")
                }
            }

            onProgress?.invoke(InstallStep.Complete)
            logger.info { "Installed extension: ${extension.pkgName}" }
        } catch (e: Exception) {
            tmpFile.delete()
            apkFile.delete()
            convertedJar.delete()
            logger.error(e) { "Failed to install extension: ${extension.pkgName}" }
            onProgress?.invoke(InstallStep.Error(e.message ?: "Unknown error"))
            // Don't throw — let the UI show the error message gracefully
        }
    }

    /**
     * Move [source] to [destination] atomically if possible, falling back to
     * a copy+delete. Throws [IOException] if the destination does not exist
     * after the operation.
     */
    private fun moveOrCopy(source: File, destination: File) {
        requireSafeExtensionPath(destination)
        require(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Source artifact is not a regular file: ${source.absolutePath}"
        }
        if (source == destination) return
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: JavaIOException) {
            // Atomic move can be unavailable across filesystems. Preserve the
            // destination while copying so a partial copy cannot destroy it.
            logger.warn(e) { "Atomic move failed from ${source.absolutePath} to ${destination.absolutePath}, falling back to rollback-safe copy" }
            val backup = File.createTempFile(".${destination.name}.backup-", ".tmp", destination.parentFile)
            try {
                requireSafeExtensionPath(backup)
                if (destination.isFile) {
                    Files.copy(destination.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    backup.delete()
                }
                try {
                    Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    Files.delete(source.toPath())
                } catch (copyFailure: Exception) {
                    Files.deleteIfExists(destination.toPath())
                    if (backup.isFile) {
                        Files.move(backup.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    throw copyFailure
                }
            } finally {
                backup.delete()
            }
        }

        if (!Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Failed to move ${source.absolutePath} to ${destination.absolutePath}")
        }
    }

    /**
     * Replace an installed artifact only if the replacement can be loaded.
     * The prior artifact remains available for rollback if validation passed but
     * class loading or source construction fails.
     */
    private fun replaceAndReload(source: File, destination: File, pkgName: String) {
        requireSafeExtensionPath(destination)
        val hadExisting = Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS)
        var backup: File? = null
        var backupReady = false

        try {
            if (hadExisting) {
                backup = File.createTempFile(".${destination.name}.previous-", ".tmp", destination.parentFile)
                requireSafeExtensionPath(backup!!)
                Files.copy(destination.toPath(), backup!!.toPath(), StandardCopyOption.REPLACE_EXISTING)
                require(Files.isRegularFile(backup!!.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "Previous extension backup was not created"
                }
                backupReady = true
            }
            moveOrCopy(source, destination)
            val loaded = reloadExtension(pkgName)
            // A first-time install is expected to enter the explicit user-trust
            // state. That proves its metadata/artifact loaded successfully even
            // though no source code is activated yet. Updates to an already
            // trusted install still require Success so a changed, untrusted
            // replacement rolls back to the working version.
            val installedAsUntrusted = !hadExisting && pkgName in untrustedExtensionsMapFlow.value
            if (!loaded && !installedAsUntrusted) {
                throw IOException("Installed extension could not be loaded: $pkgName")
            }
        } catch (failure: Exception) {
            runCatching {
                if (backupReady && backup != null && backup.isFile) {
                    Files.deleteIfExists(destination.toPath())
                    Files.move(backup.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (!reloadExtension(pkgName)) {
                        throw JavaIOException("Previous extension could not be restored: $pkgName")
                    }
                } else if (!hadExisting) {
                    Files.deleteIfExists(destination.toPath())
                    MacOSExtensionLoader.closeClassLoader(pkgName)
                    installedExtensionsMapFlow.value -= pkgName
                    untrustedExtensionsMapFlow.value -= pkgName
                }
            }.getOrElse { rollbackFailure ->
                logger.error(rollbackFailure) { "Failed to restore previous extension artifact: $pkgName" }
                throw JavaIOException(
                    "Extension replacement failed and rollback could not restore $pkgName",
                    rollbackFailure,
                )
            }
            throw failure
        } finally {
            backup?.delete()
        }
    }

    /**
     * Locate [source-api-jvm.jar] needed for APK-to-JAR conversion.
     *
     * Search order:
     * 1. Bundled in the .app Resources (when running from DMG)
     * 2. Project libs/ directory (during development via gradlew run)
     * 3. Current working directory
     */
    private fun findSourceApiJar(): File? {
        val cwd = File(System.getProperty("user.dir", "."))
        val candidates = listOf(
            // Bundled in .app/Contents/Resources/libs/
            File("../Resources/libs/source-api-jvm.jar"),
            File("../lib/libs/source-api-jvm.jar"),
            // Project root (development)
            File(cwd, "macos/libs/source-api-jvm.jar"),
            File(cwd, "libs/source-api-jvm.jar"),
            // Current directory
            File("macos/libs/source-api-jvm.jar"),
            File("libs/source-api-jvm.jar"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    /**
     * Locate [common-jvm.jar] needed for APK-to-JAR conversion.
     */
    private fun findCommonJvmJar(): File? {
        val cwd = File(System.getProperty("user.dir", "."))
        val candidates = listOf(
            File("../Resources/libs/common-jvm.jar"),
            File("../lib/libs/common-jvm.jar"),
            File(cwd, "macos/libs/common-jvm.jar"),
            File(cwd, "libs/common-jvm.jar"),
            File("macos/libs/common-jvm.jar"),
            File("libs/common-jvm.jar"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    /**
     * Update an installed extension.
     */
    suspend fun updateExtension(
        extension: Extension.Installed,
        onProgress: ((InstallStep) -> Unit)? = null,
    ) {
        val availableExt = availableExtensionsMapFlow.value[extension.pkgName]
            ?: throw IllegalStateException("No available update for ${extension.pkgName}")
        installExtension(availableExt, onProgress)
    }

    /**
     * Remove (uninstall) an extension.
     */
    fun removeExtension(extension: Extension) {
        val jarFile = safeExtensionFile(extension.pkgName, ".jar")
        MacOSExtensionLoader.closeClassLoader(extension.pkgName)

        if (Files.isSymbolicLink(jarFile.toPath())) {
            throw IllegalStateException("Refusing to remove a symlinked extension artifact")
        }
        if (jarFile.exists()) {
            jarFile.delete()
            logger.info { "Removed extension: ${extension.pkgName}" }
        }

        installedExtensionsMapFlow.value -= extension.pkgName
        untrustedExtensionsMapFlow.value -= extension.pkgName
    }

    private fun loadTrustStore() {
        if (trustFile.isFile) {
            try {
                val content = trustFile.readText()
                val entries: List<MacOSExtensionLoader.TrustEntry> = json.decodeFromString(content)
                trustStore = entries
                    .groupByTo(mutableMapOf()) { it.pkgName }
                    .mapValues { (_, v) -> v.toMutableList() }
                    .toMutableMap()
                logger.info { "Loaded trust store: ${trustStore.size} packages trusted" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load trust store" }
            }
        }

    }

    // -------------------------------------------------------------------------
    // Trust management
    // -------------------------------------------------------------------------



    private data class InstallPaths(
        val tmpFile: File,
        val finalJar: File,
        val apkFile: File,
        val convertedJar: File,
    )

    private fun validateDownloadedJar(file: File, expected: Extension.Available) {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || file.length() <= 0L) {
            throw IOException("Downloaded extension artifact is empty or incomplete")
        }
        val metadata = MacOSExtensionLoader.readMetadata(file)
            ?: throw IOException("Downloaded extension artifact has invalid metadata")
        if (metadata.pkgName != expected.pkgName ||
            metadata.versionCode != expected.versionCode ||
            metadata.libVersion != expected.libVersion
        ) {
            throw IOException("Downloaded extension metadata does not match repository metadata")
        }
        expected.artifactSha256?.let { expectedHash ->
            val actualHash = MacOSExtensionLoader.computeSha256(file)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                throw IOException("Downloaded extension artifact failed trusted SHA-256 verification")
            }
        }
    }

    private fun requireValidAvailableExtension(extension: Extension.Available) {
        require(PACKAGE_NAME_PATTERN.matches(extension.pkgName)) { "Invalid extension package name" }
        require(extension.apkName.matches(ARTIFACT_NAME_PATTERN)) { "Invalid extension artifact name" }
        require(extension.repoUrl.isAllowedRepository()) { "Extension repository must use HTTPS" }
        require(extension.versionCode >= 0L && extension.versionName.isNotBlank()) { "Invalid extension version metadata" }
        require(extension.libVersion.isFinite()) { "Invalid extension library version" }
    }

    private fun safeExtensionFile(pkgName: String, suffix: String): File {
        require(PACKAGE_NAME_PATTERN.matches(pkgName)) { "Invalid extension package name" }
        val file = File(extensionsDir, "$pkgName$suffix")
        requireSafeExtensionPath(file)
        return file
    }

    private fun requireSafeExtensionPath(file: File) {
        require(!Files.isSymbolicLink(file.toPath())) { "Refusing to use symlinked extension path" }
        val parent = extensionsDir.canonicalFile
        val canonical = file.canonicalFile
        require(canonical.parentFile == parent) { "Extension path escapes its directory" }
    }

    private fun saveTrustStore() {
        try {
            val entries = trustStore.values.flatten()
            trustFile.writeText(
                json.encodeToString(
                    ListSerializer(MacOSExtensionLoader.TrustEntry.serializer()),
                    entries,
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to save trust store" }
        }
    }

    /**
     * Trust an untrusted extension.
     */
    fun trustExtension(extension: Extension.Untrusted) {
        val entry = MacOSExtensionLoader.TrustEntry(
            pkgName = extension.pkgName,
            versionCode = extension.versionCode,
            signatureHash = extension.signatureHash,
        )

        trustStore.getOrPut(extension.pkgName) { mutableListOf() }.add(entry)
        saveTrustStore()

        // Remove from untrusted and reload
        untrustedExtensionsMapFlow.value -= extension.pkgName
        reloadExtension(extension.pkgName)

        logger.info { "Trusted extension: ${extension.pkgName}" }
    }

    /**
     * Check if a package is trusted.
     */
    fun isTrusted(pkgName: String, signatureHash: String): Boolean {
        return trustStore[pkgName]?.any { it.signatureHash == signatureHash } == true
    }

    /**
     * Revoke trust for a package.
     */
    fun revokeTrust(pkgName: String) {
        trustStore.remove(pkgName)
        saveTrustStore()
        // Revocation is an explicit security action: stop the currently loaded
        // code before exposing the artifact as untrusted again.
        MacOSExtensionLoader.closeClassLoader(pkgName)
        installedExtensionsMapFlow.value -= pkgName
        untrustedExtensionsMapFlow.value -= pkgName
        reloadExtension(pkgName)
    }

    // -------------------------------------------------------------------------
    // Source lookup
    // -------------------------------------------------------------------------

    /**
     * Look up a source by its ID across all installed extensions.
     *
     * @param sourceId The source ID to find.
     * @return The anime source if found, null otherwise.
     */
    fun getSource(sourceId: Long): eu.kanade.tachiyomi.animesource.AnimeSource? {
        return installedExtensionsMapFlow.value.values
            .flatMap { it.sources }
            .filterIsInstance<eu.kanade.tachiyomi.animesource.AnimeSource>()
            .find { it.id == sourceId }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    companion object {
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
        private val ARTIFACT_NAME_PATTERN = Regex("[A-Za-z0-9._-]+\\.(?:jar|apk)", RegexOption.IGNORE_CASE)
    }

    /**
     * Shut down the extension manager. Cancels coroutine scope and closes all class loaders.
     */
    override fun close() {
        scope.cancel()
        MacOSExtensionLoader.closeAll()
        logger.info { "Extension manager shut down" }
    }
}

/**
 * CompositionLocal for [MacOSExtensionManager].
 * Provided in AnikkuApp.kt so any screen can access the extension manager
 * without threading it through every constructor.
 */
val LocalExtensionManager = compositionLocalOf<MacOSExtensionManager?> { null }

// ---------------------------------------------------------------------------
// Extension JSON models (matches Android ExtensionApi format)
// ---------------------------------------------------------------------------

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val torrent: Int = 0,
    val sources: List<ExtensionSourceJsonObject>? = null,
    val sha256: String? = null,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private fun ExtensionJsonObject.isValidMetadata(repoUrl: String): Boolean {
    val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
    val artifactPattern = Regex("[A-Za-z0-9._-]+\\.(?:jar|apk)", RegexOption.IGNORE_CASE)
    return packagePattern.matches(pkg) &&
        artifactPattern.matches(apk) &&
        version.isNotBlank() &&
        code >= 0L &&
        (sha256 == null || sha256.matches(Regex("[0-9a-fA-F]{64}"))) &&
        repoUrl.isAllowedRepository()
}

private fun String.isAllowedRepository(): Boolean {
    return runCatching {
        URI(this).let { uri ->
            val host = uri.host
            val localTestHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
            (uri.scheme == "https" || (uri.scheme == "http" && localTestHost)) &&
                !host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }
    }.getOrDefault(false)
}

private fun ExtensionJsonObject.extractLibVersion(): Double {
    return version.substringBeforeLast('.').toDouble()
}

private fun ExtensionJsonObject.toExtension(repoUrl: String): Extension.Available {
    return Extension.Available(
        name = name.substringAfter("Aniyomi: "),
        pkgName = pkg,
        versionName = version,
        versionCode = code,
        libVersion = extractLibVersion(),
        lang = lang,
        isNsfw = nsfw == 1,
        isTorrent = torrent == 1,
        sources = sources?.map { source ->
            Extension.Available.AnimeSource(
                id = source.id,
                lang = source.lang,
                name = source.name,
                baseUrl = source.baseUrl,
            )
        }.orEmpty(),
        apkName = apk,
        iconUrl = "$repoUrl/icon/$pkg.png",
        repoUrl = repoUrl,
        artifactSha256 = sha256,
    )
}

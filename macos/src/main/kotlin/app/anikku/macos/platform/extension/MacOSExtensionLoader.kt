package app.anikku.macos.platform.extension

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipEntry

private val logger = KotlinLogging.logger {}

/**
 * JAR-based extension loader for macOS.
 *
 * Extensions are JAR files stored in an extensions directory.
 * Each JAR must contain a `META-INF/extension.json` file with metadata.
 *
 * Replaces the Android ExtensionLoader which uses PackageManager and PathClassLoader.
 *
 * An in-process extension is not a security sandbox. Trust and archive checks
 * protect the install boundary, but trusted extension code still runs with the
 * application's JVM privileges.
 */
object MacOSExtensionLoader {

    /** Minimum and maximum supported extension lib versions */
    const val LIB_VERSION_MIN = 12
    const val LIB_VERSION_MAX = 15

    private const val EXTENSION_METADATA_PATH = "META-INF/extension.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val packageNamePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
    private val classNamePattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

    /**
     * Tracks active URLClassLoaders by package name for cleanup.
     */
    private val classLoaders = mutableMapOf<String, URLClassLoader>()

    /**
     * Metadata format for extension.json inside each extension JAR.
     */
    @Serializable
    data class ExtensionMetadata(
        val name: String,
        val pkgName: String,
        val versionName: String,
        val versionCode: Long,
        val libVersion: Double,
        val lang: String = "",
        val isNsfw: Boolean = false,
        val isTorrent: Boolean = false,
        val sourceClass: String,
        val pkgFactory: String? = null,
        val hasReadme: Boolean = false,
        val hasChangelog: Boolean = false,
    )

    /**
     * Trust store entry.
     */
    @Serializable
    data class TrustEntry(
        val pkgName: String,
        val versionCode: Long,
        val signatureHash: String,
    )

    /**
     * Load all extensions from the extensions directory.
     *
     * Supports both JAR files (native JVM extensions with META-INF/extension.json)
     * and APK files (legacy keiyoushi Android extensions, automatically converted
     * via [DexClassLoader] when jadx is available).
     *
     * Pre-converted JARs are the preferred format. APK conversion is deprecated
     * and may be removed in a future release.
     */
    fun loadExtensions(
        extensionsDir: File,
        trustStore: Map<String, List<TrustEntry>> = emptyMap(),
        loadNsfw: Boolean = false,
    ): List<LoadResult> {
        if (!extensionsDir.isDirectory) return emptyList()

        val files = extensionsDir.listFiles()?.filter {
            it.isFile && !Files.isSymbolicLink(it.toPath())
        } ?: emptyList()

        // Separate JAR/EXT files from APK files
        val jarFiles = files.filter { it.extension == "jar" || it.extension == "ext" }.toMutableList()
        val apkFiles = files.filter { it.extension == "apk" }

        // Move dependency/base JARs to libs/ BEFORE loading.
        // These JARs contain shared DTOs, extractors, or base classes that other
        // extensions depend on — they aren't standalone extensions with Source classes.
        // Moving them to libs/ lets the URLClassLoader pick them up as shared dependencies
        // without showing them as "untrusted" or "failed to load" extensions.
        moveDependencyJarsToLibs(jarFiles, extensionsDir)

        logger.info { "Loading ${jarFiles.size} JAR extensions, ${apkFiles.size} legacy APK extensions from ${extensionsDir.absolutePath}" }

        val results = mutableListOf<LoadResult>()

        // Resolve metadata before loading any code so duplicate package names can
        // be rejected as a set. Loading whichever duplicate happens to sort first
        // would make the active artifact depend on its filename.
        val orderedJarFiles = jarFiles.sortedBy { it.name }
        val metadataByJar = orderedJarFiles.associateWith(::readMetadata)
        val duplicatePackages = metadataByJar.values
            .filterNotNull()
            .groupingBy { it.pkgName }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        // Load native JVM extensions in a stable order. Reject duplicate package
        // names and source IDs instead of allowing map/source aggregation to
        // silently replace an existing extension.
        val seenPackages = mutableSetOf<String>()
        val seenSourceIds = mutableMapOf<Long, String>()
        for (jarFile in orderedJarFiles) {
            val metadata = metadataByJar[jarFile]
            if (metadata == null) {
                logger.error { "Rejecting invalid extension artifact: ${jarFile.name}" }
                results.add(LoadResult.Error)
                continue
            }
            if (metadata.pkgName in duplicatePackages) {
                logger.error { "Rejecting duplicate extension package ${metadata.pkgName}: ${jarFile.name}" }
                results.add(LoadResult.Error)
                continue
            }
            seenPackages.add(metadata.pkgName)

            when (val result = loadExtension(
                jarFile = jarFile,
                libsDir = extensionsDir,
                trustStore = trustStore,
                loadNsfw = loadNsfw,
                occupiedSourceIds = seenSourceIds,
            )) {
                is LoadResult.Success -> {
                    val duplicateId = result.extension.sources.firstNotNullOfOrNull { source ->
                        seenSourceIds[source.id]?.let { owner -> source.id to owner }
                    }
                    if (duplicateId != null) {
                        logger.error { "Rejecting ${metadata.pkgName}: source ID ${duplicateId.first} already belongs to ${duplicateId.second}" }
                        closeClassLoader(metadata.pkgName)
                        results.add(LoadResult.Error)
                    } else {
                        result.extension.sources.forEach { source -> seenSourceIds[source.id] = metadata.pkgName }
                        results.add(result)
                    }
                }
                else -> results.add(result)
            }
        }

        // Convert and load legacy APK (keiyoushi) extensions
        if (apkFiles.isNotEmpty()) {
            logger.warn { "Legacy APK extensions found: ${apkFiles.map { it.name }}. Pre-converted JAR repos are recommended." }
            convertAndLoadApks(
                apkFiles = apkFiles,
                extensionsDir = extensionsDir,
                results = results,
                trustStore = trustStore,
                loadNsfw = loadNsfw,
                seenPackages = seenPackages,
                seenSourceIds = seenSourceIds,
            )
        }

        return results
    }

    /**
     * Convert legacy keiyoushi APK files to JARs and load them.
     * Uses [DexClassLoader] to decompile DEX bytecode via jadx + javac.
     *
     * **DEPRECATED**: Pre-converted JAR repos are the preferred distribution
     * format. Runtime APK conversion is fragile, slow, and requires users to
     * install jadx. This fallback may be removed in a future release.
     *
     * Limitations:
     * - Requires jadx to be installed (`brew install jadx`)
     * - Obfuscated extensions (R8/ProGuard) produce decompiled code with
     *   meaningless class names — conversion may fail or produce unusable results
     * - Source-api JAR paths are resolved by DexClassLoader internally or
     *   must be provided in the extensions directory's parent structure
     */
    @Suppress("DEPRECATION")
    private fun convertAndLoadApks(
        apkFiles: List<File>,
        extensionsDir: File,
        results: MutableList<LoadResult>,
        trustStore: Map<String, List<TrustEntry>> = emptyMap(),
        loadNsfw: Boolean = false,
        seenPackages: MutableSet<String> = mutableSetOf(),
        seenSourceIds: MutableMap<Long, String> = mutableMapOf(),
    ) {
        if (!DexClassLoader.isAvailable()) {
            logger.warn { "jadx not available — cannot convert ${apkFiles.size} legacy APK extension(s). Install: brew install jadx, or use a pre-converted JAR repo." }
            apkFiles.forEach { _ ->
                results.add(LoadResult.Error)
            }
            return
        }

        for (apkFile in apkFiles) {
            val jarName = apkFile.nameWithoutExtension + ".jar"
            val jarFile = File(extensionsDir, jarName)
            val convertedFile = File(extensionsDir, ".${jarName}.converted.tmp")

            if (Files.isSymbolicLink(jarFile.toPath()) || jarFile.exists() ||
                Files.isSymbolicLink(convertedFile.toPath()) || convertedFile.exists()
            ) {
                logger.error { "Rejecting legacy APK with occupied or unsafe conversion target: ${apkFile.name}" }
                results.add(LoadResult.Error)
                continue
            }

            try {
                logger.warn { "Converting legacy APK extension: ${apkFile.name} → ${jarFile.name}" }
                val success = DexClassLoader.convertToJar(apkFile, convertedFile, null, null)

                if (!success || !Files.isRegularFile(convertedFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw IllegalStateException("APK conversion did not produce a regular JAR")
                }

                logger.info { "Loading converted extension: ${jarFile.name}" }
                val metadata = readMetadata(convertedFile)
                if (metadata == null || !seenPackages.add(metadata.pkgName)) {
                    logger.error { "Rejecting duplicate or invalid converted extension: ${jarFile.name}" }
                    results.add(LoadResult.Error)
                    continue
                }

                try {
                    Files.move(convertedFile.toPath(), jarFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(convertedFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val result = loadExtension(
                    jarFile = jarFile,
                    libsDir = extensionsDir,
                    trustStore = trustStore,
                    loadNsfw = loadNsfw,
                    occupiedSourceIds = seenSourceIds,
                )
                if (result is LoadResult.Success) {
                    result.extension.sources.forEach { source -> seenSourceIds[source.id] = metadata.pkgName }
                }
                results.add(result)
            } catch (e: Exception) {
                logger.error(e) { "Failed to convert legacy APK extension: ${apkFile.name}" }
                results.add(LoadResult.Error)
            } finally {
                convertedFile.delete()
            }
        }
    }

    /**
     * Load a single extension JAR.
     *
     * Trust model (security-critical):
     * - Trust is always verified if trustStore is non-empty
     * - If trustStore is empty (first run), ALL extensions are untrusted
     * - Extensions must be explicitly trusted via trustExtension() before loading
     */
    fun loadExtension(
        jarFile: File,
        libsDir: File? = null,
        trustStore: Map<String, List<TrustEntry>> = emptyMap(),
        loadNsfw: Boolean = false,
        occupiedSourceIds: Map<Long, String> = emptyMap(),
    ): LoadResult {
        val metadata = readMetadata(jarFile) ?: return LoadResult.Error

        val pkgName = metadata.pkgName

        // Validate lib version
        if (metadata.libVersion < LIB_VERSION_MIN || metadata.libVersion > LIB_VERSION_MAX) {
            logger.warn { "Lib version ${metadata.libVersion} for $pkgName outside supported range [$LIB_VERSION_MIN, $LIB_VERSION_MAX]" }
            return LoadResult.Error
        }

        // Compute JAR hash for trust verification
        val signatureHash = computeSha256(jarFile)

        // Always verify trust. If trustStore is empty, nothing is trusted.
        val trustedEntries = trustStore[pkgName]
        if (trustedEntries == null || trustedEntries.none { it.signatureHash == signatureHash }) {
            logger.warn { "Extension $pkgName is untrusted (hash: $signatureHash)" }
            return LoadResult.Untrusted(
                Extension.Untrusted(
                    name = metadata.name,
                    pkgName = metadata.pkgName,
                    versionName = metadata.versionName,
                    versionCode = metadata.versionCode,
                    libVersion = metadata.libVersion,
                    signatureHash = signatureHash,
                    lang = metadata.lang,
                    isNsfw = metadata.isNsfw,
                    isTorrent = metadata.isTorrent,
                )
            )
        }

        // Check NSFW
        if (metadata.isNsfw && !loadNsfw) {
            logger.warn { "NSFW extension $pkgName blocked by user preference" }
            return LoadResult.Error
        }

        // Build the replacement classloader first. The previous loader remains
        // usable until the new artifact has been validated and instantiated.
        val classLoader = try {
            buildClassLoader(jarFile, libsDir)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create classloader for $pkgName" }
            return LoadResult.Error
        }

        // Load sources (resilient: individual class failures skip, not fail the extension)
        val sources = try {
            loadSources(classLoader, metadata)
        } catch (e: LinkageError) {
            logger.error(e) { "Linkage failure loading sources from $pkgName" }
            classLoader.close()
            return LoadResult.Error
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error loading sources from $pkgName" }
            classLoader.close()
            return LoadResult.Error
        }

        if (sources.isEmpty()) {
            logger.warn { "No valid sources found in $pkgName — all listed classes failed to instantiate" }
            classLoader.close()
            return LoadResult.Error
        }

        val duplicateSource = sources
            .groupBy { it.id }
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.let { it.key to "${metadata.pkgName} (duplicate within artifact)" }
            ?: sources.firstNotNullOfOrNull { source ->
                occupiedSourceIds[source.id]?.let { owner -> source.id to owner }
            }
        if (duplicateSource != null) {
            logger.error { "Rejecting $pkgName: source ID ${duplicateSource.first} already belongs to ${duplicateSource.second}" }
            classLoader.close()
            return LoadResult.Error
        }

        // Swap only after successful construction, then release the old loader.
        classLoaders.put(pkgName, classLoader)?.close()

        logger.info { "Loaded ${sources.size} source(s) from $pkgName" }

        // Build lang from sources
        val sourceLangs = sources
            .filterIsInstance<eu.kanade.tachiyomi.source.CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (sourceLangs.size) {
            0 -> metadata.lang
            1 -> sourceLangs.first()
            else -> "all"
        }

        return LoadResult.Success(
            Extension.Installed(
                name = metadata.name,
                pkgName = metadata.pkgName,
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                libVersion = metadata.libVersion,
                lang = lang,
                isNsfw = metadata.isNsfw,
                isTorrent = metadata.isTorrent,
                sources = sources,
                pkgFactory = metadata.pkgFactory,
                icon = null,
                hasUpdate = false,
                isObsolete = false,
                isShared = false,
            )
        )
    }

    /**
     * Read extension metadata from the JAR's META-INF/extension.json.
     */
    fun readMetadata(jarFile: File): ExtensionMetadata? {
        return try {
            requireSafeArtifactFile(jarFile)
            validateArchive(jarFile)
            JarFile(jarFile).use { jar ->
                val entry: ZipEntry = jar.getEntry(EXTENSION_METADATA_PATH)
                    ?: return logAndNull("No $EXTENSION_METADATA_PATH in ${jarFile.name}")

                val content = jar.getInputStream(entry).use { stream: InputStream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }

                val metadata = json.decodeFromString<ExtensionMetadata>(content)
                validateMetadata(metadata)
                metadata
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to read metadata from ${jarFile.name}" }
            null
        }
    }

    /**
     * Validate every archive entry before the artifact is trusted or loaded.
     * The current loader does not extract JAR contents, but rejecting unsafe
     * archives here protects future extraction paths and prevents a malformed
     * artifact from crossing the install/load boundary.
     */
    internal fun validateArchive(jarFile: File) {
        requireSafeArtifactFile(jarFile)
        val unixSymlinkEntries = findUnixSymlinkEntries(jarFile)
        require(unixSymlinkEntries.isEmpty()) {
            "Archive contains symlink entries: ${unixSymlinkEntries.joinToString(limit = 3)}"
        }
        JarFile(jarFile).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                validateArchiveEntryPath(entry.name)
            }
        }
    }

    private fun validateArchiveEntryPath(entryName: String) {
        val normalizedSeparators = entryName.replace('\\', '/')
        val path = java.nio.file.Paths.get(normalizedSeparators)
        if (entryName.isBlank() || normalizedSeparators.startsWith("/") ||
            normalizedSeparators.matches(Regex("^[A-Za-z]:/.*")) || path.isAbsolute ||
            normalizedSeparators.split('/').any { it == ".." } ||
            path.normalize().startsWith(java.nio.file.Paths.get(".."))
        ) {
            throw IllegalArgumentException("Unsafe archive entry path: $entryName")
        }
    }

    private fun validateMetadata(metadata: ExtensionMetadata) {
        require(packageNamePattern.matches(metadata.pkgName)) { "Invalid extension package name" }
        require(metadata.name.isNotBlank() && metadata.versionName.isNotBlank()) { "Missing extension identity metadata" }
        require(metadata.versionCode >= 0L && metadata.libVersion.isFinite()) { "Invalid extension version metadata" }
        require(metadata.sourceClass.isNotBlank() && metadata.sourceClass.split(';').all { className ->
            val trimmed = className.trim()
            classNamePattern.matches(trimmed) && trimmed.startsWith("${metadata.pkgName}.")
        }) {
            "Invalid extension source class metadata"
        }
    }

    private fun requireSafeArtifactFile(file: File) {
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Extension artifact is not a regular file: ${file.absolutePath}"
        }
    }

    /**
     * ZipEntry does not expose Unix external attributes on Java 17. Read the
     * central directory record to reject entries whose Unix mode is symlink.
     */
    private fun findUnixSymlinkEntries(jarFile: File): Set<String> {
        val symlinkEntries = mutableSetOf<String>()
        RandomAccessFile(jarFile, "r").use { file ->
            val length = file.length()
            val endOfCentralDirectory = findEndOfCentralDirectory(file)
            file.seek(endOfCentralDirectory + 4)
            val diskNumber = readLittleEndianShort(file)
            val centralDirectoryDisk = readLittleEndianShort(file)
            val entriesOnDisk = readLittleEndianShort(file)
            val entryCount = readLittleEndianShort(file)
            val centralDirectorySize = readLittleEndianInt(file).toLong() and 0xffffffffL
            val centralDirectoryOffset = readLittleEndianInt(file).toLong() and 0xffffffffL

            require(diskNumber == 0 && centralDirectoryDisk == 0 && entriesOnDisk == entryCount) {
                "Multi-disk archives are not supported"
            }
            require(entryCount != 0xffff && centralDirectorySize != 0xffffffffL &&
                centralDirectoryOffset != 0xffffffffL
            ) {
                "ZIP64 extension archives are not supported"
            }
            val centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize
            require(centralDirectoryOffset >= 0 && centralDirectoryEnd <= endOfCentralDirectory &&
                centralDirectoryEnd <= length
            ) {
                "Invalid central directory bounds"
            }

            file.seek(centralDirectoryOffset)
            repeat(entryCount) {
                val headerOffset = file.filePointer
                require(readLittleEndianInt(file) == 0x02014b50) {
                    "Invalid central directory entry"
                }
                val madeBy = readLittleEndianShort(file)
                file.skipBytes(22)
                val nameLength = readLittleEndianShort(file)
                val extraLength = readLittleEndianShort(file)
                val commentLength = readLittleEndianShort(file)
                file.skipBytes(4)
                val externalAttributes = readLittleEndianInt(file).toLong() and 0xffffffffL
                file.skipBytes(4)
                val name = ByteArray(nameLength)
                file.readFully(name)
                val unixMode = (externalAttributes ushr 16) and 0xffff
                if (((madeBy ushr 8) and 0xff) == 3 && unixMode and 0xf000L == 0xa000L &&
                    name.isNotEmpty()
                ) {
                    symlinkEntries += name.toString(Charsets.UTF_8)
                }
                val nextHeader = headerOffset + 46L + nameLength + extraLength + commentLength
                require(nextHeader <= centralDirectoryEnd) { "Invalid central directory entry bounds" }
                file.seek(nextHeader)
            }
        }
        return symlinkEntries
    }

    private fun findEndOfCentralDirectory(file: RandomAccessFile): Long {
        val length = file.length()
        require(length >= 22) { "Archive is too small" }
        val searchLength = minOf(length, 22L + 0xffffL).toInt()
        val searchOffset = length - searchLength
        val tail = ByteArray(searchLength)
        file.seek(searchOffset)
        file.readFully(tail)

        for (index in tail.size - 22 downTo 0) {
            val signature = (tail[index].toInt() and 0xff) or
                ((tail[index + 1].toInt() and 0xff) shl 8) or
                ((tail[index + 2].toInt() and 0xff) shl 16) or
                ((tail[index + 3].toInt() and 0xff) shl 24)
            if (signature != 0x06054b50) continue
            val commentLength = (tail[index + 20].toInt() and 0xff) or
                ((tail[index + 21].toInt() and 0xff) shl 8)
            if (index + 22 + commentLength == tail.size) {
                return searchOffset + index
            }
        }
        throw IllegalArgumentException("Archive has no valid end-of-central-directory record")
    }

    private fun readLittleEndianShort(file: RandomAccessFile): Int {
        return file.readUnsignedByte() or (file.readUnsignedByte() shl 8)
    }

    private fun readLittleEndianInt(file: RandomAccessFile): Int {
        return readLittleEndianShort(file) or (readLittleEndianShort(file) shl 16)
    }

    /**
     * Move dependency JARs to libs/ so they are available as shared dependencies
     * rather than treated as standalone extensions that show as "untrusted".
     *
     * Dependency JARs have specific package name patterns:
     * - *.dto packages: shared data transfer objects
     * - *.extractors packages: extractor implementations used by other extensions
     * - Specific base packages: watchanimeworld, reanime, dflixbackup, anitusk
     *   contain shared classes but no standalone Source implementations
     */
    private fun moveDependencyJarsToLibs(
        jarFiles: MutableList<File>,
        extensionsDir: File,
    ) {
        logger.info { "🔍 Scanning ${jarFiles.size} JAR(s) for dependency packages..." }
        val libsDir = File(extensionsDir, "libs")
        if (Files.isSymbolicLink(libsDir.toPath()) ||
            (!libsDir.exists() && !libsDir.mkdirs()) ||
            libsDir.canonicalFile.parentFile != extensionsDir.canonicalFile
        ) {
            logger.error { "Skipping dependency relocation: unsafe extension libs directory ${libsDir.absolutePath}" }
            return
        }
        val iterator = jarFiles.iterator()
        var movedCount = 0
        var scannedCount = 0

        while (iterator.hasNext()) {
            val jarFile = iterator.next()
            val metadata = readMetadata(jarFile)
            if (metadata == null) {
                logger.debug { "  No metadata for ${jarFile.name} — skipping" }
                continue
            }
            scannedCount++
            val pkg = metadata.pkgName

            val isDependency = pkg.endsWith(".dto") ||
                pkg.endsWith(".extractors") ||
                pkg.contains("watchanimeworld") ||
                pkg.contains("reanime") ||
                pkg.contains("dflixbackup") ||
                pkg.contains("anitusk")

            if (isDependency) {
                val target = File(libsDir, jarFile.name)
                if (Files.isSymbolicLink(target.toPath()) ||
                    target.exists() ||
                    target.canonicalFile.parentFile != libsDir.canonicalFile
                ) {
                    logger.error { "Skipping unsafe or occupied dependency target: ${target.absolutePath}" }
                    continue
                }

                try {
                    if (!jarFile.renameTo(target)) {
                        jarFile.copyTo(target, overwrite = true)
                        if (!jarFile.delete()) {
                            throw IllegalStateException("Could not remove original dependency after copy")
                        }
                    }

                    iterator.remove()
                    movedCount++
                    logger.info { "  ✅ Moved dependency JAR to libs/: ${jarFile.name} ($pkg)" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to relocate dependency JAR ${jarFile.name}; leaving it in place" }
                }
            }
        }

        logger.info { "🔍 Dependency scan done: $scannedCount scanned, $movedCount moved to libs/" }
    }

    /**
     * Build a URLClassLoader for the extension JAR and its dependencies.
     *
     * Shared dependency JARs (source-api, common, kotlin-stdlib, etc.) are added
     * DIRECTLY to the URLClassLoader's URLs so extensions can find them regardless
     * of class loader parent delegation behavior. This is the most robust approach
     * because:
     *
     * 1. The parent class loader may not expose all app dependencies (Gradle's
     *    class loader hierarchy is complex and varies by launch method).
     * 2. Android extensions use `compileOnly` for these libraries — they expect
     *    the host to provide them at runtime.
     * 3. Adding JARs directly to the URLClassLoader URLs bypasses all delegation
     *    issues and guarantees the classes are available.
     */
    private fun buildClassLoader(jarFile: File, libsDir: File?): URLClassLoader {
        val urls = mutableListOf(jarFile.toURI().toURL())

        // 1. Scan the extension's own libs/ directory for dependency JARs
        val depsDir = libsDir?.let { File(it, "libs") } ?: File(jarFile.parentFile, "libs")
        if (depsDir.isDirectory && !Files.isSymbolicLink(depsDir.toPath())) {
            depsDir.listFiles()
                ?.filter { it.extension == "jar" && !Files.isSymbolicLink(it.toPath()) }
                ?.forEach { urls.add(it.toURI().toURL()) }
        }

        // 2. Add shared dependency JARs from the macOS app's libs/ directory.
        //    These are built by the `rebuildSourceApiJars` Gradle task and
        //    contain the source-api and core/common classes that extensions
        //    reference via `compileOnly` (ConfigurableAnimeSource, AnimeSource, etc.).
        val sharedLibsDir = findSharedLibsDir()
        if (sharedLibsDir.isDirectory && !Files.isSymbolicLink(sharedLibsDir.toPath())) {
            sharedLibsDir.listFiles()
                ?.filter { it.extension == "jar" && !Files.isSymbolicLink(it.toPath()) }
                ?.forEach { urls.add(it.toURI().toURL()) }
        }

        // 3. Also scan extensions/libs/ for globally shared dependency JARs
        val globalLibsDir = File(jarFile.parentFile, "libs")
        if (globalLibsDir.isDirectory && !Files.isSymbolicLink(globalLibsDir.toPath()) && globalLibsDir != depsDir) {
            globalLibsDir.listFiles()
                ?.filter { it.extension == "jar" && !Files.isSymbolicLink(it.toPath()) }
                ?.forEach { urls.add(it.toURI().toURL()) }
        }

        return URLClassLoader(
            urls.toTypedArray(),
            MacOSExtensionLoader::class.java.classLoader,
        )
    }

    /**
     * Find the directory containing shared library JARs (source-api-jvm.jar, common-jvm.jar, etc.).
     *
     * Search order:
     * 1. `macos/libs/` relative to working directory (development via gradlew run)
     * 2. `../libs/` relative to app bundle Contents (packaged .app)
     * 3. `libs/` relative to working directory (any other setup)
     */
    private fun findSharedLibsDir(): File {
        val cwd = File(System.getProperty("user.dir", "."))

        val candidates = listOf(
            File(cwd, "macos/libs"),
            File(cwd, "libs"),
            File("../Resources/libs"),
            File("../lib/libs"),
        )

        return candidates.firstOrNull { it.isDirectory } ?: File(cwd, "libs")
    }

    /**
     * Load source instances from the extension JAR.
     */
    private fun loadSources(
        classLoader: URLClassLoader,
        metadata: ExtensionMetadata,
    ): List<eu.kanade.tachiyomi.source.Source> {
        val sourceClassNames = metadata.sourceClass
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sourceClassNames.isEmpty()) {
            throw IllegalStateException("No source classes defined for ${metadata.pkgName}")
        }

        return sourceClassNames.flatMap { className ->                try {
                    val fullClassName = if (className.startsWith(".")) {
                        metadata.pkgName + className
                    } else {
                        className
                    }

                    // Use false (don't initialize) to avoid resolving android.* class references
                    // that don't exist on JVM. The class will be initialized lazily when first used.
                    // Keep NoClassDefFoundError catch below as an additional safety net.
                    val clazz = Class.forName(fullClassName, false, classLoader)
                    // BUGFIX: getDeclaredConstructor() returns private constructors too,
                    // but newInstance() throws IllegalAccessException for private ones.
                    // We must call setAccessible(true) first. Many extension source classes
                    // (e.g. AnilistGraphQLResponse, AnimePahe, DhakaFlix2) have private
                    // no-arg constructors.
                    val constructor = clazz.getDeclaredConstructor()
                    constructor.isAccessible = true
                    val instance = constructor.newInstance()

                    when (instance) {
                        is eu.kanade.tachiyomi.source.Source -> {
                            logger.info { "Loaded Source directly: $fullClassName" }
                            listOf(instance)
                        }
                        is eu.kanade.tachiyomi.source.SourceFactory -> {
                            logger.info { "Loaded SourceFactory: $fullClassName — creating sources..." }
                            instance.createSources()
                        }
                        else -> {
                            if (instance is AnimeSource) {
                                logger.warn { "Class $fullClassName implements AnimeSource but not Source. Wrapping via SourceAdapter." }
                                listOf(SourceAdapter(instance))
                            } else {
                                // Try reflection-based wrapping for real extension JARs
                                val wrapped = wrapAsSource(instance)
                                if (wrapped != null) {
                                    logger.info { "Wrapped ${instance.javaClass.name} via reflection as CatalogueSource" }
                                    listOf(wrapped)
                                } else {
                                    throw IllegalStateException(
                                        "Unknown source class type for $fullClassName: ${instance.javaClass.name}"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: LinkageError) {
                    logger.warn { "Skipping $className — linkage failure: ${e.message}" }
                    emptyList()
                } catch (e: IllegalAccessException) {
                    logger.warn { "Skipping $className — cannot access constructor (private/protected): ${e.message}" }
                    emptyList()
                } catch (e: NoSuchMethodException) {
                    logger.warn { "Skipping $className — no no-arg constructor found: ${e.message}" }
                    emptyList()
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    val cause = e.cause ?: e
                    logger.warn(cause) { "Skipping $className — constructor threw ${cause::class.simpleName}: ${cause.message}" }
                    emptyList()
                } catch (e: Exception) {
                    logger.warn(e) { "Skipping $className — instantiation failed: ${e::class.simpleName}: ${e.message}" }
                    emptyList()
                }
        }
    }

    /**
     * Compute SHA-256 hash of a file.
     */
    fun computeSha256(file: File): String {
        requireSafeArtifactFile(file)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Close the class loader for a specific package.
     */
    fun closeClassLoader(pkgName: String) {
        classLoaders.remove(pkgName)?.close()
    }

    /**
     * Close all tracked class loaders.
     */
    fun closeAll() {
        classLoaders.values.forEach { it.close() }
        classLoaders.clear()
    }

    private fun logAndNull(message: String): Nothing? {
        logger.warn { message }
        return null
    }

    /**
     * Adapter that wraps an AnimeSource as a Source for compatibility.
     */
    private class SourceAdapter(
        private val delegate: AnimeSource,
    ) : eu.kanade.tachiyomi.source.Source {
        override val id: Long get() = delegate.id
        override val name: String get() = delegate.name
        override val lang: String get() = delegate.lang

        override suspend fun getAnimeDetails(anime: eu.kanade.tachiyomi.animesource.model.SAnime) =
            delegate.getAnimeDetails(anime)

        override suspend fun getEpisodeList(anime: eu.kanade.tachiyomi.animesource.model.SAnime) =
            delegate.getEpisodeList(anime)

        override suspend fun getVideoList(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) =
            delegate.getVideoList(episode)
    }
}

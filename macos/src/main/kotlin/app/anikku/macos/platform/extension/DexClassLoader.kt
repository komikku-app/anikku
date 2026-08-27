package app.anikku.macos.platform.extension

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URLClassLoader

private val logger = KotlinLogging.logger {}

/**
 * Loads classes from Android DEX/APK files by converting them to JVM bytecode
 * at build time using jadx (decompiler) and javac (compiler).
 *
 * ## Architecture
 *
 * DEX bytecode (from keiyoushi APKs) is fundamentally different from JVM bytecode
 * and cannot be loaded directly by the JVM's [URLClassLoader]. This loader works
 * around that limitation by:
 *
 * 1. Spawning `jadx` as a subprocess to decompile the DEX to Java source code
 * 2. Spawning `javac` (from JDK 17) to compile the Java source to JVM .class files
 * 3. Loading the resulting .class files via a standard [URLClassLoader]
 *
 * ## Limitations
 *
 * - **Obfuscated extensions**: Keiyoushi extensions are minified with R8/ProGuard,
 *   producing decompiled code with meaningless class names. The compiled result
 *   may have class loading issues or runtime errors.
 * - **Android API references**: Decompiled code may reference `android.*` APIs
 *   that don't exist on the JVM. This loader creates minimal stub implementations
 *   to satisfy compilation, but runtime behavior may differ.
 * - **Performance**: Runtime conversion is slow (~5-15 seconds per extension).
 *   For production, convert extensions ahead of time.
 * - **Requires jadx**: The `jadx` CLI tool must be installed (`brew install jadx`).
 *
 * ## Recommended approach
 *
 * For the best experience, extension developers should compile their extensions
 * from source as JVM bytecode. See `macos/docs/MIGRATION-GUIDE.md` for details.
 * This class loader is a fallback for loading existing keiyoushi APKs.
 */
@Deprecated(
    message = "Runtime APK conversion is deprecated. Use pre-converted JAR repos or build from source instead.",
    replaceWith = ReplaceWith("Build extensions from source or use a pre-converted JAR repo"),
)
object DexClassLoader {

    private const val JADX_CMD = "jadx"
    private const val MIN_JADX_VERSION = "1.5.0"

    /** The JDK's javac executable path. */
    private val javacPath: String by lazy {
        // Try JAVA_HOME first, then Homebrew symlinks, then other known paths
        // IMPORTANT: we verify javac actually works by running --version
        val javaHome = System.getenv("JAVA_HOME")
        val candidates = mutableListOf<String>()

        if (javaHome != null) {
            candidates.add(File(javaHome, "bin/javac").absolutePath)
        }

        // Homebrew JDK symlinks (preferred — always point to active version)
        candidates.addAll(
            listOf(
                "/opt/homebrew/opt/openjdk@17/bin/javac",
                "/opt/homebrew/opt/openjdk@21/bin/javac",
                "/opt/homebrew/opt/openjdk/bin/javac",
                "/usr/local/opt/openjdk@17/bin/javac",
                "/usr/local/opt/openjdk/bin/javac",
            )
        )

        // Cellar version-specific paths (fallback)
        candidates.addAll(
            listOf(
                "/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home/bin/javac",
                "/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/javac",
            )
        )

        // macOS system javac (often a stub that fails)
        candidates.add("/usr/bin/javac")

        // Try each candidate — verify it actually works
        for (path in candidates) {
            if (File(path).isFile) {
                try {
                    val proc = ProcessBuilder(path, "--version")
                        .redirectErrorStream(true)
                        .start()
                    val output = proc.inputStream.reader().readText()
                    proc.waitFor()
                    if (proc.exitValue() == 0 && output.isNotBlank()) {
                        return@lazy path
                    }
                } catch (_: Exception) {
                    // Candidate failed, try next
                }
            }
        }

        // Last resort: hope javac is on PATH
        "javac"
    }

    /**
     * Check if jadx is available on this system.
     */
    fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(JADX_CMD, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.reader().readText().trim()
            process.waitFor() == 0 && output.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert an APK file to a loadable JAR.
     *
     * This method:
     * 1. Verifies that jadx is installed
     * 2. Decompiles the APK to Java source using jadx
     * 3. Creates Android API stubs
     * 4. Compiles the Java source with javac against source-api JARs
     * 5. Generates META-INF/extension.json from the decompiled output
     * 6. Packages as a JAR in the same directory as the APK
     *
     * @param apkFile The keiyoushi extension APK file
     * @param outputJar The output JAR path
     * @param sourceApiJar Path to source-api-jvm.jar
     * @param commonJvmJar Path to common-jvm.jar
     * @return true if conversion succeeded
     */
    fun convertToJar(
        apkFile: File,
        outputJar: File,
        sourceApiJar: File? = null,
        commonJvmJar: File? = null,
    ): Boolean {
        if (!apkFile.isFile) {
            logger.error { "APK file not found: ${apkFile.absolutePath}" }
            return false
        }

        if (!isAvailable()) {
            logger.error { "jadx is not installed. Install with: brew install jadx" }
            return false
        }

        val tempDir = File(System.getProperty("java.io.tmpdir"), "anikku-dex-${apkFile.nameWithoutExtension}")
        tempDir.mkdirs()

        try {
            // Step 1: Decompile with jadx
            logger.info { "Decompiling ${apkFile.name} with jadx..." }
            val sourcesDir = File(tempDir, "sources")
            val jadxResult = runCommand(
                JADX_CMD,
                "-d", sourcesDir.absolutePath,
                apkFile.absolutePath,
            )

            if (jadxResult.exitCode != 0) {
                logger.error { "jadx decompilation failed:\n${jadxResult.stderr}" }
                return false
            }

            val javaFiles = sourcesDir.walkTopDown()
                .filter { it.extension == "java" }
                .toList()

            if (javaFiles.isEmpty()) {
                logger.error { "jadx produced no Java source files" }
                return false
            }

            logger.info { "Decompiled ${javaFiles.size} Java source files" }

            // Step 2: Create Android API stubs
            val stubsDir = createAndroidStubs(tempDir)

            // Step 3: Compile with javac
            logger.info { "Compiling with javac..." }
            val classFilesDir = File(tempDir, "classes")
            classFilesDir.mkdirs()

            val classpath = buildString {
                append(stubsDir.absolutePath)
                if (sourceApiJar != null && sourceApiJar.isFile) {
                    append(File.pathSeparator).append(sourceApiJar.absolutePath)
                }
                if (commonJvmJar != null && commonJvmJar.isFile) {
                    append(File.pathSeparator).append(commonJvmJar.absolutePath)
                }
            }

            // Write source file list
            val sourceListFile = File(tempDir, "sources.txt")
            sourceListFile.writeText(javaFiles.joinToString("\n") { it.absolutePath })

            val javacResult = runCommand(
                javacPath,
                "-d", classFilesDir.absolutePath,
                "-cp", classpath,
                "-source", "17",
                "-target", "17",
                "-proc:none", // Disable annotation processing (avoids errors)
                "-Xlint:-options",
                "-implicit:none",
                "@${sourceListFile.absolutePath}",
            )

            if (javacResult.exitCode != 0) {
                logger.warn { "javac compilation had issues:\n${javacResult.stderr.take(500)}" }
                // Continue anyway if we got some class files
            }

            val classFiles = classFilesDir.walkTopDown()
                .filter { it.extension == "class" }
                .toList()

            if (classFiles.isEmpty()) {
                logger.error { "Compilation produced no .class files" }
                return false
            }

            logger.info { "Compilation produced ${classFiles.size} .class files" }

            // Step 4: Generate extension.json from APK metadata
            val extensionJson = generateExtensionJson(apkFile, javaFiles, classFilesDir)

            // Step 5: Package as JAR
            classFilesDir.resolve("META-INF").mkdirs()
            classFilesDir.resolve("META-INF/extension.json").writeText(extensionJson)

            val jarProcess = ProcessBuilder(
                "jar", "cf", outputJar.absolutePath,
                "-C", classFilesDir.absolutePath, ".",
            )
                .redirectErrorStream(true)
                .start()
            jarProcess.waitFor()

            if (!outputJar.isFile) {
                logger.error { "Failed to create JAR at ${outputJar.absolutePath}" }
                return false
            }

            logger.info { "Converted ${apkFile.name} to ${outputJar.name} (${classFiles.size} classes)" }
            return true
        } catch (e: Exception) {
            logger.error(e) { "DEX conversion failed for ${apkFile.name}" }
            return false
        } finally {
            // Clean up temp files
            tempDir.deleteRecursively()
        }
    }

    /**
     * Create Android API stub classes for compilation.
     */
    private fun createAndroidStubs(baseDir: File): File {
        val stubsDir = File(baseDir, "stubs")
        if (stubsDir.isDirectory) return stubsDir

        // Create stub source files
        val stubs = mapOf(
            "android/util/Log.java" to """
                package android.util;
                public class Log {
                    public static int v(String t, String m) { return 0; }
                    public static int d(String t, String m) { return 0; }
                    public static int i(String t, String m) { return 0; }
                    public static int w(String t, String m) { return 0; }
                    public static int e(String t, String m) { return 0; }
                    public static int v(String t, String m, Throwable tr) { return 0; }
                    public static int d(String t, String m, Throwable tr) { return 0; }
                    public static int i(String t, String m, Throwable tr) { return 0; }
                    public static int w(String t, String m, Throwable tr) { return 0; }
                    public static int e(String t, String m, Throwable tr) { return 0; }
                    public static String getStackTraceString(Throwable t) { return ""; }
                }
            """.trimIndent(),
            "android/os/Build.java" to """
                package android.os;
                public class Build {
                    public static class VERSION {
                        public static final String RELEASE = "15";
                        public static final int SDK_INT = 35;
                    }
                    public static final String BRAND = "generic";
                    public static final String MODEL = "Anikku";
                    public static final String MANUFACTURER = "unknown";
                }
            """.trimIndent(),
            "android/text/TextUtils.java" to """
                package android.text;
                public class TextUtils {
                    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
                    public static String join(CharSequence d, Iterable<?> t) {
                        StringBuilder sb = new StringBuilder();
                        for (Object o : t) { if (sb.length() > 0) sb.append(d); sb.append(o); }
                        return sb.toString();
                    }
                }
            """.trimIndent(),
            "android/net/Uri.java" to """
                package android.net;
                public class Uri {
                    private final String s;
                    private Uri(String s) { this.s = s; }
                    public static Uri parse(String s) { return new Uri(s); }
                    public static final Uri EMPTY = new Uri("");
                    public String getScheme() { return s.contains(":") ? s.substring(0, s.indexOf(':')) : ""; }
                    public String toString() { return s; }
                    public String getPath() { try { return new java.net.URL(s).getPath(); } catch (Exception e) { return ""; } }
                    public String getQuery() { try { return new java.net.URL(s).getQuery(); } catch (Exception e) { return ""; } }
                    public String getHost() { try { return new java.net.URL(s).getHost(); } catch (Exception e) { return ""; } }
                }
            """.trimIndent(),
            "android/content/Context.java" to """
                package android.content;
                public class Context {
                    public static final int MODE_PRIVATE = 0;
                    public SharedPreferences getSharedPreferences(String n, int m) { return new SharedPreferences(); }
                }
            """.trimIndent(),
            "android/content/SharedPreferences.java" to """
                package android.content;
                import java.util.*;
                public class SharedPreferences {
                    public String getString(String k, String d) { return d; }
                    public int getInt(String k, int d) { return d; }
                    public long getLong(String k, long d) { return d; }
                    public boolean getBoolean(String k, boolean d) { return d; }
                    public float getFloat(String k, float d) { return d; }
                    public boolean contains(String k) { return false; }
                    public Map<String, ?> getAll() { return new HashMap<>(); }
                    public Editor edit() { return null; }
                    public interface Editor {
                        Editor putString(String k, String v); Editor putInt(String k, int v);
                        Editor putLong(String k, long v); Editor putFloat(String k, float v);
                        Editor putBoolean(String k, boolean v); Editor remove(String k);
                        Editor clear(); boolean commit(); void apply();
                    }
                }
            """.trimIndent(),
        )

        for ((path, source) in stubs) {
            val file = File(stubsDir, path)
            file.parentFile.mkdirs()
            file.writeText(source)
        }

        // Compile stubs
        val classesDir = File(baseDir, "stubs-classes")
        classesDir.mkdirs()

        val stubFiles = stubs.keys.map { File(stubsDir, it) }
        val sourceList = stubFiles.joinToString("\n") { it.absolutePath }
        File(baseDir, "stubs-sources.txt").writeText(sourceList)

        runCommand(
            javacPath,
            "-d", classesDir.absolutePath,
            "-source", "17",
            "-target", "17",
            "-proc:none",
            "@${File(baseDir, "stubs-sources.txt").absolutePath}",
        )

        return classesDir
    }

    /**
     * Generate extension.json metadata from an APK file and decompiled sources.
     */
    private fun generateExtensionJson(
        apkFile: File,
        javaFiles: List<File>,
        classFilesDir: File,
    ): String {
        // Extract package name from APK (from AndroidManifest.xml)
        val pkgName = try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                if (entry != null) {
                    val data = zip.getInputStream(entry).readAllBytes()
                    // Binary XML — extract package= as a regex on the decoded bytes
                    val text = data.decodeToString()
                    val regex = Regex("""package="([^"]+)"""")
                    regex.find(text)?.groupValues?.getOrNull(1) ?: "unknown"
                } else "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }

        // Extract version info
        val versionName = try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                if (entry != null) {
                    val text = zip.getInputStream(entry).readAllBytes().decodeToString()
                    Regex("""versionName="([^"]+)"""").find(text)?.groupValues?.getOrNull(1) ?: "1.0.0"
                } else "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }

        val versionCode = try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                if (entry != null) {
                    val text = zip.getInputStream(entry).readAllBytes().decodeToString()
                    Regex("""versionCode="?(\d+)"?""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 100
                } else 100
            }
        } catch (e: Exception) {
            100
        }

        // Parse lib version from versionName
        val libVersion = try {
            versionName.substringBeforeLast('.').toDouble().coerceIn(12.0, 15.0)
        } catch (e: Exception) {
            15.0
        }

        // Find source classes (classes that implement AnimeSource or Source)
        val sourceClasses = classFilesDir.walkTopDown()
            .filter { it.extension == "class" }
            .map { relativizeClassPath(it, classFilesDir) }
            .filter { className ->
                // Try to find class names that look like source implementations
                // (not the stub classes, not BuildConfig, not R$*)
                !className.contains("R\$") &&
                !className.contains("BuildConfig") &&
                !className.contains("Manifest") &&
                className.contains(pkgName.substringAfterLast(".").take(3), ignoreCase = true)
            }
            .take(5)
            .joinToString(";")

        val finalSourceClasses = sourceClasses.ifEmpty {
            // Fallback: use the first non-stub class
            classFilesDir.walkTopDown()
                .filter { it.extension == "class" }
                .map { relativizeClassPath(it, classFilesDir) }
                .firstOrNull { !it.contains("stubs") }
                ?: "${pkgName}.MainSource"
        }

        return """
        |{
        |  "name": "Aniyomi: ${pkgName.substringAfterLast(".").replaceFirstChar { it.uppercase() }}",
        |  "pkgName": "$pkgName",
        |  "versionName": "$versionName",
        |  "versionCode": $versionCode,
        |  "libVersion": $libVersion,
        |  "lang": "en",
        |  "isNsfw": false,
        |  "isTorrent": false,
        |  "sourceClass": "$finalSourceClasses",
        |  "pkgFactory": null,
        |  "hasReadme": false,
        |  "hasChangelog": false
        |}
        """.trimMargin()
    }

    /**
     * Convert a class file path to a fully qualified class name.
     */
    private fun relativizeClassPath(classFile: File, baseDir: File): String {
        return classFile.relativeTo(baseDir).path
            .replace(File.separator, ".")
            .removeSuffix(".class")
    }

    /**
     * Run a command and capture output.
     */
    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runCommand(vararg command: String): CommandResult {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return CommandResult(exitCode, stdout, stderr)
    }
}

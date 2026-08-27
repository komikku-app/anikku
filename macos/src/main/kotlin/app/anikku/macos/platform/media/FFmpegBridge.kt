package app.anikku.macos.platform.media

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * FFmpeg bridge for media processing on macOS.
 *
 * Invokes the `ffmpeg` binary via [ProcessBuilder] for:
 * - Screenshot extraction from video files
 * - Format conversion
 * - Video information extraction
 * - Thumbnail generation
 *
 * ## FFmpeg installation
 *
 * The bridge checks for ffmpeg in these locations:
 * 1. Bundled with the app: `Anikku.app/Contents/Resources/ffmpeg`
 * 2. Homebrew: `/opt/homebrew/bin/ffmpeg` (Apple Silicon) or `/usr/local/bin/ffmpeg` (Intel)
 * 3. System PATH (via `which ffmpeg`)
 *
 * If ffmpeg is not available, operations gracefully return failure
 * with descriptive error messages.
 *
 * ## Usage
 *
 * ```kotlin
 * val result = FFmpegBridge.extractScreenshot(
 *     videoFile = File("/path/to/video.mkv"),
 *     outputFile = File("/path/to/screenshot.png"),
 *     atSecond = 120.0,
 * )
 * ```
 */
object FFmpegBridge {

    private var cachedPath: String? = null

    /** Whether ffmpeg is available on this system. */
    val isAvailable: Boolean get() = findFFmpeg() != null

    /**
     * Extract a screenshot from a video at the specified timestamp.
     *
     * @param videoFile The source video file.
     * @param outputFile Where to save the screenshot.
     * @param atSecond Timestamp in seconds for the screenshot.
     * @return Result indicating success or failure.
     */
    fun extractScreenshot(
        videoFile: File,
        outputFile: File,
        atSecond: Double = 0.0,
    ): FFmpegResult {
        val ffmpeg = findFFmpeg() ?: return FFmpegResult.Error("ffmpeg not found")
        if (!videoFile.isFile) return FFmpegResult.Error("Video file not found: ${videoFile.path}")

        val command = listOf(
            ffmpeg,
            "-y", // Overwrite output
            "-ss", formatTimestamp(atSecond),
            "-i", videoFile.absolutePath,
            "-vframes", "1",
            "-q:v", "2", // High quality
            outputFile.absolutePath,
        )

        return execute(command)
    }

    /**
     * Get media file information (duration, codec, resolution, etc.).
     *
     * @param videoFile The video file to analyze.
     * @return Parsed media information, or error result.
     */
    fun getMediaInfo(videoFile: File): FFmpegResult {
        val ffmpeg = findFFmpeg() ?: return FFmpegResult.Error("ffmpeg not found")
        if (!videoFile.isFile) return FFmpegResult.Error("Video file not found: ${videoFile.path}")

        val command = listOf(
            ffmpeg,
            "-i", videoFile.absolutePath,
            "-f", "null",
            "-",
        )

        return execute(command)
    }

    /**
     * Probe media file information using ffprobe for structured output.
     *
     * @param videoFile The video file to probe.
     * @return JSON-formatted media information, or error result.
     */
    fun probeMediaInfo(videoFile: File): FFmpegResult {
        val ffprobe = findFFprobe() ?: return FFmpegResult.Error("ffprobe not found")
        if (!videoFile.isFile) return FFmpegResult.Error("Video file not found: ${videoFile.path}")

        val command = listOf(
            ffprobe,
            "-v", "quiet",
            "-print_format", "json",
            "-show_format",
            "-show_streams",
            videoFile.absolutePath,
        )

        return execute(command)
    }

    /**
     * Convert a video file to a different format.
     *
     * @param inputFile Source video file.
     * @param outputFile Output path (extension determines format).
     * @param extraArgs Additional ffmpeg arguments (e.g., codec, bitrate).
     * @return Result indicating success or failure.
     */
    fun convertVideo(
        inputFile: File,
        outputFile: File,
        vararg extraArgs: String,
    ): FFmpegResult {
        val ffmpeg = findFFmpeg() ?: return FFmpegResult.Error("ffmpeg not found")
        if (!inputFile.isFile) return FFmpegResult.Error("Input file not found: ${inputFile.path}")

        val command = mutableListOf(
            ffmpeg,
            "-y",
            "-i", inputFile.absolutePath,
        )
        command.addAll(extraArgs)
        command.add(outputFile.absolutePath)

        return execute(command)
    }

    /**
     * Generate a thumbnail for a video file.
     *
     * @param videoFile Source video file.
     * @param outputDir Directory to save thumbnails.
     * @param maxWidth Maximum width of the thumbnail (aspect ratio preserved).
     * @return Path to the generated thumbnail, or error result.
     */
    fun generateThumbnail(
        videoFile: File,
        outputDir: File,
        maxWidth: Int = 320,
    ): FFmpegResult {
        val ffmpeg = findFFmpeg() ?: return FFmpegResult.Error("ffmpeg not found")
        if (!videoFile.isFile) return FFmpegResult.Error("Video file not found: ${videoFile.path}")

        outputDir.mkdirs()
        val thumbnailFile = File(outputDir, "${videoFile.nameWithoutExtension}.jpg")

        val command = listOf(
            ffmpeg,
            "-y",
            "-i", videoFile.absolutePath,
            "-vframes", "1",
            "-vf", "scale=$maxWidth:-1",
            thumbnailFile.absolutePath,
        )

        val result = execute(command)
        return if (result is FFmpegResult.Success) {
            FFmpegResult.Success(thumbnailFile.absolutePath)
        } else {
            result
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun findFFmpeg(): String? {
        if (cachedPath != null) return cachedPath

        val candidates = listOf(
            // Bundle path
            findBundleBinary("ffmpeg"),
            // Homebrew Apple Silicon
            "/opt/homebrew/bin/ffmpeg",
            // Homebrew Intel
            "/usr/local/bin/ffmpeg",
            // MacPorts
            "/opt/local/bin/ffmpeg",
        )

        val found = candidates.firstOrNull { File(it).isFile }
            ?: findInPath("ffmpeg")

        if (found != null) {
            cachedPath = found
        }

        return found
    }

    private fun findFFprobe(): String? {
        val candidates = listOf(
            findBundleBinary("ffprobe"),
            "/opt/homebrew/bin/ffprobe",
            "/usr/local/bin/ffprobe",
            "/opt/local/bin/ffprobe",
        )

        return candidates.firstOrNull { File(it).isFile }
            ?: findInPath("ffprobe")
    }

    private fun findBundleBinary(name: String): String {
        // Check relative to the app bundle
        val bundlePath = System.getProperty("com.apple.application.bundle.path")
            ?: return ""

        val paths = listOf(
            "$bundlePath/Contents/Resources/$name",
            "$bundlePath/Contents/MacOS/$name",
            "$bundlePath/Contents/Frameworks/$name",
        )

        return paths.firstOrNull { File(it).isFile } ?: ""
    }

    private fun findInPath(name: String): String? {
        return try {
            val process = ProcessBuilder("which", name)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isNotEmpty() && File(output).isFile) output else null
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTimestamp(seconds: Double): String {
        val totalMillis = (seconds * 1000).toLong()
        val hours = totalMillis / 3600000
        val minutes = (totalMillis % 3600000) / 60000
        val secs = (totalMillis % 60000) / 1000
        val millis = totalMillis % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, secs, millis)
    }

    private fun execute(command: List<String>): FFmpegResult {
        logger.debug { "Executing: ${command.joinToString(" ")}" }

        return try {
            val startTime = System.currentTimeMillis()
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startTime

            if (exitCode == 0) {
                logger.debug { "FFmpeg completed in ${duration}ms" }
                FFmpegResult.Success(output)
            } else {
                logger.warn { "FFmpeg exited with code $exitCode: $output" }
                FFmpegResult.Error("ffmpeg exited with code $exitCode: ${output.take(200)}")
            }
        } catch (e: Exception) {
            logger.error(e) { "FFmpeg execution failed" }
            FFmpegResult.Error("ffmpeg execution failed: ${e.message}")
        }
    }
}

/**
 * Result of an FFmpeg operation.
 */
sealed class FFmpegResult {
    /** Operation completed successfully. */
    data class Success(val output: String) : FFmpegResult()
    /** Operation failed with an error message. */
    data class Error(val message: String) : FFmpegResult()
}

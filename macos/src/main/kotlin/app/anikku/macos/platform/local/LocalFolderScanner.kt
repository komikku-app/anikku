package app.anikku.macos.platform.local

import app.anikku.macos.platform.library.AnimeSourceMatcher
import app.anikku.macos.platform.torrent.NyaaTorrentParser
import java.io.File
import java.util.Locale

/**
 * Scans a user-chosen folder for video files and turns each into a
 * [LocalVideoEntry] by parsing the filename with the same release-name parser
 * used for Nyaa torrents ("Show - 01 (1080p).mkv", "Show S01E05.mkv", …).
 *
 * Files that don't parse into a usable title are skipped (they'd be noise).
 */
object LocalFolderScanner {

    val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "m4v", "ts", "webm")

    /**
     * Recursively scan [folder] (up to [maxDepth] levels) for video files.
     * Never throws; unreadable entries are skipped.
     */
    fun scan(folder: File, maxDepth: Int = 3): List<LocalVideoEntry> {
        if (!folder.isDirectory) return emptyList()
        val found = mutableListOf<LocalVideoEntry>()
        walk(folder, 0, maxDepth, found)
        return found
    }

    private fun walk(dir: File, depth: Int, maxDepth: Int, out: MutableList<LocalVideoEntry>) {
        if (depth > maxDepth) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children.sortedBy { it.name.lowercase(Locale.ROOT) }) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child, depth + 1, maxDepth, out)
            } else if (child.isFile && isVideoFile(child.name)) {
                toEntry(child)?.let { out.add(it) }
            }
        }
    }

    private fun isVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in VIDEO_EXTENSIONS
    }

    private fun toEntry(file: File): LocalVideoEntry? {
        val parsed = NyaaTorrentParser.parse(file.nameWithoutExtension)
        val normalizedKey = AnimeSourceMatcher.normalizeTitle(parsed.title)
        if (normalizedKey.isEmpty()) return null
        // Only files that look like anime episodes (or batch releases) belong
        // in the collection — a random video file without episode info would
        // just be noise in the grouped view.
        if (parsed.episode == null && !parsed.batch) return null
        val animeId = normalizedKey.hashCode().toLong().let { if (it == 0L) 1L else it }
        return LocalVideoEntry(
            animeId = animeId,
            title = parsed.title,
            season = (parsed.season ?: 1).coerceAtLeast(1),
            episode = parsed.episode ?: 0,
            filePath = file.absolutePath,
            fileName = file.name,
            sizeBytes = runCatching { file.length() }.getOrDefault(0L),
        )
    }
}

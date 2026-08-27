package app.anikku.macos.platform.storage

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Small persistence primitive for macOS JSON stores.
 *
 * Writes are serialized per canonical target path, data is flushed to a unique
 * temporary file, and the completed file is moved into place. A malformed
 * source file is copied aside before a store starts fresh, so recovery evidence
 * is retained instead of being silently destroyed.
 */
internal object MacOSAtomicFile {
    private val locks = ConcurrentHashMap<String, Any>()

    fun writeText(target: File, content: String) {
        val parent = target.absoluteFile.parentFile
            ?: throw IOException("Persistence target has no parent directory: ${target.path}")
        require(parent.exists() || parent.mkdirs()) {
            "Unable to create persistence directory: ${parent.path}"
        }
        require(parent.isDirectory) { "Persistence parent is not a directory: ${parent.path}" }

        val canonicalTarget = target.canonicalFile
        val lock = locks.computeIfAbsent(canonicalTarget.path) { Any() }
        synchronized(lock) {
            val temporary = Files.createTempFile(
                parent.toPath(),
                ".${target.name}.",
                ".tmp",
            ).toFile()
            try {
                temporary.writeText(content, StandardCharsets.UTF_8)
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        }
    }

    fun preserveMalformed(target: File): File? {
        if (!target.isFile) return null
        val parent = target.absoluteFile.parentFile ?: return null
        val backup = File(
            parent,
            ".${target.name}.corrupt-${System.currentTimeMillis()}",
        )
        return runCatching {
            Files.copy(
                target.toPath(),
                backup.toPath(),
                StandardCopyOption.COPY_ATTRIBUTES,
            )
            backup
        }.getOrNull()
    }
}

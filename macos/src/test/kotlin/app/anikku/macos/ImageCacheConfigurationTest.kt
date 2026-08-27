package app.anikku.macos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ImageCacheConfigurationTest {
    @Test
    fun `image caches are bounded and disk cache lives in app support`() {
        assertEquals(256L * 1024L * 1024L, IMAGE_MEMORY_CACHE_BYTES)
        assertEquals(512L * 1024L * 1024L, IMAGE_DISK_CACHE_BYTES)

        val path = imageDiskCacheDirectory("/Users/tester")
        assertEquals(
            File("/Users/tester/Library/Application Support/Anikku/cache/images"),
            path,
        )
        assertTrue(path.path.endsWith("Anikku${File.separator}cache${File.separator}images"))
    }
}

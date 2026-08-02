package tachiyomi.core.common.storage

import java.io.File

/**
 * Minimal compatibility interface required by JVM extension binaries.
 */
interface FolderProvider {
    fun directory(): File
    fun path(): String
}

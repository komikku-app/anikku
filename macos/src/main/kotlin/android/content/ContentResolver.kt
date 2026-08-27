package android.content

import android.net.Uri

/**
 * Stub for `android.content.ContentResolver` on macOS JVM.
 *
 * Extensions may call `contentResolver.openInputStream(uri)`.
 */
open class ContentResolver {
    fun openInputStream(uri: Uri): java.io.InputStream? = null
    fun openOutputStream(uri: Uri): java.io.OutputStream? = null
    fun openOutputStream(uri: Uri, mode: String): java.io.OutputStream? = null
}

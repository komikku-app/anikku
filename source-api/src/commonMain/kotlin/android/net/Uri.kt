package android.net

import java.io.File

/**
 * Expect declaration for android.net.Uri.
 * Android: actual typealias = android.net.Uri
 * JVM: actual class stub
 */
expect open class Uri {
    val scheme: String?
    val path: String?

    companion object {
        fun fromFile(file: File): Uri
        fun parse(uriString: String): Uri
    }
}

package android.content.pm

/**
 * Stub for `android.content.pm.PackageManager` on macOS JVM.
 */
open class PackageManager {
    companion object {
        const val PERMISSION_GRANTED: Int = 0
        const val PERMISSION_DENIED: Int = -1
        const val GET_SIGNATURES: Int = 0x00000040
        const val GET_SIGNING_CERTIFICATES: Int = 0x08000000
    }

    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo()

    fun checkPermission(permName: String, pkgName: String): Int = PERMISSION_GRANTED
}

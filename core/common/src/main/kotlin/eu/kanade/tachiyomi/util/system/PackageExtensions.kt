package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import java.util.Collections

fun Context.defaultBrowserPackageName(): String? {
    val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
    val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
            browserIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolveInfo
        ?.activityInfo?.packageName
    // KMK -->
    // ?.takeUnless { it in DeviceUtil.invalidDefaultBrowsers }
    // KMK <--
}

// KMK -->
/** Cache for installed packages to avoid querying the package manager multiple times */
private val installedPackagesCache = Collections.synchronizedList(mutableListOf<String>())

/**
 * Returns a list of all installed package names on the device.
 */
fun Context.getAllInstalledPackages(): List<String> {
    if (installedPackagesCache.isEmpty()) {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            packageManager.getInstalledPackages(0)
        }
        installedPackagesCache.addAll(packages.map { it.packageName })
    }
    return installedPackagesCache
}
// KMK <--

/**
 * Returns true if [packageName] is installed.
 */
fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.launchRequestPackageInstallsPermission() {
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = "package:$packageName".toUri()
        startActivity(this)
    }
}

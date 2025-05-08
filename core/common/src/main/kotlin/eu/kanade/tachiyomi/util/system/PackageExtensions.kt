package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.ank.AMR
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
    // KMK -->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // KMK <--
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:$packageName".toUri()
            startActivity(this)
        }
        // KMK -->
    } else {
        // For Android 7.1 (API 25) and below
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        try {
            startActivity(intent)
            toast(stringResource(AMR.strings.install_unknown_apps_permission_required))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            toast(e.message ?: stringResource(AMR.strings.unable_open_security_settings))
        }
    }
    // KMK <--
}

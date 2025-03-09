package eu.kanade.tachiyomi.util.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.PermissionChecker
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.setting.connections.DiscordLoginActivity
import eu.kanade.tachiyomi.util.lang.truncateCenter
import logcat.LogPriority
import rikka.sui.Sui
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.File

/**
 * Copies a string to clipboard
 *
 * @param label Label to show to the user describing the content
 * @param content the actual text to copy to the board
 */
fun Context.copyToClipboard(label: String, content: String) {
    if (content.isBlank()) return

    try {
        val clipboard = getSystemService<ClipboardManager>()!!
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))

        // Android 13 and higher shows a visual confirmation of copied contents
        // https://developer.android.com/about/versions/13/features/copy-paste
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            toast(stringResource(MR.strings.copied_to_clipboard, content.truncateCenter(50)))
        }
    } catch (e: Throwable) {
        logcat(LogPriority.ERROR, e)
        toast(MR.strings.clipboard_copy_error)
    }
}

/**
 * Checks if the give permission is granted.
 *
 * @param permission the permission to check.
 * @return true if it has permissions.
 */
fun Context.hasPermission(permission: String) = PermissionChecker.checkSelfPermission(
    this,
    permission,
) == PermissionChecker.PERMISSION_GRANTED

val Context.powerManager: PowerManager
    get() = getSystemService()!!

fun Context.openInBrowser(url: String, forceDefaultBrowser: Boolean = false) {
    this.openInBrowser(url.toUri(), forceDefaultBrowser)
}

fun Context.openInBrowser(uri: Uri, forceDefaultBrowser: Boolean = false) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // Force default browser so that verified extensions don't re-open Tachiyomi
            if (forceDefaultBrowser) {
                defaultBrowserPackageName()?.let { setPackage(it) }
            }
        }
        startActivity(intent)
    } catch (e: Exception) {
        toast(e.message)
    }
}

// AM (DISCORD) -->
fun Context.openDiscordLoginActivity() {
    try {
        val intent = Intent(this, DiscordLoginActivity::class.java)
        startActivity(intent)
    } catch (e: Exception) {
        toast(e.message)
    }
}
// <-- AM (DISCORD)

private fun Context.defaultBrowserPackageName(): String? {
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
        ?.takeUnless { it in DeviceUtil.invalidDefaultBrowsers }
}

fun Context.createFileInCacheDir(name: String): File {
    val file = File(externalCacheDir, name)
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
    return file
}

/**
 * Gets document size of provided [Uri]
 *
 * @return document size of [uri] or null if size can't be obtained
 */
fun Context.getUriSize(uri: Uri): Long? {
    return UniFile.fromUri(this, uri)?.length()?.takeIf { it >= 0 }
}

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

val Context.hasMiuiPackageInstaller get() = isPackageInstalled("com.miui.packageinstaller")

val Context.isShizukuInstalled get() = isPackageInstalled("moe.shizuku.privileged.api") || Sui.isSui()

fun Context.isInstalledFromFDroid(): Boolean {
    val installerPackageName = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    } catch (e: Exception) {
        null
    }

    return installerPackageName == "org.fdroid.fdroid" ||
        // F-Droid builds typically disable the updater
        (!BuildConfig.INCLUDE_UPDATER && !isDevFlavor)
}

fun Context.launchRequestPackageInstallsPermission() {
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:$packageName")
        startActivity(this)
    }
}

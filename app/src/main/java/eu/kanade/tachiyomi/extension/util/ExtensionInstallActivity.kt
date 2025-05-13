package eu.kanade.tachiyomi.extension.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.hasMiuiPackageInstaller
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Activity used to install extensions, because we can only receive the install completion
 * broadcast if we use [startActivityForResult] with the package installer.
 */
class ExtensionInstallActivity : Activity() {

    // MIUI package installer bug workaround
    private var ignoreUntil = 0L
    private var ignoreResult = false
    private var hasIgnoredResult = false
    private var tempApkFilePath: String? = null
    private var tempFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tempApkFilePath = intent.getStringExtra(ExtensionInstaller.EXTRA_APK_FILEPATH)
        tempFileUri = intent.data

        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(intent.data, intent.type)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (hasMiuiPackageInstaller) {
            ignoreResult = true
            ignoreUntil = System.nanoTime() + 1.seconds.inWholeNanoseconds
        }

        try {
            startActivityForResult(installIntent, INSTALL_REQUEST_CODE)
        } catch (error: Exception) {
            // Either install package can't be found (probably bots) or there's a security exception
            // with the download manager. Nothing we can workaround.
            toast(error.message)
            logcat(LogPriority.ERROR, error)
            this.finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (ignoreResult && System.nanoTime() < ignoreUntil) {
            hasIgnoredResult = true
            return
        }
        if (requestCode == INSTALL_REQUEST_CODE) {
            // Clean up temp file if needed
            cleanupTempApkFile()
            val file = UniFile.fromUri(applicationContext, tempFileUri)
            file?.delete()
            checkInstallationResult(resultCode)
        }
        finish()
    }

    override fun onStart() {
        super.onStart()
        if (hasIgnoredResult) {
            checkInstallationResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun checkInstallationResult(resultCode: Int) {
        val downloadId = intent.extras!!.getLong(ExtensionInstaller.EXTRA_DOWNLOAD_ID)
        val extensionManager = Injekt.get<ExtensionManager>()
        val newStep = when (resultCode) {
            RESULT_OK -> InstallStep.Installed
            RESULT_CANCELED -> InstallStep.Idle
            else -> InstallStep.Error
        }
        extensionManager.updateInstallStep(downloadId, newStep)
    }

    private fun cleanupTempApkFile() {
        tempApkFilePath?.let {
            try {
                val tempFile = File(it)
                if (tempFile.exists()) {
                    tempFile.delete()
                    logcat { "Deleted temporary APK file: $it" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to delete temporary APK file" }
            }
        }
    }

    companion object {
        private const val INSTALL_REQUEST_CODE = 500
    }
}

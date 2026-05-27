package animiru.feature.mpvfiles

import android.content.Context
import android.content.res.AssetManager
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MpvConfig(
    private val context: Context,
    private val storageManager: StorageManager,
    private val advancedPlayerPreferences: AdvancedPlayerPreferences,
    private val getCustomButtons: GetCustomButtons,
) {
    // ANK -->
    // A plain CoroutineScope lets any failure reach the default uncaught handler and kill the app,
    // so the scope swallows and logs instead.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, throwable ->
                logcat(LogPriority.ERROR, throwable) { "Uncaught failure while copying mpv files" }
            },
    )

    /** Number of live [eu.kanade.tachiyomi.ui.player.PlayerActivity] instances. */
    private val playerSessions = AtomicInteger(0)
    private val copyPending = AtomicBoolean(false)
    // ANK <--

    private var copyJob: Job? = null

    fun copyFiles() {
        // ANK -->
        // Copying wipes the scripts/script-opts/shaders/fonts directories first, which must never
        // happen underneath a running mpv instance. MainActivity stays resumed behind the player in
        // picture-in-picture and split-screen, so defer while any player lives.
        //
        // A request that arrives mid-copy is deferred too: the copy may already have passed the
        // directory it concerns, so it is recorded and replayed by the loop below instead of being
        // dropped.
        if (playerSessions.get() > 0 || copyJob?.isActive == true) {
            copyPending.set(true)
            return
        }

        copyJob = scope.launchIO {
            do {
                // Cleared at the start of the pass, so a request racing with it is recorded again
                // and picked up by the loop condition rather than overwritten.
                copyPending.set(false)
                try {
                    val mpvDir = getMpvDir()
                    copyUserFiles(mpvDir)
                    copyFontsDirectory(mpvDir)
                    copyAssets(mpvDir)
                    writeFontsConf(context, mpvDir)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to copy mpv files" }
                }
                // Bail out if a player started during the pass; onPlayerDestroyed() flushes the
                // still-pending request once it is safe again.
            } while (copyPending.get() && playerSessions.get() == 0)
        }
        // ANK <--
    }

    // ANK -->
    /**
     * Suspends until any in-flight copy has finished, so mpv never initializes against a
     * directory tree that is still being deleted and rewritten.
     */
    suspend fun awaitCopy() {
        copyJob?.join()
    }

    fun onPlayerCreated() {
        playerSessions.incrementAndGet()
    }

    fun onPlayerDestroyed() {
        val remaining = playerSessions.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (remaining == 0 && copyPending.get()) {
            copyFiles()
        }
    }
    // ANK <--

    private fun getMpvDir(): UniFile {
        return UniFile.fromFile(context.filesDir)!!.createDirectory(MPV_DIR)!!
    }

    private suspend fun copyUserFiles(mpvDir: UniFile) {
        // First, delete all present scripts
        val scriptsDir = deleteAndGet(mpvDir, MPV_SCRIPTS_DIR)
        val scriptOptsDir = deleteAndGet(mpvDir, MPV_SCRIPTS_OPTS_DIR)
        val shadersDir = deleteAndGet(mpvDir, MPV_SHADERS_DIR)

        // Then, copy the user files from the Aniyomi directory
        if (advancedPlayerPreferences.mpvUserFiles().get()) {
            copyDirectoryContents(storageManager.getScriptsDirectory(), scriptsDir)
            copyDirectoryContents(storageManager.getScriptOptsDirectory(), scriptOptsDir)
            copyDirectoryContents(storageManager.getShadersDirectory(), shadersDir)
        }

        val buttons = getCustomButtons.getAll()
        setupCustomButtons(buttons)

        // Copy over the bridge file
        val luaFile = scriptsDir.createFile("aniyomi.lua") ?: return
        context.assets.open("aniyomi.lua").use { inputStream ->
            luaFile.openOutputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    fun setupCustomButtons(buttons: List<CustomButton>) {
        val scriptsDir = getMpvDir().createDirectory(MPV_SCRIPTS_DIR)!!
        val primaryButtonId = buttons.firstOrNull { it.isFavorite }?.id ?: 0L

        val customButtonsContent = buildString {
            appendLine(
                """
                    local lua_modules = mp.find_config_file('scripts')
                    if lua_modules then
                        package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir.filePath}' .. '/?.lua'
                    end
                    local aniyomi = require 'aniyomi'
                """.trimIndent(),
            )

            buttons.forEach { button ->
                appendLine(
                    """
                        ${button.getButtonOnStartup(primaryButtonId)}
                        function button${button.id}()
                            ${button.getButtonContent(primaryButtonId)}
                        end
                        mp.register_script_message('call_button_${button.id}', button${button.id})
                        function button${button.id}long()
                            ${button.getButtonLongPressContent(primaryButtonId)}
                        end
                        mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                    """.trimIndent(),
                )
            }
        }

        val file = scriptsDir.createFile("custombuttons.lua")
        file?.openOutputStream()?.bufferedWriter()?.use {
            it.write(customButtonsContent)
        }
    }

    private suspend fun copyFontsDirectory(mpvDir: UniFile) {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        val fontsDirectory = deleteAndGet(mpvDir, MPV_FONTS_DIR)
        copyDirectoryContents(storageManager.getFontsDirectory(), fontsDirectory)
    }

    private fun copyAssets(mpvDir: UniFile) {
        val assetManager = context.assets
        val files = arrayOf("cacert.pem")
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = mpvDir.createFile(filename)!!
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    logcat(LogPriority.VERBOSE) { "Skipping copy of asset file (exists same size): $filename" }
                    continue
                }
                out = outFile.openOutputStream()
                ins.copyTo(out)
                logcat(LogPriority.WARN) { "Copied asset file: $filename" }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR, e) { "Failed to copy asset file: $filename" }
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    private fun writeFontsConf(context: Context, mpvDir: UniFile) {
        val parts = listOfNotNull(
            "<fontconfig>",
            // Android system fonts reside here
            "<dir>/system/fonts/</dir>",
            "<dir>/product/fonts/</dir>",
            // User provided fonts
            // ANK -->
            mpvDir.createDirectory(MPV_FONTS_DIR)?.filePath?.let { filePath -> "<dir>$filePath</dir>" },
            // ANK <--
            // Point fontconfig to the right cache path so that caching works
            "<cachedir>${context.cacheDir.path}</cachedir>",
            // Conveniently there is *no* Java API to query the system default fonts, but we can
            // manually specify the font families we know Android uses and provides by default.
            // (compare to 60-latin.conf shipped with fontconfig)
            "<alias><family>serif</family>",
            "<prefer><family>Noto Serif</family></prefer>",
            "</alias>",
            "<alias><family>Sans Serif</family>",
            "<prefer>",
            "<family>Roboto</family>",
            "<family>Noto Sans</family>", // other languages
            "</prefer>",
            "</alias>",
            "<alias><family>monospace</family>",
            "<prefer><family>Droid Sans Mono</family></prefer>",
            "</alias>",
            "</fontconfig>",
        ).toMutableList()
        try {
            val file = mpvDir.createFile("fonts.conf")
            file?.openOutputStream()?.bufferedWriter()?.use {
                it.write(parts.joinToString("\n"))
            }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, e) { "Failed to write fonts.conf" }
        }
    }

    private fun deleteAndGet(parent: UniFile, name: String): UniFile {
        parent.createDirectory(name)?.delete()
        return parent.createDirectory(name)!!
    }

    private suspend fun copyDirectoryContents(sourceDir: UniFile?, destDir: UniFile) {
        sourceDir?.listFiles()?.forEach { file ->
            if (!currentCoroutineContext().isActive) {
                throw CancellationException()
            }

            val outFile = destDir.createFile(file.name) ?: return@forEach
            file.openInputStream().use { input ->
                outFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    companion object {
        const val MPV_DIR = "mpv"
        const val MPV_FONTS_DIR = "fonts"
        const val MPV_SCRIPTS_DIR = "scripts"
        const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
        const val MPV_SHADERS_DIR = "shaders"
    }
}

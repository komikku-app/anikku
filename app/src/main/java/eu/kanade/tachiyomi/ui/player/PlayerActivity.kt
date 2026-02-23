/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Code is a mix between PlayerActivity from mpvKt and the former
 * PlayerActivity from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.hippo.unifile.UniFile
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.PlayerData
import eu.kanade.tachiyomi.data.download.sanitizeFFmpegKey
import eu.kanade.tachiyomi.data.download.sanitizeFFmpegValue
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.torrentServer.service.TorrentServerService
import eu.kanade.tachiyomi.source.isNsfw
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.torrentServer.TorrentServerApi
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.UrlUtils
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.floor

class PlayerActivity : BaseActivity() {
    private val viewModel by viewModels<PlayerViewModel>()
    private val mpv by lazy { viewModel.mpv }
    private val player by lazy { AniyomiMPVView(this, null) }
    private val playerObserver by lazy { PlayerObserver(this) }
    private val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val inputMethodManager by lazy { getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }

    private var mediaSession: MediaSession? = null
    private val gesturePreferences: GesturePreferences = Injekt.get()
    private val playerPreferences: PlayerPreferences = Injekt.get()
    private val audioPreferences: AudioPreferences = Injekt.get()
    private val advancedPlayerPreferences: AdvancedPlayerPreferences = Injekt.get()
    private val storageManager: StorageManager = Injekt.get()

    // Cast -->
    val castManager: CastManager by lazy { CastManager(this, Injekt.get()) }
    // <-- Cast

    // AM (CONNECTIONS) -->
    private val connectionsPreferences: ConnectionsPreferences = Injekt.get()
    // <-- AM (CONNECTIONS)

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var restoreAudioFocus: () -> Unit = {}

    private var pipRect: Rect? = null
    val isPipSupportedAndEnabled by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            playerPreferences.enablePip().get()
    }

    private var pipReceiver: BroadcastReceiver? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        var initialized = false
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    companion object {
        fun newIntent(
            context: Context,
            animeId: Long?,
            episodeId: Long?,
            hostList: List<Hoster>? = null,
            hostIndex: Int? = null,
            vidIndex: Int? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("animeId", animeId)
                putExtra("episodeId", episodeId)
                hostIndex?.let { putExtra("hostIndex", it) }
                vidIndex?.let { putExtra("vidIndex", it) }
                hostList?.let { putExtra("hostList", it.serialize()) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        internal const val MPV_DIR = "mpv"
        private const val MPV_FONTS_DIR = "fonts"
        private const val MPV_SCRIPTS_DIR = "scripts"
        private const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
        private const val MPV_SHADERS_DIR = "shaders"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val animeId = intent.extras?.getLong("animeId") ?: -1
        val episodeId = intent.extras?.getLong("episodeId") ?: -1
        val hostList = intent.extras?.getString("hostList") ?: ""
        val hostIndex = intent.extras?.getInt("hostIndex") ?: -1
        val vidIndex = intent.extras?.getInt("vidIndex") ?: -1
        if (animeId == -1L || episodeId == -1L) {
            finish()
            return
        }
        NotificationReceiver.dismissNotification(
            this,
            animeId.hashCode(),
            Notifications.ID_NEW_EPISODES,
        )

        viewModel.saveCurrentEpisodeWatchingProgress()

        lifecycleScope.launchNonCancellable {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)

            val initResult = viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                withUIContext {
                    setInitialEpisodeError(exception)
                }
            }

            viewModel.updateIsLoadingHosters(false)

            lifecycleScope.launch {
                viewModel.loadHosters(
                    source = viewModel.currentSource.value!!,
                    hosterList = initResult.first.hosterList ?: emptyList(),
                    hosterIndex = initResult.first.videoIndex.first,
                    videoIndex = initResult.first.videoIndex.second,
                )
            }
        }

        setIntent(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        registerSecureActivity(this)
        super.onCreate(savedInstanceState)

        setupPlayerMPV()
        setupCustomButtons()
        setupPlayerAudio()
        setupMediaSession()
        setupPlayerOrientation()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                toast(throwable.message)
            }
            logcat(LogPriority.ERROR, throwable)
            finish()
        }

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is PlayerViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is PlayerViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.seconds)
                    }
                    is PlayerViewModel.Event.SetCoverResult -> {
                        onSetAsArtResult(event.result, event.artType)
                    }
                    is PlayerViewModel.Event.ShowToast -> {
                        showToast(stringResource(event.stringResource))
                    }
                    is PlayerViewModel.Event.ShowToastString -> {
                        showToast(event.string)
                    }
                    is PlayerViewModel.Event.ChangeEpisode -> {
                        changeEpisode(event.episodeId, event.autoPlay)
                    }
                    is PlayerViewModel.Event.SetVideo -> {
                        setVideo(event.video)
                    }
                    is PlayerViewModel.Event.SetStatusBar -> {
                        if (event.show) {
                            windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
                        } else {
                            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
                        }
                    }
                    is PlayerViewModel.Event.SetBrightness -> {
                        window.attributes = window.attributes.apply {
                            screenBrightness = event.brightness
                        }
                    }
                    is PlayerViewModel.Event.ChangeVideoAspect -> {
                        changeVideoAspect(event.aspect)
                    }
                    PlayerViewModel.Event.CycleRotations -> {
                        cycleRotations()
                    }
                    is PlayerViewModel.Event.SetKeyboard -> {
                        if (event.show) {
                            forceShowSoftwareKeyboard()
                        } else {
                            forceHideSoftwareKeyboard()
                        }
                    }
                    PlayerViewModel.Event.ToggleKeyboard -> {
                        toggleShowSoftwareKeyboard()
                    }
                }
            }
            .launchIn(lifecycleScope)

        // AM (DISCORD) -->
        viewModel.viewModelScope.launchIO {
            updateDiscordRPC(exitingPlayer = false)
        }
        // <-- AM (DISCORD)

        // Cast -->
        castManager
        // <-- Cast

        setContent {
            TachiyomiTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { player },
                        modifier = Modifier.onGloballyPositioned {
                            pipRect = run {
                                val boundsInWindow = it.boundsInWindow()
                                Rect(
                                    boundsInWindow.left.toInt(),
                                    boundsInWindow.top.toInt(),
                                    boundsInWindow.right.toInt(),
                                    boundsInWindow.bottom.toInt(),
                                )
                            }
                        },
                    )
                    PlayerControls(
                        viewModel = viewModel,
                        castManager = castManager, // Pass the castManager instance
                        onBackPress = {
                            if (isPipSupportedAndEnabled && viewModel.paused == false &&
                                playerPreferences.pipOnExit().get()
                            ) {
                                enterPictureInPictureMode(createPipParams())
                            } else {
                                finish()
                            }
                        },
                    )
                }
            }
        }

        // ANK -->
        // Migrate system back gesture handling to OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = backPressed()
            },
        )
        // ANK <--

        onNewIntent(this.intent)
    }

    override fun onDestroy() {
        player.isExiting = true

        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
        audioFocusRequest = null

        mediaSession?.let {
            it.isActive = false
            it.release()
        }

        if (noisyReceiver.initialized) {
            unregisterReceiver(noisyReceiver)
            noisyReceiver.initialized = false
        }

        mpv.removeLogObserver(playerObserver)
        mpv.removeObserver(playerObserver)
        // ANK -->
        // `mpv` is owned by the retained PlayerViewModel and must survive activity
        // recreation (e.g. config changes), so it's closed in onCleared() instead of here.
        // mpv.close()
        // ANK <--
        castManager.cleanup()

        // AM (DISCORD) -->
        updateDiscordRPC(exitingPlayer = true)
        // <-- AM (DISCORD)

        super.onDestroy()
    }

    override fun onPause() {
        viewModel.saveCurrentEpisodeWatchingProgress()

        // Maintain active Cast session
        castManager.maintainCastSessionBackground()

        // AM (DISCORD) -->
        updateDiscordRPC(exitingPlayer = true)
        // <-- AM (DISCORD)

        if (isInPictureInPictureMode) {
            super.onPause()
            return
        }

        player.isExiting = true
        if (isFinishing) {
            viewModel.deletePendingEpisodes()
            mpv.command("stop")
        } else {
            viewModel.pause()
        }

        super.onPause()
    }

    override fun onStop() {
        window.attributes.screenBrightness.let {
            if (playerPreferences.rememberPlayerBrightness().get() && it != -1f) {
                playerPreferences.playerBrightnessValue().set(it)
            }
        }

        if (isInPictureInPictureMode && powerManager.isInteractive) {
            viewModel.deletePendingEpisodes()
        }

        super.onStop()
    }

    override fun onUserLeaveHint() {
        if (isPipSupportedAndEnabled && viewModel.paused == false && playerPreferences.pipOnExit().get()) {
            enterPictureInPictureMode()
        }
        super.onUserLeaveHint()
    }

    // ANK -->
    private fun backPressed() {
        // ANK <--
        if (isPipSupportedAndEnabled && viewModel.paused == false && playerPreferences.pipOnExit().get()) {
            if (viewModel.sheetShown.value == Sheets.None &&
                viewModel.panelShown.value == Panels.None &&
                viewModel.dialogShown.value == Dialogs.None
            ) {
                enterPictureInPictureMode()
            }
            // ANK -->
            return
        }

        // Default behavior: finish the activity
        finish()
        // ANK <--
    }

    override fun onStart() {
        super.onStart()
        setPictureInPictureParams(createPipParams())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.playerFullscreen().get()) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        if (playerPreferences.rememberPlayerBrightness().get()) {
            playerPreferences.playerBrightnessValue().get().let {
                if (it != -1f) viewModel.changeBrightnessTo(it)
            }
        }

        // AM (DISCORD) -->
        updateDiscordRPC(exitingPlayer = false)
        // <-- AM (DISCORD)

        castManager.apply {
            // Register session listener cast
            registerSessionListener()

            // Update current status of cast
            if (castState.value == CastManager.CastState.CONNECTED) {
                updateCastState(CastManager.CastState.CONNECTED)
            }
            // Synchronize initial status with viewmodel
            viewModel.isCasting.value = castState.value == CastManager.CastState.CONNECTED
        }
    }

    private fun UniFile.writeText(text: String) {
        this.openOutputStream().use {
            it.write(text.toByteArray())
        }
    }

    private fun setupPlayerMPV() {
        val mpvDir = UniFile.fromFile(applicationContext.filesDir)?.createDirectory(MPV_DIR)
            ?: run {
                logcat(LogPriority.ERROR) { "Failed to create MPV directory: $MPV_DIR in ${applicationContext.filesDir}" }
                return
            }

        val mpvConfFile = mpvDir.createFile("mpv.conf")!!
        advancedPlayerPreferences.mpvConf().get().let { mpvConfFile.writeText(it) }
        val mpvInputFile = mpvDir.createFile("input.conf")!!
        advancedPlayerPreferences.mpvInput().get().let { mpvInputFile.writeText(it) }

        copyUserFiles(mpvDir)
        copyAssets(mpvDir)
        // ANK -->
        // Should provision configuration scripts before initializing MPV
        player.init(mpv)
        // ANK <--
        copyFontsDirectory(mpvDir)

        mpv.setOptionString("sub-ass-force-margins", "yes")
        mpv.setOptionString("sub-use-margins", "yes")
        mpv.addLogObserver(playerObserver)
        mpv.addObserver(playerObserver)
    }

    private fun copyUserFiles(mpvDir: UniFile) {
        // First, delete all present scripts
        val scriptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_DIR) }
        val scriptOptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_OPTS_DIR) }
        val shadersDir = { mpvDir.createDirectory(MPV_SHADERS_DIR) }

        scriptsDir()?.delete()
        scriptOptsDir()?.delete()
        shadersDir()?.delete()

        // Then, copy the user files from the Aniyomi directory
        if (advancedPlayerPreferences.mpvUserFiles().get()) {
            storageManager.getScriptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getScriptOptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptOptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getShadersDirectory()?.listFiles()?.forEach { file ->
                val outFile = shadersDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
        }

        // Copy over the bridge file
        val luaFile = scriptsDir()?.createFile("aniyomi.lua")
        val luaBridge = assets.open("aniyomi.lua")
        luaFile?.openOutputStream()?.bufferedWriter()?.use { scriptLua ->
            luaBridge.bufferedReader().use { scriptLua.write(it.readText()) }
        }
    }

    private fun copyAssets(mpvDir: UniFile) {
        val assetManager = this.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
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

    private fun copyFontsDirectory(mpvDir: UniFile) {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        lifecycleScope.launchIO {
            val fontsDirectory = mpvDir.createDirectory(MPV_FONTS_DIR)!!

            storageManager.getFontsDirectory()?.listFiles()?.forEach { font ->
                val outFile = fontsDirectory.createFile(font.name)
                outFile?.let { destinationFile ->
                    try {
                        font.openInputStream().use { input ->
                            destinationFile.openOutputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: IOException) {
                        logcat(LogPriority.ERROR, e) { "Failed to copy font file: ${font.name}" }
                    }
                }
            }

            mpv.setPropertyString("sub-fonts-dir", fontsDirectory.filePath!!)
            mpv.setPropertyString("osd-fonts-dir", fontsDirectory.filePath!!)
        }
    }

    fun setupCustomButtons() {
        viewModel.viewModelScope.launchIO {
            val buttons = viewModel.getCustomButtons()
            viewModel.setCustomButtons(buttons)

            val scriptsDir = {
                UniFile.fromFile(applicationContext.filesDir)
                    ?.createDirectory(MPV_DIR)
                    ?.createDirectory(MPV_SCRIPTS_DIR)
            }

            val primaryButtonId = viewModel.primaryButton.value?.id ?: 0L

            val customButtonsContent = buildString {
                append(
                    """
                        local lua_modules = mp.find_config_file('scripts')
                        if lua_modules then
                            package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir()!!.filePath}' .. '/?.lua'
                        end
                        local aniyomi = require 'aniyomi'
                    """.trimIndent(),
                )

                buttons.forEach { button ->
                    append(
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

            val file = scriptsDir()?.createFile("custombuttons.lua")
            file?.openOutputStream()?.bufferedWriter()?.use {
                it.write(customButtonsContent)
            }

            // ANK -->
            file?.filePath?.let {
                mpv.command("load-script", it)
            }
            // ANK <--
        }
    }

    private fun setupPlayerAudio() {
        with(audioPreferences) {
            audioChannels().get().let { mpv.setPropertyString(it.property, it.value) }

            val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
                it.setAudioAttributes(
                    AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
                )
                it.setOnAudioFocusChangeListener(audioFocusChangeListener)
            }.build()
            AudioManagerCompat.requestAudioFocus(audioManager, request).let {
                if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
                audioFocusRequest = request
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = viewModel.paused ?: false
                viewModel.pause()
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) viewModel.unpause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mpv.command("multiply", "volume", "0.5")
                restoreAudioFocus = {
                    mpv.command("multiply", "volume", "2")
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreAudioFocus()
                restoreAudioFocus = {}
            }

            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                logcat(LogPriority.DEBUG) { "didn't get audio focus" }
            }
        }
    }

    override fun onResume() {
        // Reconnect cast if it was active
        castManager.apply {
            reconnect()
            registerSessionListener()
        }

        // AM (DISCORD) -->
        updateDiscordRPC(exitingPlayer = false)
        // <-- AM (DISCORD)

        if (!player.isExiting) {
            super.onResume()
            return
        }

        player.isExiting = false
        super.onResume()

        viewModel.currentVolume.update {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.changeVideoAspect(playerPreferences.aspectState().get())
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    fun showToast(message: String) {
        runOnUiThread { toast(message) }
    }

    // A bunch of observers

    @Suppress("unused")
    internal fun onObserverEvent(property: String, value: Long) {
        if (player.isExiting) return
    }

    @Suppress("unused")
    internal fun onObserverEvent(property: String) {
        if (player.isExiting) return
    }

    internal fun onObserverEvent(property: String, value: Boolean) {
        if (player.isExiting) return
        when (property) {
            "pause" if value -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // ANK -->
                updateDiscordRPC(exitingPlayer = false)
                // ANK <--
            }
            "pause" -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // ANK -->
                updateDiscordRPC(exitingPlayer = false)
                // ANK <--
            }
            "eof-reached" -> endFile(value)
        }
    }

    internal fun onObserverEvent(property: String, value: String) {
        if (player.isExiting) return
        when (property.substringBeforeLast("/")) {
            "user-data/aniyomi" -> viewModel.handleLuaInvocation(property, value)
        }
    }

    @Suppress("unused")
    internal fun onObserverEvent(property: String, value: Double) {
        if (player.isExiting) return
        when (property) {
            "video-params/aspect" -> if (isPipSupportedAndEnabled) createPipParams()
        }
    }

    @Suppress("unused")
    internal fun onObserverEvent(property: String, value: MPVNode) {
        if (player.isExiting) return
    }

    internal fun event(eventId: Int, node: MPVNode) {
        if (player.isExiting) return
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                viewModel.viewModelScope.launchIO { fileLoaded() }
            }
            MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                val errorNode = node.asMap()?.get("file_error") ?: return
                var errorMessage = errorNode.asString() ?: "Error: File ended"

                val httpError = playerObserver.httpError
                if (!httpError.isNullOrEmpty()) {
                    errorMessage += ": $httpError"
                    playerObserver.httpError = null
                }

                logcat(LogPriority.ERROR) { errorMessage }
                showToast(errorMessage)

                viewModel.setCurrentVideoError()

                if (playerPreferences.switchOnFailure().get()) {
                    viewModel.loadBestVideo()
                } else {
                    viewModel.setIsStopped(true)
                }
            }
        }
    }

    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val anime = viewModel.currentAnime.value
            val episode = viewModel.currentEpisode.value

            if (anime != null && episode != null) {
                builder.setTitle(anime.title).setSubtitle(episode.name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences.pipOnExit().get()
            builder.setAutoEnterEnabled(viewModel.paused == false && autoEnter)
            builder.setSeamlessResizeEnabled(viewModel.paused == false && autoEnter)
        }
        builder.setActions(
            createPipActions(
                context = this,
                isPaused = viewModel.paused ?: true,
                replaceWithPrevious = playerPreferences.pipReplaceWithPrevious().get(),
                playlistCount = viewModel.currentPlaylist.value.size,
                playlistPosition = viewModel.getCurrentEpisodeIndex(),
            ),
        )
        builder.setSourceRectHint(pipRect)
        mpv.getPropertyInt("video-params/h")?.let { height ->
            val width = height * player.getVideoOutAspect()!!
            val rational = Rational(height, width.toInt()).toFloat()
            if (rational in 0.42..2.38) builder.setAspectRatio(Rational(width.toInt(), height))
        }
        return builder.build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let {
                unregisterReceiver(pipReceiver)
                pipReceiver = null
            }
        } else {
            setPictureInPictureParams(createPipParams())
            viewModel.hideControls()
            viewModel.hideSeekBar()
            viewModel.isBrightnessSliderShown.update { false }
            viewModel.isVolumeSliderShown.update { false }
            viewModel.sheetShown.update { Sheets.None }
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                    when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                        PIP_PAUSE -> viewModel.pause()
                        PIP_PLAY -> viewModel.unpause()
                        PIP_NEXT -> viewModel.changeEpisode(false)
                        PIP_PREVIOUS -> viewModel.changeEpisode(true)
                        PIP_SKIP -> viewModel.seekBy(10)
                    }
                    setPictureInPictureParams(createPipParams())
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }
        }

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun setupPlayerOrientation() {
        if (player.isExiting) return
        requestedOrientation = when (playerPreferences.defaultPlayerOrientationType().get()) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((player.getVideoOutAspect() ?: 0.0) > 1.0) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.changeVolumeBy(1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.changeVolumeBy(-1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
            KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
            KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

            // other keys should be bound by the user in input.conf ig
            else -> {
                event?.let { player.onKey(it) }
                super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (player.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun setupMediaSession() {
        val previousAction = gesturePreferences.mediaPreviousGesture().get()
        val playAction = gesturePreferences.mediaPlayPauseGesture().get()
        val nextAction = gesturePreferences.mediaNextGesture().get()

        mediaSession = MediaSession(this, "PlayerActivity").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPlay()
                                viewModel.unpause()
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                mpv.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onPause() {
                        // Cast -->
                        castManager.apply {
                            // Release resources only if not in PIP
                            if (!isInPictureInPictureMode) {
                                unregisterSessionListener()
                            }

                            // If you are transmitting, keep an active session
                            if (castState.value == CastManager.CastState.CONNECTED) {
                                maintainCastSessionBackground()
                            }
                        }
                        //
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPause()
                                viewModel.pause()
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                mpv.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onSkipToPrevious() {
                        when (previousAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.leftSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                mpv.command("keypress", CustomKeyCodes.MediaPrevious.keyCode)
                            }

                            SingleActionGesture.Switch -> viewModel.changeEpisode(true)
                        }
                    }

                    override fun onSkipToNext() {
                        when (nextAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.rightSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                mpv.command("keypress", CustomKeyCodes.MediaNext.keyCode)
                            }

                            SingleActionGesture.Switch -> viewModel.changeEpisode(false)
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        isActive = false
                        this@PlayerActivity.onStop()
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SKIP_TO_NEXT,
                    )
                    .build(),
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    // ==== END MPVKT ====

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            viewModel.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Switches to the episode based on [episodeId],
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    internal fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false) {
        viewModel.sheetShown.update { _ -> Sheets.None }
        viewModel.panelShown.update { _ -> Panels.None }
        viewModel.pause()
        viewModel.isLoading.update { _ -> true }
        viewModel.resetHosterState()

        lifecycleScope.launch {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)
            viewModel.cancelHosterVideoLinksJob()

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()
            val switchMethod = viewModel.loadEpisode(episodeId)

            viewModel.updateIsLoadingHosters(false)

            when (switchMethod) {
                null -> {
                    if (viewModel.currentAnime.value != null && !autoPlay) {
                        launchUI { toast(AYMR.strings.no_next_episode) }
                    }
                    viewModel.isLoading.update { _ -> false }
                }

                else -> {
                    if (switchMethod.hosterList != null) {
                        when {
                            switchMethod.hosterList.isEmpty() -> setInitialEpisodeError(
                                PlayerViewModel.ExceptionWithStringResource(
                                    "Hoster list is empty",
                                    AYMR.strings.no_hosters,
                                ),
                            )
                            else -> {
                                viewModel.loadHosters(
                                    source = switchMethod.source,
                                    hosterList = switchMethod.hosterList,
                                    hosterIndex = -1,
                                    videoIndex = -1,
                                )
                            }
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                    }

                    if (isInPictureInPictureMode && pipEpisodeToasts) {
                        launchUI { toast(switchMethod.episodeTitle) }
                    }
                }
            }
        }

        viewModel.updateHasPreviousEpisode(
            viewModel.getCurrentEpisodeIndex() != 0,
        )
        viewModel.updateHasNextEpisode(
            viewModel.getCurrentEpisodeIndex() != viewModel.currentPlaylist.value.size - 1,
        )
    }

    fun setVideo(video: Video?, position: Long? = null) {
        if (player.isExiting) return
        if (video == null) return

        viewModel.setIsStopped(false)
        setHttpOptions(video)

        if (viewModel.isLoadingEpisode.value) {
            viewModel.currentEpisode.value?.let { episode ->
                val preservePos = playerPreferences.preserveWatchingPosition().get()
                val resumePosition = position
                    ?: if (episode.seen && !preservePos) {
                        0L
                    } else {
                        episode.last_second_seen
                    }
                mpv.command("set", "start", "${resumePosition / 1000F}")
            }
        } else {
            viewModel.pos?.let {
                mpv.command("set", "start", "$it")
            }
        }
        if (video.videoUrl.startsWith(TorrentServerUtils.hostUrl) ||
            video.videoUrl.startsWith("magnet") ||
            video.videoUrl.endsWith(".torrent")
        ) {
            launchIO {
                TorrentServerService.start()
                TorrentServerService.wait(10)
                torrentLinkHandler(video.videoUrl, video.videoTitle)
            }
        } else {
            val videoOptions = video.mpvArgs.joinToString(",") { (option, value) ->
                val sanitizedOption = sanitizeFFmpegKey(option)
                val sanitizedValue = sanitizeFFmpegValue(value)
                "$sanitizedOption=\"${sanitizedValue.replace("\"", "\\\"")}\""
            }

            mpv.command(
                "loadfile",
                parseVideoUrl(video.videoUrl)!!,
                "replace",
                "0",
                videoOptions,
            )
        }

        // AM (DISCORD) -->
        updateDiscordRPC(exitingPlayer = false)
        // <-- AM (DISCORD)
    }

    private fun torrentLinkHandler(videoUrl: String, quality: String) {
        var index = 0

        // check if link is from localSource
        if (videoUrl.startsWith("content://")) {
            val videoInputStream = applicationContext.contentResolver.openInputStream(videoUrl.toUri())
            val torrent = TorrentServerApi.uploadTorrent(videoInputStream!!, quality, "", "", false)
            val torrentUrl = TorrentServerUtils.getTorrentPlayLink(torrent, 0)
            mpv.command("loadfile", torrentUrl)
            return
        }

        // check if link is from magnet, in that check if index is present
        if (videoUrl.startsWith("magnet")) {
            if (videoUrl.contains("index=")) {
                index = try {
                    videoUrl.substringAfter("index=").toInt()
                } catch (_: NumberFormatException) {
                    0
                }
            }
        }

        val currentTorrent = TorrentServerApi.addTorrent(videoUrl, quality, "", "", false)
        val videoTorrentUrl = TorrentServerUtils.getTorrentPlayLink(currentTorrent, index)
        mpv.command("loadfile", videoTorrentUrl)
    }

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        if (error is PlayerViewModel.ExceptionWithStringResource) {
            toast(error.stringResource)
        } else {
            toast(error.message)
        }
        logcat(LogPriority.ERROR, error)
        finish()
    }

    private fun parseVideoUrl(videoUrl: String?): String? {
        return videoUrl?.toUri()?.resolveUri(this)
            ?: videoUrl
    }

    private fun setHttpOptions(video: Video) {
        if (viewModel.isEpisodeOnline() != true) return
        val source = viewModel.currentSource.value as? HttpSource ?: return

        val headers = (video.headers ?: source.headers)
            .toMultimap()
            .mapValues { it.value.firstOrNull() ?: "" }
            .toMutableMap()

        val httpHeaderString = headers.map {
            it.key + ": " + it.value.replace(",", "\\,")
        }.joinToString(",")

        mpv.setOptionString("http-header-fields", httpHeaderString)

        // need to fix the cache
        // MPVLib.setOptionString("cache-on-disk", "yes")
        // val cacheDir = File(applicationContext.filesDir, "media").path
        // MPVLib.setOptionString("cache-dir", cacheDir)
    }

    fun onTrackLoadedFailure(url: String) {
        viewModel.onTrackLoadedFailure(url)
    }

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(AYMR.strings.share_screenshot_info, anime.title, episode.name, seconds),
        )
        startActivity(intent)
    }

    /**
     * Called from the presenter when a screenshot is saved or fails. It shows a message
     * or logs the event depending on the [result].
     */
    private fun onSaveImageResult(result: PlayerViewModel.SaveImageResult) {
        when (result) {
            is PlayerViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is PlayerViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a screenshot is set as art or fails.
     * It shows a different message depending on the [result].
     */
    private fun onSetAsArtResult(result: SetAsCover, artType: ArtType) {
        toast(
            when (result) {
                SetAsCover.Success ->
                    when (artType) {
                        ArtType.Cover -> MR.strings.cover_updated
                        ArtType.Background -> AYMR.strings.background_updated
                        ArtType.Thumbnail -> AYMR.strings.thumbnail_updated
                    }
                SetAsCover.AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                SetAsCover.Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    private fun changeVideoAspect(aspect: VideoAspect) {
        var ratio = -1.0
        val pan: Double
        when (aspect) {
            VideoAspect.Crop -> {
                pan = 1.0
            }

            VideoAspect.Fit -> {
                pan = 0.0
                mpv.setPropertyDouble("panscan", 0.0)
            }

            VideoAspect.Stretch -> {
                val dm = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(dm)
                ratio = dm.widthPixels / dm.heightPixels.toDouble()
                pan = 0.0
            }
        }
        viewModel.setAspect(aspect, pan, ratio)
    }

    private fun cycleRotations() {
        requestedOrientation = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            -> {
                playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorPortrait)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            else -> {
                playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorLandscape)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    private fun toggleShowSoftwareKeyboard() {
        if (inputMethodManager.isActive) {
            forceHideSoftwareKeyboard()
        } else {
            forceShowSoftwareKeyboard()
        }
    }

    private fun forceShowSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun forceHideSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    private fun fileLoaded() {
        if (player.isExiting) return

        setMpvOptions()
        setMpvMediaTitle()
        setupPlayerOrientation()
        setupChapters()
        viewModel.setPausedState()
        viewModel.updateIsLoadingEpisode(false)

        // aniSkip stuff
        viewModel.viewModelScope.launchIO {
            if (viewModel.introSkipEnabled && playerPreferences.aniSkipEnabled().get() &&
                !(playerPreferences.disableAniSkipOnChapters().get() && viewModel.getChapterCount() > 0)
            ) {
                viewModel.aniSkipResponse(viewModel.duration)?.let {
                    viewModel.addTimestamps(it)
                }
            }
        }
    }

    private fun setMpvOptions() {
        if (player.isExiting) return
        val video = viewModel.currentVideo.value ?: return

        // Only check for `MPV_ARGS_TAG` on downloaded videos
        if (listOf("file", "content", "data").none { video.videoUrl.startsWith(it) }) {
            return
        }

        try {
            val metadata = mpv.getPropertyString("metadata")?.let {
                Json.decodeFromString<Map<String, String>>(it)
            } ?: return

            val opts = metadata[Video.MPV_ARGS_TAG]
                ?.split(";")
                ?.map { it.split("=", limit = 2) }
                ?: return

            opts.forEach { parts ->
                // AY -->
                if (parts.size == 2) {
                    val (option, value) = parts
                    // <-- AY
                    mpv.setPropertyString(option, value)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read video metadata" }
        }
    }

    private fun setupChapters() {
        if (player.isExiting) return

        val timestamps = viewModel.currentVideo.value?.timestamps?.takeIf { it.isNotEmpty() }
            ?.map { timestamp ->
                if (timestamp.name.isEmpty() && timestamp.type != ChapterType.Other) {
                    timestamp.copy(
                        name = timestamp.type.getStringRes()?.let(::stringResource) ?: "",
                    )
                } else {
                    timestamp
                }
            }
            ?: return

        viewModel.addTimestamps(timestamps)
    }

    private fun setMpvMediaTitle() {
        if (player.isExiting) return
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        // Write to mpv table
        mpv.setPropertyString("user-data/current-anime/episode-title", episode.name)

        val epNumber = episode.episode_number.let { number ->
            if (ceil(number) == floor(number)) number.toInt() else number
        }.toString().padStart(2, '0')

        val title = stringResource(
            AYMR.strings.mpv_media_title,
            anime.title,
            epNumber,
            episode.name,
        )

        mpv.setPropertyString("force-media-title", title)
    }

    private fun endFile(eofReached: Boolean) {
        if (eofReached && playerPreferences.autoplayEnabled().get()) {
            viewModel.changeEpisode(previous = false, autoPlay = true)
        }
    }

    // AM (DISCORD) -->
    private fun updateDiscordRPC(exitingPlayer: Boolean) {
        if (!connectionsPreferences.enableDiscordRPC().get()) return

        DiscordRPCService.discordScope.launchIO {
            try {
                if (!exitingPlayer) {
                    // ANK -->
                    val timePos = viewModel.pos ?: return@launchIO
                    val duration = viewModel.duration ?: 1440
                    // ANK <--

                    val currentPosition = timePos.toLong() * 1000
                    val startTimestamp = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis() - currentPosition
                    }
                    val endTimestamp = Calendar.getInstance().apply {
                        timeInMillis = startTimestamp.timeInMillis
                        add(Calendar.SECOND, duration)
                    }

                    val anime = viewModel.currentAnime.value ?: return@launchIO
                    val episode = viewModel.currentEpisode.value ?: return@launchIO

                    DiscordRPCService.setPlayerActivity(
                        context = this@PlayerActivity,
                        PlayerData(
                            incognitoMode = viewModel.currentSource.value?.isNsfw() == true || viewModel.incognitoMode,
                            animeId = anime.id,
                            animeTitle = anime.ogTitle,
                            thumbnailUrl = anime.thumbnailUrl.takeIf { UrlUtils.isOnlineUrl(it) } ?: anime.ogThumbnailUrl,
                            episodeNumber = if (connectionsPreferences.useChapterTitles().get()) {
                                episode.name
                            } else {
                                episode.episode_number.toString()
                            },
                            startTimestamp = startTimestamp.timeInMillis,
                            endTimestamp = endTimestamp.timeInMillis,
                        ),
                    )
                } else {
                    with(DiscordRPCService) {
                        setScreen(this@PlayerActivity)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Error updating Discord RPC: ${e.message}" }
            }
        }
    }
}

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

package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.view.KeyCharacterMap
import android.view.KeyEvent
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.player.controls.components.panels.toColorHexString
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitleAssOverride
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPV
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.injectLazy

class AniyomiMPVView(context: Context, attributes: AttributeSet?) : BaseMPVView(context, attributes) {

    private val playerPreferences: PlayerPreferences by injectLazy()
    private val decoderPreferences: DecoderPreferences by injectLazy()
    private val subtitlePreferences: SubtitlePreferences by injectLazy()
    private val audioPreferences: AudioPreferences by injectLazy()
    private val advancedPreferences: AdvancedPlayerPreferences by injectLazy()
    private val networkPreferences: NetworkPreferences by injectLazy()

    var isExiting = false

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoOutAspect(): Double? {
        return mpv?.getPropertyDouble("video-params/aspect")?.let {
            if (it < 0.001) return 0.0
            if ((mpv?.getPropertyInt("video-params/rotate") ?: 0) % 180 == 90) 1.0 / it else it
        }
    }

    fun init(mpvInst: MPV) {
        this.mpv = mpvInst
        setVo(if (decoderPreferences.gpuNext().get()) "gpu-next" else "gpu")
        mpv?.setPropertyBoolean("pause", true)
        mpv?.setOptionString("profile", "fast")
        mpv?.setOptionString("hwdec", if (decoderPreferences.tryHWDecoding().get()) "auto" else "no")

        if (decoderPreferences.useYUV420P().get()) {
            mpv?.setOptionString("vf", "format=yuv420p")
        }
        mpv?.setOptionString("msg-level", "all=" + if (networkPreferences.verboseLogging().get()) "v" else "warn")

        mpv?.setPropertyBoolean("input-default-bindings", true)

        mpv?.setOptionString("idle", "yes")
        mpv?.setOptionString("ytdl", "no")
        mpv?.setOptionString("tls-verify", "yes")
        mpv?.setOptionString("tls-ca-file", "${context.filesDir.path}/${PlayerActivity.MPV_DIR}/cacert.pem")

        // We handle selecting this in the viewmodel
        mpv?.setOptionString("sid", "no")
        mpv?.setOptionString("aid", "no")

        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        mpv?.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        mpv?.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        mpv?.setOptionString("screenshot-directory", screenshotDir.path)

        VideoFilters.entries.forEach {
            mpv?.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
        }

        mpv?.setOptionString("speed", playerPreferences.playerSpeed().get().toString())
        // workaround for <https://github.com/mpv-player/mpv/issues/14651>
        mpv?.setOptionString("vd-lavc-film-grain", "cpu")

        postInitOptions()
        setupSubtitlesOptions()
        setupAudioOptions()
        observeProperties()
    }

    fun observeProperties() {
        for ((name, format) in observedProps) mpv?.observeProperty(name, format)
    }

    fun postInitOptions() {
        when (decoderPreferences.debanding().get()) {
            Debanding.None -> {}
            Debanding.CPU -> mpv?.setOptionString("vf", "gradfun=radius=12")
            Debanding.GPU -> mpv?.setOptionString("deband", "yes")
        }

        advancedPreferences.playerStatisticsPage().get().let {
            if (it != 0) {
                mpv?.command("script-binding", "stats/display-stats-toggle")
                mpv?.command("script-binding", "stats/display-page-$it")
            }
        }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
            return false
        }

        var mapped = KeyMapping[event.keyCode]
        if (mapped == null) {
            // Fallback to produced glyph
            if (!event.isPrintingKey) {
                if (event.repeatCount == 0) {
                    logcat(LogPriority.DEBUG) { "Unmapped non-printable key ${event.keyCode}" }
                }
                return false
            }

            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
                return false // dead key
            }
            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0) {
            return true // eat event but ignore it, mpv has its own key repeat
        }

        val mod: MutableList<String> = mutableListOf()
        event.isShiftPressed && mod.add("shift")
        event.isCtrlPressed && mod.add("ctrl")
        event.isAltPressed && mod.add("alt")
        event.isMetaPressed && mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        mpv?.command(action, mod.joinToString("+"))

        return true
    }

    private val observedProps = mapOf(
        "pause" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "video-params/aspect" to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "eof-reached" to MPV.mpvFormat.MPV_FORMAT_FLAG,

        "user-data/aniyomi/show_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/toggle_ui" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/show_panel" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/software_keyboard" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/set_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/reset_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/toggle_button" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/switch_episode" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/pause" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_by" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_to" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_by_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_to_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/launch_int_picker" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/show_seek_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
    )

    private fun setupAudioOptions() {
        mpv?.setOptionString("alang", audioPreferences.preferredAudioLanguages().get())
        mpv?.setOptionString("audio-delay", (audioPreferences.audioDelay().get() / 1000.0).toString())
        mpv?.setOptionString("audio-pitch-correction", audioPreferences.enablePitchCorrection().get().toString())
        mpv?.setOptionString("volume-max", (audioPreferences.volumeBoostCap().get() + 100).toString())
    }

    private fun setupSubtitlesOptions() {
        mpv?.setOptionString("sub-delay", (subtitlePreferences.subtitlesDelay().get() / 1000.0).toString())
        mpv?.setOptionString("sub-speed", subtitlePreferences.subtitlesSpeed().get().toString())
        mpv?.setOptionString(
            "secondary-sub-delay",
            (subtitlePreferences.subtitlesSecondaryDelay().get() / 1000.0).toString(),
        )

        mpv?.setOptionString("sub-font", subtitlePreferences.subtitleFont().get())
        subtitlePreferences.overrideSubsASS().get().let {
            mpv?.setOptionString("sub-ass-override", it.value)
            if (it != SubtitleAssOverride.No) {
                mpv?.setOptionString("sub-ass-justify", "yes")
            }
        }
        mpv?.setOptionString("sub-font-size", subtitlePreferences.subtitleFontSize().get().toString())
        mpv?.setOptionString("sub-bold", if (subtitlePreferences.boldSubtitles().get()) "yes" else "no")
        mpv?.setOptionString("sub-italic", if (subtitlePreferences.italicSubtitles().get()) "yes" else "no")
        mpv?.setOptionString("sub-justify", subtitlePreferences.subtitleJustification().get().value)
        mpv?.setOptionString("sub-color", subtitlePreferences.textColorSubtitles().get().toColorHexString())
        mpv?.setOptionString(
            "sub-back-color",
            subtitlePreferences.backgroundColorSubtitles().get().toColorHexString(),
        )
        mpv?.setOptionString("sub-outline-color", subtitlePreferences.borderColorSubtitles().get().toColorHexString())
        mpv?.setOptionString("sub-outline-size", subtitlePreferences.subtitleBorderSize().get().toString())
        mpv?.setOptionString("sub-border-style", subtitlePreferences.borderStyleSubtitles().get().value)
        mpv?.setOptionString("sub-shadow-offset", subtitlePreferences.shadowOffsetSubtitles().get().toString())
        mpv?.setOptionString("sub-pos", subtitlePreferences.subtitlePos().get().toString())
        mpv?.setOptionString("sub-scale", subtitlePreferences.subtitleFontScale().get().toString())
    }
}

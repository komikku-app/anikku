package eu.kanade.tachiyomi.ui.player.settings

import eu.kanade.tachiyomi.ui.player.Debanding
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class DecoderPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun tryHWDecoding() = preferenceStore.getBoolean("pref_try_hwdec", true)
    fun gpuNext() = preferenceStore.getBoolean("pref_gpu_next", false)
    fun useYUV420P() = preferenceStore.getBoolean("use_yuv420p", true)

    fun debanding() = preferenceStore.getEnum("pref_video_debanding", Debanding.None)
    fun debandIterations() = preferenceStore.getInt("deband_iterations", 1)
    fun debandThreshold() = preferenceStore.getInt("deband_threshold", 48)
    fun debandRange() = preferenceStore.getInt("deband_range", 16)
    fun debandGrain() = preferenceStore.getInt("deband_grain", 32)

    // Non-preferences

    fun brightnessFilter() = preferenceStore.getInt("pref_player_filter_brightness")
    fun saturationFilter() = preferenceStore.getInt("pref_player_filter_saturation")
    fun contrastFilter() = preferenceStore.getInt("pref_player_filter_contrast")
    fun gammaFilter() = preferenceStore.getInt("pref_player_filter_gamma")
    fun hueFilter() = preferenceStore.getInt("pref_player_filter_hue")
}

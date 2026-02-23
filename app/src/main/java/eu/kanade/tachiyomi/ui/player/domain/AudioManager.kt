package eu.kanade.tachiyomi.ui.player.domain

import android.content.Context
import android.media.AudioManager as AndroidAudioManager

class AudioManager(
    private val context: Context,
) {
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AndroidAudioManager }

    fun getVolume(): Int {
        return audioManager.getStreamVolume(AndroidAudioManager.STREAM_MUSIC)
    }

    fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AndroidAudioManager.STREAM_MUSIC)
    }

    fun setVolume(volume: Int) {
        audioManager.setStreamVolume(
            AndroidAudioManager.STREAM_MUSIC,
            volume,
            0,
        )
    }
}

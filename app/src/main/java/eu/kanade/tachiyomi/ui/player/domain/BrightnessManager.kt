package eu.kanade.tachiyomi.ui.player.domain

import android.content.Context
import android.provider.Settings
import eu.kanade.tachiyomi.ui.player.normalize

class BrightnessManager(
    private val context: Context,
) {
    fun getCurrentBrightness(): Float {
        return runCatching {
            Settings.System.getFloat(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .normalize(0f, 255f, 0f, 1f)
        }.getOrElse { 0f }
    }
}

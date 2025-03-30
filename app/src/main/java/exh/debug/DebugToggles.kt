package exh.debug

import eu.kanade.core.preference.PreferenceMutableState
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy
import java.util.Locale

enum class DebugToggles(val default: Boolean) {
    // KMK -->
    HIDE_COVER_IMAGE_ONLY_SHOW_COLOR(false),
    // KMK <--
    ;

    private val prefKey = "eh_debug_toggle_${name.lowercase(Locale.US)}"

    var enabled: Boolean
        get() = preferenceStore.getBoolean(prefKey, default).get()
        set(value) {
            preferenceStore.getBoolean(prefKey).set(value)
        }

    fun asPref(scope: CoroutineScope) = PreferenceMutableState(preferenceStore.getBoolean(prefKey, default), scope)

    companion object {
        private val preferenceStore: PreferenceStore by injectLazy()
    }
}

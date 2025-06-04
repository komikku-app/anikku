package eu.kanade.domain.ui.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIcon(val aliasName: String) {
    DEFAULT("eu.kanade.tachiyomi.ui.main.MainActivityDefault"),
    ANIKUN1("eu.kanade.tachiyomi.ui.main.MainActivityAnikun1"),
    ANIKUN2("eu.kanade.tachiyomi.ui.main.MainActivityAnikun2"),
    ANIKUN3("eu.kanade.tachiyomi.ui.main.MainActivityAnikun3"),
    ONIGIRI1("eu.kanade.tachiyomi.ui.main.MainActivityOnigiri1"),
    ONIGIRI2("eu.kanade.tachiyomi.ui.main.MainActivityOnigiri2"),
}

fun setAppIcon(context: Context, appIcon: AppIcon) {
    val pm = context.packageManager
    val packageName = context.packageName

    // List of all activity aliases
    val allAliases = AppIcon.entries.map { it.aliasName }

    // Enable the selected alias and disable all others
    for (alias in allAliases) {
        val component = ComponentName(packageName, alias)
        val newState = if (alias == appIcon.aliasName) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        pm.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP,
        )
    }
}

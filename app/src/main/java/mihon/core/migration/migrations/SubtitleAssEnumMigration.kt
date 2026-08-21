package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.ui.player.settings.SubtitleAssOverride
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class SubtitleAssEnumMigration : Migration {
    override val version = 8f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (!prefs.contains(OLD_KEY)) return false

        val overrideAss = preferenceStore.getBoolean(OLD_KEY, false).get()
        preferenceStore.getEnum(NEW_KEY, SubtitleAssOverride.No).set(
            if (overrideAss) SubtitleAssOverride.Force else SubtitleAssOverride.No,
        )
        prefs.edit {
            remove(OLD_KEY)
        }

        return true
    }

    companion object {
        private const val OLD_KEY = "pref_override_subtitles_ass"
        private const val NEW_KEY = "pref_override_subtitles_ass_enum"
    }
}

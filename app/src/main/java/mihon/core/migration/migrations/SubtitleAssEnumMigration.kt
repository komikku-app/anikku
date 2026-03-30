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

        val overrideAss = preferenceStore.getBoolean("pref_override_subtitles_ass", false).get()
        prefs.edit {
            remove("pref_override_subtitles_ass")
            preferenceStore.getEnum("pref_override_subtitles_ass_enum", SubtitleAssOverride.No).set(
                if (overrideAss) SubtitleAssOverride.Force else SubtitleAssOverride.No,
            )
        }

        return true
    }
}

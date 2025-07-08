package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.presentation.util.isValidLanguageCode
import eu.kanade.presentation.util.parseCommaSeparatedList
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class PrefLangMigration : Migration {
    override val version = 5f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val audioPreferences = migrationContext.get<AudioPreferences>() ?: return false
        val subtitlePreferences = migrationContext.get<SubtitlePreferences>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val updates = listOf(
            audioPreferences.preferredAudioLanguages(),
            subtitlePreferences.preferredSubLanguages(),
        ).mapNotNull { pref ->
            if (pref.isSet()) {
                prefs.getString(pref.key(), "")
                    ?.parseCommaSeparatedList()
                    ?.filter { it.isValidLanguageCode() }?.joinToString(",")
                    ?.let { pref.key() to it }
            } else {
                null
            }
        }

        if (updates.isNotEmpty()) {
            prefs.edit {
                updates.forEach { (key, value) ->
                    putString(key, value)
                }
            }
        }

        return true
    }
}

package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsSubtitleScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_player_subtitle

    @Composable
    override fun getPreferences(): List<Preference> {
        val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }

        val langPref = subtitlePreferences.preferredSubLanguages()
        val whitelist = subtitlePreferences.subtitleWhitelist()
        val blacklist = subtitlePreferences.subtitleBlacklist()

        return listOf(
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = langPref,
                title = stringResource(MR.strings.pref_player_subtitle_lang),
                dialogSubtitle = stringResource(MR.strings.pref_player_subtitle_lang_info),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = whitelist,
                title = stringResource(MR.strings.pref_player_subtitle_whitelist),
                dialogSubtitle = stringResource(MR.strings.pref_player_subtitle_whitelist_info),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = blacklist,
                title = stringResource(MR.strings.pref_player_subtitle_blacklist),
                dialogSubtitle = stringResource(MR.strings.pref_player_subtitle_blacklist_info),
            ),
            getJimakuGroup(subtitlePreferences),
        )
    }

    @Composable
    private fun getJimakuGroup(subtitlePreferences: SubtitlePreferences): Preference.PreferenceGroup {
        val jimakuEnabled by subtitlePreferences.jimakuEnabled().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_jimaku_group),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = subtitlePreferences.jimakuEnabled(),
                    title = stringResource(MR.strings.pref_jimaku_enabled),
                    subtitle = stringResource(MR.strings.pref_jimaku_enabled_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = subtitlePreferences.jimakuApiKey(),
                    title = stringResource(MR.strings.pref_jimaku_api_key),
                    enabled = jimakuEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = subtitlePreferences.jimakuAutoFetch(),
                    title = stringResource(MR.strings.pref_jimaku_auto_fetch),
                    subtitle = stringResource(MR.strings.pref_jimaku_auto_fetch_summary),
                    enabled = jimakuEnabled,
                ),
            ),
        )
    }
}

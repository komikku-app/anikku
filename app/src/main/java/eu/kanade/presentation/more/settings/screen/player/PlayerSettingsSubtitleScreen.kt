package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.util.getInvalidLanguageError
import eu.kanade.presentation.util.isLanguageListValid
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsSubtitleScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = PlayerSettingsSubtitleScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_subtitle

    @Composable
    override fun getPreferences(): List<Preference> {
        val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }

        val langPref = subtitlePreferences.preferredSubLanguages()
        val whitelist = subtitlePreferences.subtitleWhitelist()
        val blacklist = subtitlePreferences.subtitleBlacklist()
        val blackBars = subtitlePreferences.subtitleBlackBars()
        val systemFonts = subtitlePreferences.subtitleSystemFonts

        return listOf(
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = langPref,
                title = stringResource(AYMR.strings.pref_player_subtitle_lang),
                dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_lang_info),
                validate = { pref ->
                    isLanguageListValid(pref)
                },
                errorMessage = { pref ->
                    getInvalidLanguageError(pref) { invalidLang ->
                        stringResource(
                            AYMR.strings.pref_player_subtitle_invalid_lang,
                            invalidLang,
                        )
                    }
                },
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = whitelist,
                title = stringResource(AYMR.strings.pref_player_subtitle_whitelist),
                dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_whitelist_info),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = blacklist,
                title = stringResource(AYMR.strings.pref_player_subtitle_blacklist),
                dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_blacklist_info),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = blackBars,
                title = stringResource(AMMR.strings.player_pref_subtitle_black_bars),
                subtitle = stringResource(AMMR.strings.player_pref_subtitle_black_bars_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = systemFonts,
                title = stringResource(AMMR.strings.player_pref_subtitle_system_fonts),
            ),
            // ANK -->
            getJimakuGroup(subtitlePreferences),
            // ANK <--
        )
    }

    // ANK -->
    @Composable
    private fun getJimakuGroup(subtitlePreferences: SubtitlePreferences): Preference.PreferenceGroup {
        val jimakuEnabled by subtitlePreferences.jimakuEnabled().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AMR.strings.pref_jimaku_group),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitlePreferences.jimakuEnabled(),
                    title = stringResource(AMR.strings.pref_jimaku_enabled),
                    subtitle = stringResource(AMR.strings.pref_jimaku_enabled_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = subtitlePreferences.jimakuApiKey(),
                    title = stringResource(AMR.strings.pref_jimaku_api_key),
                    enabled = jimakuEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitlePreferences.jimakuAutoFetch(),
                    title = stringResource(AMR.strings.pref_jimaku_auto_fetch),
                    subtitle = stringResource(AMR.strings.pref_jimaku_auto_fetch_summary),
                    enabled = jimakuEnabled,
                ),
            ),
        )
    }
    // ANK <--
}

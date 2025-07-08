package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.util.getInvalidLanguageError
import eu.kanade.presentation.util.isLanguageListValid
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsSubtitleScreen : SearchableSettings {
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
        )
    }
}

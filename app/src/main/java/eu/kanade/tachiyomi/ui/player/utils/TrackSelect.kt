package eu.kanade.tachiyomi.ui.player.utils

import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.MissingResourceException

class TrackSelect(
    private val subtitlePreferences: SubtitlePreferences = Injekt.get(),
    private val audioPreferences: AudioPreferences = Injekt.get(),
) {

    fun getPreferredTrackIndex(tracks: List<VideoTrack>, subtitle: Boolean = true): VideoTrack? {
        val prefLangs = if (subtitle) {
            subtitlePreferences.preferredSubLanguages().get()
        } else {
            audioPreferences.preferredAudioLanguages().get()
        }.split(",").filter { it.isNotEmpty() }

        val whitelist = if (subtitle) {
            subtitlePreferences.subtitleWhitelist().get()
        } else {
            ""
        }.split(",").filter { it.isNotEmpty() }

        val blacklist = if (subtitle) {
            subtitlePreferences.subtitleBlacklist().get()
        } else {
            ""
        }.split(",").filter { it.isNotEmpty() }

        val locales = prefLangs.map(::Locale).ifEmpty {
            listOf(LocaleListCompat.getDefault()[0]!!)
        }

        val chosenLocale = locales.firstOrNull { locale ->
            tracks.any { t -> containsLang(t, locale) }
        } ?: return null

        val filtered = tracks.asSequence()
            .filterNot { track ->
                blacklist.any { track.name.contains(it, true) } ||
                    !containsLang(track, chosenLocale)
            }
            .toList()

        return filtered.firstOrNull { track ->
            whitelist.isNotEmpty() && whitelist.any { track.name.contains(it, true) }
        } ?: filtered.firstOrNull()
    }

    private fun containsLang(track: VideoTrack, locale: Locale): Boolean {
        try {
            val localName = locale.getDisplayName(locale)
            val englishName = locale.getDisplayName(Locale.ENGLISH).substringBefore(" (")
            val langRegex = Regex("""\b${locale.isO3Language}|${locale.language}\b""", RegexOption.IGNORE_CASE)

            return track.name.contains(localName, true) ||
                track.name.contains(englishName, true) ||
                track.language?.let { langRegex.find(it) != null } == true
        } catch (e: MissingResourceException) {
            return false
        }
    }
}

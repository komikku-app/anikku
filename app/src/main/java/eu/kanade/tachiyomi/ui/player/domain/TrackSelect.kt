package eu.kanade.tachiyomi.ui.player.domain

import androidx.core.os.LocaleListCompat
import eu.kanade.presentation.util.parseCommaSeparatedList
import eu.kanade.tachiyomi.ui.player.VideoTrack
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
        }.parseCommaSeparatedList()

        val whitelist = if (subtitle) {
            subtitlePreferences.subtitleWhitelist().get()
        } else {
            ""
        }.parseCommaSeparatedList()

        val blacklist = if (subtitle) {
            subtitlePreferences.subtitleBlacklist().get()
        } else {
            ""
        }.parseCommaSeparatedList()

        val locales = prefLangs.map(::Locale).ifEmpty {
            listOf(LocaleListCompat.getDefault()[0]!!)
        }

        val chosenLocale = locales.firstOrNull { locale ->
            tracks.any { t -> containsLang(t, locale) }
        }

        // ANK -->
        val filtered = tracks.asSequence()
            .filterNot { track ->
                blacklist.any { track.title.contains(it, true) }
            }
            .filter { track ->
                chosenLocale?.let { containsLang(track, it) } ?: true
            }

        whitelist.forEach { w ->
            filtered.firstOrNull { track ->
                track.title.contains(w, true)
            }?.let { return it }
        }

        return filtered.firstOrNull()
        // ANK <--
    }

    private fun containsLang(track: VideoTrack, locale: Locale): Boolean {
        // ANK -->
        try {
            // ANK <--
            val localName = locale.getDisplayName(locale)
            val englishName = locale.getDisplayName(Locale.ENGLISH).substringBefore(" (")
            // ANK -->
            // The ISO-639-2 code is matched as a prefix, since sources commonly glue it to
            // another word ("engsub"), while the two-letter code needs both boundaries so it
            // doesn't match inside an unrelated one.
            val langRegex = Regex("""\b${locale.isO3Language}|\b${locale.language}\b""", RegexOption.IGNORE_CASE)
            // ANK <--
            val trackTitle = track.title

            return trackTitle.contains(localName, true) ||
                trackTitle.contains(englishName, true) ||
                track.lang.let { langRegex.find(it) != null }
            // ANK -->
        } catch (_: MissingResourceException) {
            return false
        }
        // ANK <--
    }
}

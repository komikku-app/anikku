package eu.kanade.tachiyomi.source

import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.online.all.MergedSource
import tachiyomi.domain.source.model.StubSource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Source.icon(): Drawable? = Injekt.get<ExtensionManager>().getAppIconForSource(this.id)

fun Source.getPreferenceKey(): String = "source_$id"

fun Source.toStubSource(): StubSource = StubSource(id = id, lang = lang, name = name)

fun Source.getNameForMangaInfo(
    // SY -->
    mergeSources: List<Source>? = null,
    // SY <--
): String {
    val preferences = Injekt.get<SourcePreferences>()
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // SY -->
        !mergeSources.isNullOrEmpty() -> getMergedSourcesString(
            mergeSources,
            enabledLanguages,
            hasOneActiveLanguages,
        )
        // SY <--
        // KMK -->
        isLocalOrStub() -> toString()
        // KMK <--
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages ->
            // KMK -->
            "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
        // KMK <--
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else ->
            // KMK -->
            "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
        // KMK <--
    }
}

// SY -->
private fun getMergedSourcesString(
    mergeSources: List<Source>,
    enabledLangs: List<String>,
    onlyName: Boolean,
): String {
    return if (onlyName) {
        mergeSources.joinToString { source ->
            when {
                // KMK -->
                source.isLocalOrStub() -> source.toString()
                // KMK <--
                source.lang !in enabledLangs ->
                    // KMK -->
                    "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
                // KMK <--
                else ->
                    source.name
            }
        }
    } else {
        mergeSources.joinToString { source ->
            // KMK -->
            if (source.isLocalOrStub()) {
                source.toString()
            } else {
                "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
            }
            // KMK <--
        }
    }
}
// SY <--

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource

// AM (DISCORD) -->
fun Source?.isNsfw(): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = Injekt.get<ExtensionManager>().installedExtensionsFlow.value
        .find { ext -> ext.sources.any { it.id == this.id } }!!
    return sourceUsed.isNsfw
}
// <-- AM (DISCORD)

// (TORRENT) -->
fun Source?.isSourceForTorrents(): Boolean {
    if (this == null || this.isLocalOrStub() || this is MergedSource) return false
    val sourceUsed = Injekt.get<ExtensionManager>().installedExtensionsFlow.value
        .find { ext -> ext.sources.any { it.id == this.id } }!!
    return sourceUsed.isTorrent
}
// <-- (TORRENT)

package eu.kanade.presentation.util

import androidx.compose.runtime.Composable
import java.util.Locale
import java.util.MissingResourceException

fun String.isValidLanguageCode(): Boolean {
    try {
        val locale = Locale(this)
        if (locale.getISO3Language() == locale.language && locale.language == locale.getDisplayName(Locale.ENGLISH)) {
            return false
        }
    } catch (_: MissingResourceException) {
        return false
    }

    return true
}

// Utility function to validate language codes
fun isLanguageListValid(pref: String): Boolean {
    val langs = pref.parseCommaSeparatedList()
    return langs.all { it.isValidLanguageCode() }
}

// Utility function to get invalid language error message
@Composable
fun getInvalidLanguageError(
    pref: String,
    errorMessageProvider: @Composable (String) -> String,
): String {
    val langs = pref.parseCommaSeparatedList()
    return langs.firstOrNull { !it.isValidLanguageCode() }
        ?.let { errorMessageProvider(it) }
        ?: ""
}

fun String.parseCommaSeparatedList(): List<String> {
    return split(",").filter(String::isNotBlank).map(String::trim)
}

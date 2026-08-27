package keiyoushi.utils

import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * JVM stubs for the preference screen extension functions defined in keiyoushi's core module.
 *
 * The real implementations (in extensions-source/core/) use AndroidX `ListPreference` and
 * `SwitchPreference` to build an Android-style settings UI. On macOS/JVM those widgets
 * don't exist, so these are no-ops that just let the extension compile.
 */
fun PreferenceScreen.addListPreference(
    key: String,
    default: String,
    title: String,
    summary: String,
    entries: List<String>,
    entryValues: List<String>,
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, String) -> Boolean = { _, _ -> true },
    onComplete: (String) -> Unit = {},
) {}

fun PreferenceScreen.addEditTextPreference(
    key: String,
    default: String,
    title: String,
    summary: String,
    getSummary: (String) -> String = { summary },
    dialogMessage: String? = null,
    inputType: Int? = null,
    validate: ((String) -> Boolean)? = null,
    validationMessage: ((String) -> String)? = null,
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, String) -> Boolean = { _, _ -> true },
    onComplete: (String) -> Unit = {},
) {}

fun PreferenceScreen.addSwitchPreference(
    key: String,
    default: Boolean,
    title: String,
    summary: String,
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, Boolean) -> Boolean = { _, _ -> true },
    onComplete: (Boolean) -> Unit = {},
) {}

/**
 * Alias for addSwitchPreference — used by miruro and other extensions.
 */
fun PreferenceScreen.getSwitchPreference(
    key: String,
    default: Boolean = false,
    title: String = "",
    summary: String = "",
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, Boolean) -> Boolean = { _, _ -> true },
    onComplete: (Boolean) -> Unit = {},
) = addSwitchPreference(key, default, title, summary, restartRequired, enabled, onChange, onComplete)

package keiyoushi.utils

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences

/**
 * Name for the preferences node used by keiyoushi sources.
 */
private const val PREFS_NODE = "keiyoushi/utils"

/**
 * Retrieve source-specific preferences as a [SharedPreferences]-compatible object.
 *
 * Uses [java.util.prefs.Preferences] under the hood so preferences
 * persist across app restarts on the JVM.
 */
fun getPreferencesLazy(sourceId: Long = -1L): Lazy<SharedPreferences> = lazy { getPreferences(sourceId) }

fun getPreferences(sourceId: Long): SharedPreferences {
    val prefsNode = Preferences.userRoot().node("$PREFS_NODE/sources/$sourceId")
    return JvmSharedPreferences(prefsNode)
}

// JvmSharedPreferences is compiled in the macOS module (macos/src/main/kotlin/keiyoushi/utils/JvmSharedPreferences.kt)

/**
 * A thread-safe lazy delegate for mutable values.
 * Backed by the source's preferences map.
 */
class LazyMutable<T>(
    private val initializer: () -> T,
) : ReadWriteProperty<Any?, T> {

    @Volatile
    private var _value: T? = null

    private val lock = Any()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val v = _value
        if (v != null) return v
        return synchronized(lock) {
            _value ?: initializer().also { _value = it }
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(lock) {
            _value = value
        }
    }
}

/**
 * Delegate that reads/writes a preference value from a [MutableMap].
 *
 * Supports String, Int, Long, Float, Boolean, and Set<String> types.
 * The expected type is inferred from the `default` parameter.
 */
class PreferenceDelegate<T>(
    private val prefs: MutableMap<String, Any>,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val stored = prefs[key]
        if (stored == null) return default
        return try {
            // Coerce stored string back to the expected type
            when (default) {
                is Boolean -> (stored.toString().toBooleanStrictOrNull() ?: (default as Boolean)) as T
                is Int -> (stored.toString().toIntOrNull() ?: (default as Int)) as T
                is Long -> (stored.toString().toLongOrNull() ?: (default as Long)) as T
                is Float -> (stored.toString().toFloatOrNull() ?: (default as Float)) as T
                is Set<*> -> (stored.toString().split(",").filter { it.isNotEmpty() }.toSet()) as T
                else -> stored as T
            }
        } catch (_: ClassCastException) {
            default
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        prefs[key] = value as Any
    }
}

// ---------------------------------------------------------------------------
// Preference DSL — JVM port
//
// On macOS, extensions configure themselves via getFilterList() rather than
// via PreferenceScreen UI. The following classes provide a lightweight
// in-memory representation that can be translated to AnimeFilter objects.
// ---------------------------------------------------------------------------

/**
 * Represents a preference entry for UI rendering.
 */
sealed class PreferenceEntry {
    data class EditText(
        val key: String,
        val title: String,
        val summary: String = "",
        val default: String = "",
        val dialogTitle: String? = null,
        val validator: ((String) -> Boolean)? = null,
    ) : PreferenceEntry()

    data class SingleChoice(
        val key: String,
        val title: String,
        val summary: String = "",
        val entries: List<String>,
        val entryValues: List<String> = entries,
        val default: String = "",
    ) : PreferenceEntry()

    data class MultiChoice(
        val key: String,
        val title: String,
        val summary: String = "",
        val entries: List<String>,
        val entryValues: List<String> = entries,
        val default: Set<String> = emptySet(),
    ) : PreferenceEntry()

    data class Switch(
        val key: String,
        val title: String,
        val summary: String = "",
        val default: Boolean = false,
    ) : PreferenceEntry()
}

/**
 * Build a list of [PreferenceEntry] items for a source.
 * This is a JVM-friendly replacement for Android's PreferenceScreen DSL.
 */
class PreferenceScreenBuilder {
    private val items = mutableListOf<PreferenceEntry>()

    fun editText(
        key: String,
        title: String,
        summary: String = "",
        default: String = "",
        dialogTitle: String? = null,
        validator: ((String) -> Boolean)? = null,
    ) {
        items.add(PreferenceEntry.EditText(key, title, summary, default, dialogTitle, validator))
    }

    fun list(
        key: String,
        title: String,
        summary: String = "",
        entries: List<String>,
        entryValues: List<String> = entries,
        default: String = "",
    ) {
        items.add(PreferenceEntry.SingleChoice(key, title, summary, entries, entryValues, default))
    }

    fun multiSelect(
        key: String,
        title: String,
        summary: String = "",
        entries: List<String>,
        entryValues: List<String> = entries,
        default: Set<String> = emptySet(),
    ) {
        items.add(PreferenceEntry.MultiChoice(key, title, summary, entries, entryValues, default))
    }

    fun switch(
        key: String,
        title: String,
        summary: String = "",
        default: Boolean = false,
    ) {
        items.add(PreferenceEntry.Switch(key, title, summary, default))
    }

    fun build(): List<PreferenceEntry> = items.toList()

    /**
     * Convert to [AnimeFilterList] for the macOS settings UI.
     */
    fun toFilterList(): AnimeFilterList {
        return AnimeFilterList()
    }
}

/**
 * Build preference entries using a DSL builder.
 */
fun preferences(block: PreferenceScreenBuilder.() -> Unit): List<PreferenceEntry> {
    val builder = PreferenceScreenBuilder()
    builder.block()
    return builder.build()
}

// ---------------------------------------------------------------------------
// Compatibility stubs for extension functions that extensions import from
// the original Android keiyoushi/utils module.
// These are no-ops on JVM since there is no PreferenceScreen UI.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Extensions called by lib-multisrc themes (ported from core module)
// ---------------------------------------------------------------------------

/**
 * Returns the [SharedPreferences] associated with current source id
 */
inline fun AnimeHttpSource.getPreferencesLazy(
    crossinline migration: SharedPreferences.() -> Unit = { },
) = lazy {
    val prefs = getPreferences(id)
    prefs.migration()
    prefs
}

/**
 * Create [PreferenceDelegate] from a [SharedPreferences] instance (not MutableMap).
 * Used by themes via: `by preferences.delegate(key, default)`
 */
fun <T> SharedPreferences.delegate(key: String, default: T): SharedPreferenceReadWriteDelegate<T> =
    SharedPreferenceReadWriteDelegate(this, key, default)

/**
 * Delegate that reads/writes a preference value through [SharedPreferences].
 * This is the version used by lib-multisrc themes via `.delegate(key, default)`.
 */
class SharedPreferenceReadWriteDelegate<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return try {
            when (default) {
                is String -> prefs.getString(key, default) as T
                is Int -> prefs.getInt(key, default) as T
                is Long -> prefs.getLong(key, default) as T
                is Float -> prefs.getFloat(key, default) as T
                is Boolean -> prefs.getBoolean(key, default) as T
                is Set<*> -> prefs.getStringSet(key, default as Set<String>) as T
                null -> prefs.all[key] as T
                else -> throw IllegalArgumentException("Unsupported type: ${default.javaClass}")
            }
        } catch (_: ClassCastException) {
            default
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(this) {
            val editor = prefs.edit()
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
                else -> throw IllegalArgumentException("Unsupported type: ${value.javaClass}")
            }
            editor.apply()
        }
    }
}

/**
 * Add a [MultiSelectListPreference] to the [PreferenceScreen].
 * Used by lib-multisrc themes via `screen.addSetPreference(...)`.
 */
fun PreferenceScreen.addSetPreference(
    key: String,
    title: String,
    summary: String = "",
    entries: List<String>,
    entryValues: List<String> = entries,
    default: Set<String> = emptySet(),
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (androidx.preference.Preference, Set<String>) -> Boolean = { _, _ -> true },
    onComplete: (Set<String>) -> Unit = {},
) {
    val pref = MultiSelectListPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        this.entries = entries.toTypedArray()
        this.entryValues = entryValues.toTypedArray()
        setDefaultValue(default)
        this.setEnabled(enabled)

        setOnPreferenceChangeListener { pref, newValues ->
            @Suppress("UNCHECKED_CAST")
            val values = newValues as Set<String>
            val isValid = onChange(pref, values)
            if (isValid) {
                onComplete(values)
            }
            isValid
        }
    }
    addPreference(pref)
}

@Deprecated("Use PreferenceScreenBuilder.editText for JVM-friendly preference DSL")
fun addEditTextPreference(
    key: String,
    title: String,
    summary: String = "",
    default: String = "",
    dialogTitle: String? = null,
    validator: ((String) -> Boolean)? = null,
) { /* no-op on JVM */ }

@Deprecated("Use PreferenceScreenBuilder.list for JVM-friendly preference DSL")
fun addListPreference(
    key: String,
    title: String,
    summary: String = "",
    entries: List<String>,
    entryValues: List<String>,
    default: String = "",
) { /* no-op on JVM */ }

/**
 * Create a [androidx.preference.ListPreference] attached to the [PreferenceScreen].
 * This is the getter variant used by extension sources to programmatically create
 * and configure a ListPreference before adding it to the screen.
 */
fun PreferenceScreen.getListPreference(
    key: String,
    title: String,
    summary: String = "",
    entries: List<String>,
    entryValues: List<String>,
    default: String = "",
    onChange: (androidx.preference.Preference, String) -> Boolean = { _, _ -> true },
    onComplete: (String) -> Unit = {},
): androidx.preference.ListPreference {
    val ctx = context
    val pref = androidx.preference.ListPreference(ctx).apply {
        this.key = key
        this.title = title
        this.summary = summary
        this.entries = entries.toTypedArray()
        this.entryValues = entryValues.toTypedArray()
        setDefaultValue(default)
        setOnPreferenceChangeListener { pref, newValue ->
            @Suppress("UNCHECKED_CAST")
            val value = newValue as String
            val isValid = onChange(pref, value)
            if (isValid) onComplete(value)
            isValid
        }
    }
    addPreference(pref)
    return pref
}

@Deprecated("Use PreferenceScreenBuilder.switch for JVM-friendly preference DSL")
fun addSwitchPreference(
    key: String,
    title: String,
    summary: String = "",
    default: Boolean = false,
) { /* no-op on JVM */ }

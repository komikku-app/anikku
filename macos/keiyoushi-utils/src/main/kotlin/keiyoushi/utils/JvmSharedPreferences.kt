package keiyoushi.utils

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/**
 * A [SharedPreferences] implementation backed by [java.util.prefs.Preferences].
 *
 * Thread-safe: all mutations are synchronized on the Preferences node.
 * Supports String, Boolean, Int, Long, Float, and Set<String> values.
 */
class JvmSharedPreferences(
    private val prefs: Preferences,
) : SharedPreferences {

    private val cache: ConcurrentHashMap<String, Any?> = ConcurrentHashMap()

    init {
        try {
            for (key in prefs.keys()) {
                val raw = prefs.get(key, null)
                if (raw != null) {
                    cache[key] = raw
                }
            }
        } catch (_: BackingStoreException) {
            // Preferences backend unavailable — start with empty cache
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return cache[key] as? String ?: defValue
    }

    override fun getInt(key: String, defValue: Int): Int {
        val raw = cache[key]
        return when (raw) {
            is Int -> raw
            is String -> raw.toIntOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getLong(key: String, defValue: Long): Long {
        val raw = cache[key]
        return when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is String -> raw.toLongOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val raw = cache[key]
        return when (raw) {
            is Float -> raw
            is Int -> raw.toFloat()
            is String -> raw.toFloatOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val raw = cache[key]
        return when (raw) {
            is Boolean -> raw
            is String -> raw.toBooleanStrictOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val raw = cache[key]
        return when (raw) {
            is Set<*> -> raw.filterIsInstance<String>().toSet()
            is String -> {
                if (raw.isBlank()) defValues
                else raw.split(",").filter { it.isNotEmpty() }.toSet()
            }
            else -> defValues
        }
    }

    override fun contains(key: String): Boolean = cache.containsKey(key)

    override val all: Map<String, *> get() = cache.toMap()

    override fun edit(): SharedPreferences.Editor {
        return Editor(cache, prefs)
    }

    /**
     * [SharedPreferences.Editor] implementation backed by [java.util.prefs.Preferences].
     */
    private class Editor(
        private val cache: ConcurrentHashMap<String, Any?>,
        private val prefs: Preferences,
    ) : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private val pendingRemoves = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            pendingRemoves.remove(key)
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            pendingRemoves.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            pendingRemoves.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            pendingRemoves.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            pendingRemoves.remove(key)
            return this
        }

        override fun putStringSet(key: String, value: Set<String>?): SharedPreferences.Editor {
            pending[key] = value
            pendingRemoves.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            pendingRemoves.add(key)
            pending.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            cleared = true
            pending.clear()
            pendingRemoves.clear()
            return this
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            return try {
                if (cleared) {
                    try {
                        for (k in prefs.keys()) {
                            prefs.remove(k)
                        }
                    } catch (_: BackingStoreException) { /* best-effort */ }
                    cache.clear()
                }

                for (k in pendingRemoves) {
                    prefs.remove(k)
                    cache.remove(k)
                }

                for ((k, v) in pending) {
                    if (v == null) {
                        prefs.remove(k)
                        cache.remove(k)
                    } else {
                        cache[k] = v
                        prefs.put(k, v.toString())
                    }
                }

                prefs.flush()
                pending.clear()
                pendingRemoves.clear()
                cleared = false
                true
            } catch (_: BackingStoreException) {
                false
            }
        }
    }
}

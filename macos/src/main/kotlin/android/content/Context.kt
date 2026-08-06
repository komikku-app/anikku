package android.content

import app.anikku.macos.platform.preference.MacOSPreferenceStore

/**
 * Bridge that lets the android.* stubs persist extension preferences through
 * the app's real preference store. Set once at app init (AnikkuApplication)
 * after the store is created; null keeps the historical no-op behavior.
 */
object AndroidPrefsBridge {
    @Volatile
    var store: MacOSPreferenceStore? = null
}

/**
 * Stub for `android.content.Context` on macOS JVM.
 *
 * Extensions compiled for Android call `getSharedPreferences()` on the
 * injected `Application` instance (which extends `Context` in Android).
 * This stub provides the method so method dispatch works without
 * throwing `NoSuchMethodError`.
 *
 * When [AndroidPrefsBridge.store] is set (the app's MacOSPreferenceStore),
 * returned preferences persist for real under an `ext_<name>_` namespace —
 * this is what makes per-source settings (API keys, quality defaults, …)
 * survive restarts. Without it, preferences are no-ops.
 */
open class Context {

    private val noopEditor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun putStringSet(key: String, value: Set<String>?) = this
        override fun remove(key: String) = this
        override fun clear() = this
        override fun apply() {}
        override fun commit(): Boolean = true
    }

    private val noopPrefs = object : SharedPreferences {
        override fun edit() = noopEditor
        override fun getString(key: String, defValue: String?) = defValue
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun getStringSet(key: String, defValues: Set<String>?) = defValues
        override fun contains(key: String) = false
        override val all: Map<String, *> get() = emptyMap<String, Any>()
    }

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val store = AndroidPrefsBridge.store
            ?: return noopPrefs
        return store.toSharedPreferences(name)
    }

    val applicationContext: Context get() = this

    fun getBaseContext(): Context = this

    fun getFilesDir(): java.io.File {
        val dir = java.io.File(System.getProperty("user.home"), ".Anikku/files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDir(name: String, mode: Int): java.io.File {
        val dir = java.io.File(getFilesDir(), name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getExternalFilesDir(type: String?): java.io.File = getFilesDir()

    fun getString(resId: Int): String = ""

    fun getResources(): android.content.res.Resources = android.content.res.Resources()

    fun getPackageManager(): android.content.pm.PackageManager = android.content.pm.PackageManager()

    fun getContentResolver(): android.content.ContentResolver = android.content.ContentResolver()

    fun getSystemService(name: String): Any? = null

    fun checkSelfPermission(permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_GRANTED

    fun startActivity(intent: Intent) {}

    fun sendBroadcast(intent: Intent) {}

    fun registerReceiver(
        receiver: android.content.BroadcastReceiver?,
        filter: IntentFilter?,
    ): Intent? = null

    fun unregisterReceiver(receiver: android.content.BroadcastReceiver?) {}

    fun getPackageName(): String = "app.anikku.macos"
}

/**
 * A real [SharedPreferences] backed by the app's preference store, namespaced
 * per preferences-file name (`ext_<name>_<key>`) so every extension's
 * settings live under its own prefix. Values survive restarts.
 */
private fun MacOSPreferenceStore.toSharedPreferences(name: String): SharedPreferences {
    val prefix = "ext_${name}_"
    fun key(k: String) = prefix + k

    return object : SharedPreferences {
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putStringSet(key: String, value: Set<String>?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                pending.clear()
                return this
            }

            override fun apply() {
                commit()
            }

            override fun commit(): Boolean {
                if (clearAll) {
                    this@toSharedPreferences.getAll().keys
                        .filter { it.startsWith(prefix) }
                        .forEach { runCatching { this@toSharedPreferences.getString(it, "").delete() } }
                }
                pending.forEach { (k, v) ->
                    when (v) {
                        is String -> this@toSharedPreferences.getString(key(k), "").set(v)
                        is Int -> this@toSharedPreferences.getInt(key(k), 0).set(v)
                        is Long -> this@toSharedPreferences.getLong(key(k), 0L).set(v)
                        is Float -> this@toSharedPreferences.getFloat(key(k), 0f).set(v)
                        is Boolean -> this@toSharedPreferences.getBoolean(key(k), false).set(v)
                        is Set<*> -> this@toSharedPreferences.getStringSet(key(k), emptySet()).set(v as Set<String>)
                        null -> runCatching { this@toSharedPreferences.getString(key(k), "").delete() }
                        else -> Unit
                    }
                }
                pending.clear()
                return true
            }
        }

        override fun getString(key: String, defValue: String?): String? {
            val raw = this@toSharedPreferences.getAll()[key(key)]
            return when (raw) {
                is String -> raw
                null -> defValue
                else -> raw.toString()
            }
        }

        override fun getInt(key: String, defValue: Int): Int =
            this@toSharedPreferences.getInt(key(key), defValue).get()

        override fun getLong(key: String, defValue: Long): Long =
            this@toSharedPreferences.getLong(key(key), defValue).get()

        override fun getFloat(key: String, defValue: Float): Float =
            this@toSharedPreferences.getFloat(key(key), defValue).get()

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            this@toSharedPreferences.getBoolean(key(key), defValue).get()

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            this@toSharedPreferences.getStringSet(key(key), defValues ?: emptySet()).get()

        override fun contains(key: String): Boolean =
            this@toSharedPreferences.getAll().containsKey(key(key))

        override val all: Map<String, *>
            get() = this@toSharedPreferences.getAll()
                .filterKeys { it.startsWith(prefix) }
                .mapKeys { it.key.removePrefix(prefix) }
    }
}

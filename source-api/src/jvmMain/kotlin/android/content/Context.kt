package android.content

import java.io.File

actual open class Context {
    actual val cacheDir: File = File(System.getProperty("java.io.tmpdir"), "cache")

    actual fun getSharedPreferences(name: String, mode: Int): SharedPreferences = object : SharedPreferences {
        override fun getString(key: String, defValue: String?) = defValue
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun getStringSet(key: String, defValues: Set<String>?) = defValues
        override fun contains(key: String) = false
        override val all: Map<String, *> get() = emptyMap<String, Any>()
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun putStringSet(key: String, value: Set<String>?) = this
            override fun remove(key: String) = this
            override fun clear() = this
            override fun apply() {}
            override fun commit() = true
        }
    }

    actual companion object {
        actual const val MODE_PRIVATE: Int = 0
    }
}

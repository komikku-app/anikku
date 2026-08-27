package android.content

actual interface SharedPreferences {
    actual fun getString(key: String, defValue: String?): String?
    actual fun getInt(key: String, defValue: Int): Int
    actual fun getLong(key: String, defValue: Long): Long
    actual fun getFloat(key: String, defValue: Float): Float
    actual fun getBoolean(key: String, defValue: Boolean): Boolean
    actual fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    actual fun contains(key: String): Boolean
    actual val all: Map<String, *>
    actual fun edit(): Editor

    actual interface Editor {
        actual fun putString(key: String, value: String?): Editor
        actual fun putInt(key: String, value: Int): Editor
        actual fun putLong(key: String, value: Long): Editor
        actual fun putFloat(key: String, value: Float): Editor
        actual fun putBoolean(key: String, value: Boolean): Editor
        actual fun putStringSet(key: String, value: Set<String>?): Editor
        actual fun remove(key: String): Editor
        actual fun clear(): Editor
        actual fun apply()
        actual fun commit(): Boolean
    }
}

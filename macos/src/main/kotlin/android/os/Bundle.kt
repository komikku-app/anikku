package android.os

/**
 * Stub for `android.os.Bundle` on macOS JVM.
 */
open class Bundle {
    private val map = mutableMapOf<String, Any?>()

    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun putInt(key: String, value: Int) { map[key] = value }
    fun putLong(key: String, value: Long) { map[key] = value }
    fun putFloat(key: String, value: Float) { map[key] = value }
    fun putDouble(key: String, value: Double) { map[key] = value }
    fun putString(key: String, value: String?) { map[key] = value }
    fun putStringArrayList(key: String, value: java.util.ArrayList<String>?) { map[key] = value }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        map[key] as? Boolean ?: defaultValue
    fun getInt(key: String, defaultValue: Int = 0): Int =
        map[key] as? Int ?: defaultValue
    fun getLong(key: String, defaultValue: Long = 0L): Long =
        map[key] as? Long ?: defaultValue
    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        map[key] as? Float ?: defaultValue
    fun getDouble(key: String, defaultValue: Double = 0.0): Double =
        map[key] as? Double ?: defaultValue
    fun getString(key: String): String? = map[key] as? String
    fun getString(key: String, defaultValue: String): String? = map[key] as? String ?: defaultValue
    fun getStringArrayList(key: String): java.util.ArrayList<String>? =
        map[key] as? java.util.ArrayList<String>

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun keySet(): Set<String> = map.keys
    fun size(): Int = map.size
    fun isEmpty(): Boolean = map.isEmpty()
    fun clear() { map.clear() }
    fun putAll(bundle: Bundle) { map.putAll(bundle.map) }
}

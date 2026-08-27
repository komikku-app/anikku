package tachiyomi.core.common.preference

interface PreferenceStore {
    fun getString(key: String, defaultValue: String = ""): Preference<String>
    fun getLong(key: String, defaultValue: Long = 0): Preference<Long>
    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>
    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>
    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>
    fun <T> getObject(
        key: String, defaultValue: T,
        serializer: (T) -> String, deserializer: (String) -> T,
    ): Preference<T>
    fun getAll(): Map<String, *>

    companion object {
        fun isPrivate(key: String): Boolean = key.startsWith("__PRIVATE_")
        fun privateKey(key: String): String = "__PRIVATE_$key"
        fun isAppState(key: String): Boolean = key.startsWith("__APP_STATE_")
        fun appStateKey(key: String): String = "__APP_STATE_$key"
    }
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnum(key: String, defaultValue: T): Preference<T> {
    return getObject(key, defaultValue, { it.name }, { try { enumValueOf(it) } catch (_: Exception) { defaultValue } })
}

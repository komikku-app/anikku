package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Preference<T> {
    fun key(): String
    fun get(): T
    fun set(value: T)
    fun isSet(): Boolean
    fun delete()
    fun defaultValue(): T
    fun changes(): Flow<T>
    fun stateIn(scope: CoroutineScope): StateFlow<T>

    companion object {
        fun isPrivate(key: String): Boolean = key.startsWith(PRIVATE_PREFIX)
        fun privateKey(key: String): String = "$PRIVATE_PREFIX$key"
        fun isAppState(key: String): Boolean = key.startsWith(APP_STATE_PREFIX)
        fun appStateKey(key: String): String = "$APP_STATE_PREFIX$key"

        private const val APP_STATE_PREFIX = "__APP_STATE_"
        private const val PRIVATE_PREFIX = "__PRIVATE_"
    }
}

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(block(get()))

inline fun <reified T> Preference<T>.deleteAndGet(): T {
    delete()
    return get()
}

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}

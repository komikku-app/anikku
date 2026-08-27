package android.util

/**
 * JVM-friendly stub of android.util.LruCache.
 * Not a real LRU — just a thread-safe HashMap with maxSize tracking.
 */
open class LruCache<K, V>(private val maxSize: Int) {
    private val cache = LinkedHashMap<K, V>(0, 0.75f, true)

    @Synchronized
    open fun get(key: K): V? = cache[key]

    @Synchronized
    open fun put(key: K, value: V): V? = cache.put(key, value)

    @Synchronized
    open fun remove(key: K): V? = cache.remove(key)

    @Synchronized
    open fun evictAll() = cache.clear()

    open val size: Int get() = synchronized(this) { cache.size }

    protected open fun create(key: K): V? = null

    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}

    protected open fun sizeOf(key: K, value: V): Int = 1

    @Synchronized
    fun snapshot(): Map<K, V> = LinkedHashMap(cache)

    @Synchronized
    override fun toString(): String = "LruCache[maxSize=$maxSize]"
}

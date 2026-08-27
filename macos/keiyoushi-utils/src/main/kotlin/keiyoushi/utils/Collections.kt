package keiyoushi.utils

// From https://github.com/keiyoushi/extensions-source/blob/main/core/src/main/kotlin/keiyoushi/utils/Collections.kt

/**
 * Returns the first element that is an instances of specified type parameter T.
 *
 * @throws [NoSuchElementException] if no such element is found.
 */
inline fun <reified T> Iterable<*>.firstInstance(): T = first { it is T } as T

/**
 * Returns the first element that is an instances of specified type parameter T, or `null` if element was not found.
 */
inline fun <reified T> Iterable<*>.firstInstanceOrNull(): T? = firstOrNull { it is T } as? T

package keiyoushi.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.logging.Logger

private val coroutinesLogger = Logger.getLogger("keiyoushi.utils.Coroutines")

/**
 * Maps not-null elements in parallel using [Dispatchers.IO].
 *
 * @since extensions-lib 14
 */
suspend fun <T, R : Any> Iterable<T>.parallelMapNotNull(transform: suspend (T) -> R?): List<R> =
    coroutineScope {
        map { async(Dispatchers.IO) { transform(it) } }.awaitAll().filterNotNull()
    }

/**
 * Blocking version of [parallelMapNotNull]. Accepts a suspend lambda so callers
 * can use suspend functions like awaitSuccess() inside the transform.
 *
 * @since extensions-lib 14
 */
inline fun <T, R : Any> Iterable<T>.parallelMapNotNullBlocking(
    crossinline transform: suspend (T) -> R?,
): List<R> = runBlocking {
    parallelMapNotNull { transform(it) }
}

/**
 * Maps elements in parallel using [Dispatchers.IO].
 *
 * @since extensions-lib 14
 */
suspend fun <T, R> Iterable<T>.parallelMap(transform: suspend (T) -> R): List<R> = coroutineScope {
    map { async(Dispatchers.IO) { transform(it) } }.awaitAll()
}

/**
 * Blocking version of [parallelMap]. Accepts a suspend lambda.
 */
inline fun <T, R> Iterable<T>.parallelMapBlocking(
    crossinline transform: suspend (T) -> R,
): List<R> = runBlocking {
    parallelMap { transform(it) }
}

/**
 * Maps and flattens in parallel using [Dispatchers.IO].
 */
suspend fun <T, R> Iterable<T>.parallelFlatMap(transform: suspend (T) -> Iterable<R>): List<R> =
    coroutineScope {
        map { async(Dispatchers.IO) { transform(it) } }.awaitAll().flatten()
    }

/**
 * Blocking version of [parallelFlatMap]. Accepts a suspend lambda.
 */
inline fun <T, R> Iterable<T>.parallelFlatMapBlocking(
    crossinline transform: suspend (T) -> Iterable<R>,
): List<R> = runBlocking {
    parallelFlatMap { transform(it) }
}

/**
 * Like [flatMap] but catches errors, logging them and returning an empty list for failed items.
 *
 * Matches the yuzono/anime-extensions source signature: single function parameter `f`.
 * The cloned repo's original version uses this signature with `inline`+`crossinline`,
 * which the batch build script strips for JVM compatibility.
 */
suspend fun <A, B> Iterable<A>.parallelCatchingFlatMap(
    f: suspend (A) -> Iterable<B>,
): List<B> = coroutineScope {
    map { item ->
        async(Dispatchers.IO) {
            try {
                f(item)
            } catch (e: Exception) {
                coroutinesLogger.warning("An error occurred in parallelCatchingFlatMap: ${e.message}")
                emptyList()
            }
        }
    }.awaitAll().flatten()
}

/**
 * Blocking version of [parallelCatchingFlatMap].
 * Matches the yuzono/anime-extensions source signature.
 */
fun <A, B> Iterable<A>.parallelCatchingFlatMapBlocking(
    f: suspend (A) -> Iterable<B>,
): List<B> = runBlocking {
    parallelCatchingFlatMap(f)
}

/**
 * Like [mapNotNull] but catches errors, logging them and returning null for failed items.
 */
suspend fun <T, R : Any> Iterable<T>.parallelCatchingMapNotNull(
    errorMessage: String = "An error occurred in parallelCatchingMapNotNull",
    transform: suspend (T) -> R?,
): List<R> = coroutineScope {
    map { item ->
        async(Dispatchers.IO) {
            try {
                transform(item)
            } catch (e: Exception) {
                coroutinesLogger.warning("$errorMessage: ${e.message}")
                null
            }
        }
    }.awaitAll().filterNotNull()
}

/**
 * Like [flatMap] but catches errors sequentially, logging them and continuing.
 */
suspend fun <T, R> Iterable<T>.catchingFlatMap(
    errorMessage: String = "An error occurred in catchingFlatMap",
    transform: suspend (T) -> Iterable<R>,
): List<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        try {
            result.addAll(transform(item))
        } catch (e: Exception) {
            coroutinesLogger.warning("$errorMessage: ${e.message}")
        }
    }
    return result
}

/**
 * Blocking version of [catchingFlatMap]. Accepts a suspend lambda.
 */
inline fun <T, R> Iterable<T>.catchingFlatMapBlocking(
    errorMessage: String = "An error occurred in catchingFlatMap",
    crossinline transform: suspend (T) -> Iterable<R>,
): List<R> = runBlocking {
    catchingFlatMap(errorMessage) { transform(it) }
}

/**
 * Like [flatMap] but catches errors synchronously, logging them and continuing.
 */
fun <T, R> Iterable<T>.flatMapCatching(
    errorMessage: String = "An error occurred in flatMapCatching",
    transform: (T) -> Iterable<R>,
): List<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        try {
            result.addAll(transform(item))
        } catch (e: Exception) {
            coroutinesLogger.warning("$errorMessage: ${e.message}")
        }
    }
    return result
}

package keiyoushi.utils

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString
import java.util.concurrent.TimeUnit.MINUTES

// TODO: Remove with ext lib 16

private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10, MINUTES).build()
private val DEFAULT_HEADERS = Headers.Builder().build()
private val DEFAULT_BODY: RequestBody = FormBody.Builder().build()

suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(GET(url, headers, cache)).awaitSuccess()

suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(GET(url, headers, cache)).awaitSuccess()

suspend fun OkHttpClient.post(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(POST(url, headers, body, cache)).awaitSuccess()

fun Response.useAsJsoup() = use { it.asJsoup() }

fun Response.bodyString() = use { it.body.string() }

/**
 * Empty [okhttp3] headers used by the source.
 *
 * Provides backward compatibility with the older internal `okhttp3.internal.commonEmptyHeaders`
 * by lazily returning an instance equivalent to [Headers.EMPTY].
 *
 * If app is still using [okhttp3] (ver 5.0.0-alpha.14) then even if extensions
 * are updated to new API, the call would fail to look for actual instance.
 *
 * WARNING: Keep using this if various apps are still on older [okhttp3]
 */
@Deprecated("Use newer API `Headers.EMPTY` instead", replaceWith = ReplaceWith("Headers.EMPTY"))
val commonEmptyHeaders by lazy { Headers.Builder().build() }

/**
 * Empty [okhttp3] request body used by the source.
 *
 * Provides backward compatibility with the older internal `okhttp3.internal.commonEmptyRequestBody`
 * by lazily returning an instance equivalent to [RequestBody.EMPTY].
 *
 * If app is still using [okhttp3] (ver 5.0.0-alpha.14) then even if extensions
 * are updated to new API, the call would fail to look for actual instance.
 *
 * WARNING: Keep using this if various apps are still on older [okhttp3]
 */
@Deprecated("Use newer API `RequestBody.EMPTY` instead", replaceWith = ReplaceWith("RequestBody.EMPTY"))
val commonEmptyRequestBody by lazy { ByteString.EMPTY.toRequestBody() }

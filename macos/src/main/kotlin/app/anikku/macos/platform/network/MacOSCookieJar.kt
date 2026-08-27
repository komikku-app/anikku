package app.anikku.macos.platform.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI

/**
 * macOS persistent cookie jar backed by java.net.CookieManager.
 * Stores cookies in memory during the session and persists to a JSON file.
 *
 * File: ~/Library/Application Support/Anikku/data/cookies.json
 */
class MacOSCookieJar(
    private val cookieFile: File,
    private val json: Json = Json { prettyPrint = true },
) : CookieJar {

    private val cookieManager = CookieManager(InMemoryCookieStore(), CookiePolicy.ACCEPT_ALL)

    init {
        loadFromFile()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val uri = url.toUri()
        cookies.forEach { cookie ->
            val httpCookie = HttpCookie(cookie.name, cookie.value)
            httpCookie.domain = cookie.domain
            httpCookie.path = cookie.path
            httpCookie.secure = cookie.secure
            httpCookie.isHttpOnly = cookie.httpOnly
            if (cookie.expiresAt != 0L) {
                httpCookie.maxAge = (cookie.expiresAt - System.currentTimeMillis()) / 1000
            }
            cookieManager.cookieStore.add(uri, httpCookie)
        }
        persistToFile()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val uri = url.toUri()
        val httpCookies = cookieManager.cookieStore.get(uri)
        return httpCookies.mapNotNull { httpCookie ->
            val builder = Cookie.Builder()
                .name(httpCookie.name)
                .value(httpCookie.value)
                .domain(httpCookie.domain ?: url.host)
                .path(httpCookie.path ?: "/")
            if (httpCookie.secure) builder.secure()
            if (httpCookie.isHttpOnly) builder.httpOnly()
            try {
                builder.build()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val uri = url.toUri()
        val cookies = cookieManager.cookieStore.get(uri)
        var count = 0

        cookies.forEach { cookie ->
            if (cookieNames == null || cookie.name in cookieNames) {
                cookie.maxAge = maxAge.toLong()
                cookieManager.cookieStore.remove(uri, cookie)
                count++
            }
        }

        if (count > 0) persistToFile()
        return count
    }

    fun removeAll() {
        cookieManager.cookieStore.removeAll()
        cookieFile.delete()
    }

    private fun persistToFile() {
        try {
            cookieFile.parentFile?.mkdirs()
            val entries = cookieManager.cookieStore.urIs.flatMap { uri ->
                cookieManager.cookieStore.get(uri).map { cookie ->
                    CookieEntry(
                        name = cookie.name,
                        value = cookie.value,
                        domain = cookie.domain ?: "",
                        path = cookie.path ?: "/",
                        secure = cookie.secure,
                        httpOnly = cookie.isHttpOnly,
                        maxAge = cookie.maxAge,
                        uri = uri.toString(),
                    )
                }
            }
            cookieFile.writeText(json.encodeToString(CookieList(entries)))
        } catch (_: Exception) {
            // Persistence failure is non-fatal
        }
    }

    private fun loadFromFile() {
        if (!cookieFile.exists()) return
        try {
            val data = json.decodeFromString<CookieList>(cookieFile.readText())
            data.cookies?.forEach { entry ->
                val cookie = HttpCookie(entry.name, entry.value)
                cookie.domain = entry.domain
                cookie.path = entry.path
                cookie.secure = entry.secure
                cookie.isHttpOnly = entry.httpOnly
                cookie.maxAge = entry.maxAge
                cookieManager.cookieStore.add(URI(entry.uri), cookie)
            }
        } catch (_: Exception) {
            // Corrupted cookie file — start fresh
        }
    }

    @Serializable
    private data class CookieList(val cookies: List<CookieEntry>? = null)

    @Serializable
    private data class CookieEntry(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val maxAge: Long,
        val uri: String,
    )

    /**
     * Simple in-memory cookie store.
     */
    private class InMemoryCookieStore : CookieStore {
        private val store = mutableMapOf<URI, MutableList<HttpCookie>>()

        override fun add(uri: URI?, cookie: HttpCookie?) {
            if (uri != null && cookie != null) {
                val list = store.getOrPut(uri) { mutableListOf() }
                // Remove existing cookie with same name+domain to prevent duplicates
                list.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                list.add(cookie)
            }
        }

        override fun get(uri: URI?): List<HttpCookie> {
            return uri?.let { store[it] } ?: emptyList()
        }

        override fun getCookies(): List<HttpCookie> = store.values.flatten()
        override fun getURIs(): List<URI> = store.keys.toList()

        override fun remove(uri: URI?, cookie: HttpCookie?): Boolean {
            return uri?.let { store[it]?.remove(cookie) } ?: false
        }

        override fun removeAll(): Boolean {
            store.clear()
            return true
        }
    }
}

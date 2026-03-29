package eu.kanade.tachiyomi.data.track.trakt

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import eu.kanade.tachiyomi.data.track.trakt.dto.isExpired
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class TraktInterceptor(val trakt: Trakt) : Interceptor {

    private val json: Json by injectLazy()

    private var oauth: TraktOAuth? = trakt.loadOAuth()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val currAuth = oauth ?: throw Exception("Not authenticated with Trakt")

        if (currAuth.isExpired()) {
            val response = chain.proceed(TraktApi.refreshTokenRequest(currAuth.refreshToken))
            if (response.isSuccessful) {
                newAuth(json.decodeFromString<TraktOAuth>(response.body.string()))
            } else {
                response.close()
                trakt.logout()
                throw Exception("Trakt token refresh failed")
            }
        }

        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", TraktApi.CLIENT_ID)
            .header("User-Agent", "Anikku v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: TraktOAuth?) {
        this.oauth = oauth
        trakt.saveOAuth(oauth)
    }
}

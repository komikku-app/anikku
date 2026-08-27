package app.anikku.macos.platform.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Regression coverage for the Phase 2 rule that TLS exceptions must be
 * isolated to an individual client and must never mutate JVM-global defaults.
 */
class SecureTlsDefaultsTest {

    private var server: MockWebServer? = null

    @AfterEach
    fun tearDown() {
        server?.shutdown()
    }

    @Test
    fun `default client rejects an untrusted certificate`() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            enqueue(MockResponse().setBody("secure"))
            start()
        }

        val request = Request.Builder().url(server!!.url("/")).build()

        assertThrows(IOException::class.java) {
            OkHttpClient().newCall(request).execute()
        }
    }

    @Test
    fun `default hostname verification rejects a trusted certificate for another host`() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("wrong-host.example")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            enqueue(MockResponse().setBody("should not be reachable"))
            start()
        }

        // Trust only this test certificate while retaining OkHttp's default
        // hostname verifier. The request still targets localhost, so the SAN
        // mismatch must be rejected.
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager,
            )
            .build()

        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(server!!.url("/")).build()).execute()
        }
    }

    @Test
    fun `client local trust does not change secure default client behavior`() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            enqueue(MockResponse().setBody("secure"))
            start()
        }

        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val isolatedClient = OkHttpClient.Builder()
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager,
            )
            .build()
        val request = Request.Builder().url(server!!.url("/")).build()

        isolatedClient.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("secure", response.body?.string())
        }

        // A separately created default client must still reject the same
        // certificate: the isolated trust configuration did not leak globally.
        assertThrows(IOException::class.java) {
            OkHttpClient().newCall(request).execute()
        }
    }
}

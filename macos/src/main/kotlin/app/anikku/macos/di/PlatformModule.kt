package app.anikku.macos.di

import app.anikku.macos.AnikkuApplication
import app.anikku.macos.platform.BackgroundTaskScheduler
import app.anikku.macos.platform.MacOSBackgroundJobs
import app.anikku.macos.platform.MacOSDockManager
import app.anikku.macos.platform.auth.TrackerOAuthManager
import app.anikku.macos.platform.database.MacOSDatabaseDriver
import app.anikku.macos.platform.discord.DiscordRPC
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.network.CloudflareInterceptor
import app.anikku.macos.platform.network.DiagnosticLoggingInterceptor
import app.anikku.macos.platform.network.HttpRetryInterceptor
import app.anikku.macos.platform.network.MacOSCookieJar
import app.anikku.macos.platform.network.FallbackDns
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.network.UserAgentInterceptor
import okhttp3.brotli.BrotliInterceptor
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSBiometricAuth
import app.anikku.macos.platform.storage.MacOSFilePicker
import app.anikku.macos.platform.storage.MacOSStorageProvider
import app.anikku.macos.platform.sync.GoogleDriveRestClient
import app.anikku.macos.platform.sync.MacOSGoogleDriveService
import app.anikku.macos.platform.update.AppUpdateChecker
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.FolderProvider

/**
 * Platform-specific Koin module.
 *
 * Registers macOS platform implementations that replace Android APIs:
 * - Storage: file-based storage provider
 * - Preferences: JSON file-backed preference store
 * - Database: JDBC SQLDelight driver
 * - Logging: SLF4J + Logback
 * - Networking: OkHttp client, cookie jar, DoH providers
 * - Extensions: JAR-based URLClassLoader extension loading
 * - Background tasks: coroutine-based scheduler
 */
fun platformModule(app: AnikkuApplication) = module {

    // Phase 1: Core infrastructure
    single<MacOSStorageProvider> { app.storageProvider }
    single<FolderProvider> { app.storageProvider }
    single<MacOSPreferenceStore> { app.preferenceStore }
    single<PreferenceStore> { app.preferenceStore }
    single<MacOSDatabaseDriver> { app.databaseDriver }
    single<BackgroundTaskScheduler> { app.backgroundScheduler }
    single<MacOSBackgroundJobs> { app.backgroundJobs }

    // Phase 3: Networking
    single<MacOSNetworkHelper> { app.networkHelper }
    single<MacOSCookieJar> { app.cookieJar }

    // Phase 3: Extension system
    single<MacOSExtensionManager> { app.extensionManager }

    // Injekt bridge: Register types that extensions inject via Injekt.getInjekt().getInstance()
    // These are the common KMP types (expect/actual classes) that the extension JARs
    // reference in their inlined injectLazy() bytecode.
    //
    // The minimum set of singletons needed by most extensions:
    // - Json (kotlinx.serialization) — HTTP response parsing
    // - NetworkHelper — OkHttp client wrapper with preferences
    // - DelegateSourcePreferences — extension-specific preferences
    single<Json> {
        Json { ignoreUnknownKeys = true }
    }
    // Android framework stubs — extensions compiled for Android inject these.
    // On JVM there's no Android framework, so we provide no-op stubs.
    single<android.app.Application> { android.app.Application() }
    single<android.content.Context> { android.app.Application() }
    // NetworkHelper for extension HTTP calls.
    // Crucial: uses the app's MacOSCookieJar (shared cookie store) and injects
    // the CloudflareInterceptor + DiagnosticLoggingInterceptor + HttpRetryInterceptor
    // so that extension HTTP calls get Cloudflare bypass, diagnostic logging,
    // HTTP retry for transient errors, and cookie sharing.
    single<eu.kanade.tachiyomi.network.NetworkHelper> {
        eu.kanade.tachiyomi.network.NetworkHelper(
            preferences = eu.kanade.tachiyomi.network.NetworkPreferences(get<PreferenceStore>()),
            isDebugBuild = false,
            cookieJar = app.cookieJar,
            dns = FallbackDns,
            extraInterceptors = listOf(
                // UserAgentInterceptor ensures a Chrome-like User-Agent on every
                // request so Cloudflare sees a real browser, reducing challenge rates.
                UserAgentInterceptor {
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                },
                // HttpRetryInterceptor handles transient errors (502, 503, 504, 429)
                // with exponential backoff. It runs BEFORE other interceptors so retries
                // also go through Cloudflare bypass and diagnostic logging.
                HttpRetryInterceptor(maxRetries = 3, baseDelayMs = 1000),
                // BrotliInterceptor decompresses brotli-encoded responses FIRST,
                // BEFORE CloudflareInterceptor so peekBody() sees readable text.
                // Cloudflare often uses br encoding — without this, responses
                // appear as garbage bytes and WAF pattern matching silently fails.
                BrotliInterceptor,
                // CloudflareInterceptor detects cf-challenge responses and uses
                // headless Chrome to extract cf_clearance cookies, then retries.
                CloudflareInterceptor(app.cookieJar) {
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                },
                // DiagnosticLoggingInterceptor logs error responses (non-2xx) with
                // full request/response details for debugging extension API errors.
                DiagnosticLoggingInterceptor(isDebugBuild = false),
            ),
        )
    }
    single<exh.pref.DelegateSourcePreferences> {
        exh.pref.DelegateSourcePreferences(get<PreferenceStore>())
    }

    // Phase 7.1: Tracker Sync
    single<TrackerOAuthManager> { TrackerOAuthManager(app.networkHelper.client) }

    // Phase 7.2: Google Drive Sync
    single<GoogleDriveRestClient> { GoogleDriveRestClient(app.networkHelper.client) }
    single<MacOSGoogleDriveService> { app.googleDriveService }

    // Phase 7.3: Discord Rich Presence
    single<DiscordRPC> { app.discordRPC }

    // Phase 7.4: Biometric Authentication
    single<MacOSBiometricAuth> { app.biometricAuth }

    // Phase 7.6: Notifications
    single<MacOSNotificationManager> { app.notificationManager }

    // Phase 7.7: App Update Checker
    single<AppUpdateChecker> { app.appUpdateChecker }

    // Phase 9.3: macOS File Picker
    single<MacOSFilePicker> { MacOSFilePicker() }

    // Phase 9.6: macOS Dock Integration
    single { MacOSDockManager }
}

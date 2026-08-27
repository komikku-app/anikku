package android.app

import android.content.Context

/**
 * Stub for `android.app.Application` on macOS JVM.
 *
 * Extensions compiled for Android reference `android.app.Application` in their
 * bytecode (e.g., through inlined `injectLazy()` from `AnimeHttpSource`). On
 * Android this class is provided by the Android framework. On macOS/JVM it
 * doesn't exist, causing `NoDefinitionFoundException` when the extension tries
 * to inject it via Injekt.
 *
 * This stub provides the class so the JVM can resolve it, and a corresponding
 * Koin singleton (registered in [PlatformModule]) satisfies the injection
 * request.
 *
 * Extends [Context] to provide `getSharedPreferences()` and other common
 * Android framework methods that extensions may call at runtime.
 */
open class Application : Context() {
    // Methods are inherited from Context
}

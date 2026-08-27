package keiyoushi.utils

import android.content.Context
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Application-level Context accessor.
 *
 * On Android this returns android.app.Application from Injekt.
 * On JVM/macOS, returns a Context from Injekt or a fallback.
 *
 * IMPORTANT: This must be a top-level property (not an extension function)
 * because compiled extension bytecode expects the JVM signature:
 *   static Context ContextKt.getApplicationContext()
 * An extension function would generate getApplicationContext(Context),
 * which doesn't match the bytecode reference in pre-compiled extensions.
 */
val applicationContext: Context
    get() = try {
        Injekt.get<Context>()
    } catch (_: Exception) {
        // No Context registered in Injekt/Koin — return a plain instance.
        // Extensions needing app-level state should use Injekt or Koin.
        Context()
    }

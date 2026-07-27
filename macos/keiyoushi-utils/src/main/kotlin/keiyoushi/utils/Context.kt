package keiyoushi.utils

import android.content.Context
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Extension function returning an application-level Context.
 *
 * On Android this returns android.app.Application registered in Injekt.
 * On JVM/macOS, returns a plain Context instance as fallback.
 *
 * IMPORTANT: Must be an extension function/property, NOT a top-level
 * property, because compiled extension bytecode expects the JVM signature:
 *   static Context ContextKt.getApplicationContext(Context)
 */
fun Context.getApplicationContext(): Context {
    return try {
        Injekt.get<Context>()
    } catch (_: Exception) {
        this // Return the receiver context as fallback
    }
}

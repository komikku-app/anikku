package uy.kohesive.injekt

import org.koin.core.context.GlobalContext

/**
 * Injekt lazy injection delegate stub for macOS desktop.
 * Delegates directly to Koin's GlobalContext.get() for lazy resolution.
 *
 * Usage: `val foo: Foo by injectLazy()`
 */
inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { GlobalContext.get().get<T>() }

package uy.kohesive.injekt.api

import java.lang.reflect.Type

/**
 * Injekt scope interface stub for macOS desktop.
 *
 * This interface extends [InjektFactory] and provides the same 10 methods.
 * The original `injekt-api` library defines `InjektScope` as a class that
 * implements `InjektFactory`. We define it as an interface locally (just
 * like [InjektFactory] and [InjektRegistrar]) to avoid JAR version conflicts.
 *
 * IMPORTANT: The top-level `getInjekt()` function in ALL versions of
 * `injekt-api` returns `InjektScope`, NOT `InjektFactory`. The
 * `source-api-jvm.jar` (compiled against older injekt) and extensions
 * compiled against any injekt version all call `InjektKt.getInjekt()`
 * expecting `InjektScope` as the return type. By having `InjektScope`
 * extend `InjektFactory`, the returned instance satisfies both:
 * - JVM method resolution finds `getInjekt()` → `InjektScope` ✅
 * - Extensions that cast the result to `InjektFactory` succeed ✅
 */
interface InjektScope : InjektFactory {

    // All methods are inherited from InjektFactory:
    //
    // fun <R> getInstance(type: Type): R
    // fun <R> getInstanceOrElse(type: Type, default: R): R
    // fun <R> getInstanceOrElse(type: Type, defaultProvider: () -> R): R
    // fun <R> getInstanceOrNull(type: Type): R?
    // fun <R, K> getKeyedInstance(type: Type, key: K): R
    // fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, default: R): R
    // fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, defaultProvider: () -> R): R
    // fun <R, K> getKeyedInstanceOrNull(type: Type, key: K): R?
    // fun <R> getLogger(type: Type, name: String): R
    // fun <R, T> getLogger(type: Type, clazz: Class<T>): R
}

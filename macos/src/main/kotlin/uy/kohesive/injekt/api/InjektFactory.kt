package uy.kohesive.injekt.api

import java.lang.reflect.Type

/**
 * Injekt factory interface stub for macOS desktop.
 *
 * Extensions compiled against the keiyoushi Injekt library call
 * `InjektKt.getInjekt()` and cast the result to this interface.
 * The inlined `injectLazy()` bytecode then calls one of the
 * `getInstance`/`getInstanceOrElse`/etc. methods with the requested type.
 *
 * This is a local stub matching the `uy.kohesive.injekt.api.InjektFactory`
 * interface from `injekt-api:1.16.1`. We define it locally (just like
 * [InjektScope] and [InjektRegistrar]) to avoid JAR version conflicts
 * between the Kotlin compiler and the injekt-api library.
 */
interface InjektFactory {

    /**
     * Resolve a dependency instance by java.lang.reflect.Type.
     */
    fun <R> getInstance(type: Type): R

    /**
     * Resolve with a fallback default value if not registered.
     */
    fun <R> getInstanceOrElse(type: Type, default: R): R

    /**
     * Resolve with a fallback provider if not registered.
     */
    fun <R> getInstanceOrElse(type: Type, defaultProvider: () -> R): R

    /**
     * Resolve returning null if not registered.
     */
    fun <R> getInstanceOrNull(type: Type): R?

    /**
     * Resolve a keyed (named) dependency instance.
     */
    fun <R, K> getKeyedInstance(type: Type, key: K): R

    /**
     * Resolve a keyed instance with a fallback default value.
     */
    fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, default: R): R

    /**
     * Resolve a keyed instance with a fallback provider.
     */
    fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, defaultProvider: () -> R): R

    /**
     * Resolve a keyed instance returning null if not registered.
     */
    fun <R, K> getKeyedInstanceOrNull(type: Type, key: K): R?

    /**
     * Resolve a logger instance by name.
     */
    fun <R> getLogger(type: Type, name: String): R

    /**
     * Resolve a logger instance by class.
     */
    fun <R, T> getLogger(type: Type, clazz: Class<T>): R
}

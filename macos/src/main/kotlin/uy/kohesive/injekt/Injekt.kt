package uy.kohesive.injekt

import kotlin.reflect.KClass
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektScope
import java.lang.reflect.Type

// NOTE: InjektFactory and InjektScope are defined locally (not imported from JAR)
// to avoid Kotlin compiler version conflicts with injekt-api:1.16.1.
//
// In ALL versions of injekt-api, `getInjekt()` returns `InjektScope`.
// `InjektScope` extends `InjektFactory` (added in 1.16.1). By returning
// `InjektScope`, we satisfy BOTH:
//   - JVM method resolution: `getInjekt()` → `InjektScope` ✅
//   - Extensions casting result to `InjektFactory`: succeeds via inheritance ✅

/**
 * Injekt stub for macOS desktop.
 * Delegates all dependency resolution to Koin via GlobalContext.
 * This allows existing shared code using `Injekt.get<T>()` to compile and run
 * on desktop without changes to the shared source files.
 */
object Injekt {

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> get(): T {
        return GlobalContext.get().get<T>(T::class)
    }

    fun importModule(module: InjektModule) {
        // On macOS, modules are registered directly in Koin via KoinApplication.
        // This exists for API compatibility with shared code that calls importModule.
        // See AnikkuApplication.kt for the macOS Koin module setup.
    }
}

/**
 * Internal InjektScope implementation that delegates to Koin's GlobalContext.
 *
 * Implements [InjektScope] (which extends [InjektFactory]), so it satisfies
 * all extensions regardless of which type they expect from `getInjekt()`.
 */
private val macosInjektScope = object : InjektScope {

    /**
     * Convert a [Type] to a [KClass] for Koin lookup.
     */
    private fun resolveToKClass(type: Type): KClass<*> {
        @Suppress("UNCHECKED_CAST")
        val cls: Class<*> = when (type) {
            is Class<*> -> type
            is java.lang.reflect.ParameterizedType -> type.rawType as Class<*>
            else -> Any::class.java
        }
        // kotlin extension property on Class<T> (from kotlin-stdlib)
        return cls.kotlin
    }

    /** Resolve via Koin using the Type. */
    @Suppress("UNCHECKED_CAST")
    private fun <R> resolve(type: Type): R {
        return GlobalContext.get().get(resolveToKClass(type)) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstance(type: Type): R {
        return resolve(type)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstanceOrElse(type: Type, default: R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            default
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstanceOrElse(type: Type, defaultProvider: () -> R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            defaultProvider()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstanceOrNull(type: Type): R? {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstance(type: Type, key: K): R {
        return GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, default: R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
        } catch (_: Exception) {
            default
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, defaultProvider: () -> R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
        } catch (_: Exception) {
            defaultProvider()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstanceOrNull(type: Type, key: K): R? {
        return try {
            GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getLogger(type: Type, name: String): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            @Suppress("UNCHECKED_CAST")
            val noop = Any() as R
            noop
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, T> getLogger(type: Type, clazz: Class<T>): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            @Suppress("UNCHECKED_CAST")
            val noop = Any() as R
            noop
        }
    }
}

/**
 * Top-level function that provides the InjektScope for extension DI resolution.
 * Extensions call this as `InjektKt.getInjekt()` from their inlined `injectLazy()`.
 *
 * Returns [InjektScope] which extends [InjektFactory], so the returned value
 * satisfies BOTH types. This matches ALL versions of injekt-api where
 * `getInjekt()` returns `InjektScope` (not `InjektFactory` directly).
 */
fun getInjekt(): InjektScope = macosInjektScope

/**
 * No-op on desktop. On Android, this patches Injekt for compatibility with older versions.
 */
fun patchInjekt() {
    // No-op on macOS desktop
}

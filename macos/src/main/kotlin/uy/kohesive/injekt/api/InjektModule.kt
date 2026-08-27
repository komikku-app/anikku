package uy.kohesive.injekt.api

/**
 * Injekt module interface stub for macOS desktop.
 * Modules implement this interface and register their injectables
 * in the [InjektRegistrar.registerInjectables] method.
 *
 * On macOS, modules are registered directly via Koin modules instead.
 * This interface exists for source compatibility with shared code.
 */
interface InjektModule {
    fun InjektRegistrar.registerInjectables()
}

package uy.kohesive.injekt.api

/**
 * Injekt registrar interface stub for macOS desktop.
 * Provides registration methods that mirror Injekt's API for source compatibility.
 * On macOS, prefer registering directly via Koin modules in AnikkuApplication.kt.
 */
interface InjektRegistrar {

    fun <T : Any> addSingletonFactory(block: () -> T)

    fun <T : Any> addSingleton(instance: T)

    fun <T : Any> addFactory(block: () -> T)

    fun <T : Any> get(): T

    companion object {
        /**
         * Creates an InjektRegistrar that delegates to a Koin module's single/factory definitions.
         * Used by Injekt.importModule() when modules are registered.
         */
        fun noOp(): InjektRegistrar = NoOpRegistrar
    }
}

/**
 * No-op registrar. Modules calling registerInjectables on macOS will use this by default.
 * Actual registrations happen via Koin modules in AnikkuApplication.kt.
 */
private object NoOpRegistrar : InjektRegistrar {
    override fun <T : Any> addSingletonFactory(block: () -> T) {}
    override fun <T : Any> addSingleton(instance: T) {}
    override fun <T : Any> addFactory(block: () -> T) {}
    override fun <T : Any> get(): T = error("NoOpRegistrar.get() called — register dependencies via Koin modules")
}

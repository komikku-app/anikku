package app.anikku.macos.di

import app.anikku.macos.AnikkuApplication
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

/**
 * Application-level Koin module.
 *
 * Registers the application CoroutineScope. Compose owns window, navigation,
 * and theme state at the UI lifecycle boundary.
 */
fun appModule(app: AnikkuApplication) = module {

    // Application scope (shared across all screens and background tasks)
    single<CoroutineScope> { app.applicationScope }
}

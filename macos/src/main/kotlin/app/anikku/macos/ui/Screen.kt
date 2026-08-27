package app.anikku.macos.ui

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey

/**
 * Base Screen class for Anikku macOS.
 * Provides unique screen keys for Voyager navigation.
 *
 * Ported from eu.kanade.presentation.util.Screen
 */
abstract class AnikkuScreen : Screen {

    override val key: ScreenKey = uniqueScreenKey
}

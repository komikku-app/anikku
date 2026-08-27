package app.anikku.macos.ui.screens.browse

import org.junit.Test

class ExtensionsScreenTest {

    @Test
    fun `ExtensionsScreen is properly constructable`() {
        val screen = ExtensionsScreen(extensionManager = null)
        assert(screen is cafe.adriel.voyager.core.screen.Screen)
    }

    @Test
    fun `ExtensionsScreen key is unique`() {
        val screen1 = ExtensionsScreen()
        val screen2 = ExtensionsScreen()
        assert(screen1.key != screen2.key, { "Each ExtensionsScreen should have a unique key" })
    }
}

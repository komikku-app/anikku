package app.anikku.macos.ui.screens.browse

import org.junit.Test

/**
 * Unit tests for [BrowseTab].
 *
 * Verifies tab structure. The Browse tab currently uses inline
 * SourceInfo mock data (not from MockData object).
 */
class BrowseTabTest {

    @Test
    fun `BrowseTab is an object`() {
        // Verify the tab is properly constructable
        val tab = BrowseTab
        assert(tab is cafe.adriel.voyager.navigator.tab.Tab)
    }
}

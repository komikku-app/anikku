package app.anikku.macos.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalKeyboardShortcutsTest {

    @BeforeEach
    fun setUp() {
        GlobalKeyboardShortcuts.reset()
    }

    @AfterEach
    fun tearDown() {
        GlobalKeyboardShortcuts.reset()
    }

    @Test
    fun `callbacks are null initially`() {
        assertNull(GlobalKeyboardShortcuts.onToggleSidebar)
        assertNull(GlobalKeyboardShortcuts.onOpenSearch)
        assertNull(GlobalKeyboardShortcuts.onOpenSettings)
        assertNull(GlobalKeyboardShortcuts.onNewSource)
    }

    @Test
    fun `initialize sets all callbacks`() {
        GlobalKeyboardShortcuts.initialize(
            onToggleSidebar = {},
            onOpenSearch = {},
            onOpenSettings = {},
            onNewSource = {},
        )
        assertNotNull(GlobalKeyboardShortcuts.onToggleSidebar)
        assertNotNull(GlobalKeyboardShortcuts.onOpenSearch)
        assertNotNull(GlobalKeyboardShortcuts.onOpenSettings)
        assertNotNull(GlobalKeyboardShortcuts.onNewSource)
    }

    @Test
    fun `initialize callbacks are invoked when called`() {
        var sidebarToggled = false
        var searchOpened = false
        var settingsOpened = false
        var newSourceOpened = false

        GlobalKeyboardShortcuts.initialize(
            onToggleSidebar = { sidebarToggled = true },
            onOpenSearch = { searchOpened = true },
            onOpenSettings = { settingsOpened = true },
            onNewSource = { newSourceOpened = true },
        )

        GlobalKeyboardShortcuts.onToggleSidebar?.invoke()
        GlobalKeyboardShortcuts.onOpenSearch?.invoke()
        GlobalKeyboardShortcuts.onOpenSettings?.invoke()
        GlobalKeyboardShortcuts.onNewSource?.invoke()

        assert(sidebarToggled)
        assert(searchOpened)
        assert(settingsOpened)
        assert(newSourceOpened)
    }

    @Test
    fun `reset clears all callbacks`() {
        GlobalKeyboardShortcuts.initialize(
            onToggleSidebar = {},
            onOpenSearch = {},
            onOpenSettings = {},
            onNewSource = {},
        )
        GlobalKeyboardShortcuts.reset()
        assertNull(GlobalKeyboardShortcuts.onToggleSidebar)
        assertNull(GlobalKeyboardShortcuts.onOpenSearch)
        assertNull(GlobalKeyboardShortcuts.onOpenSettings)
        assertNull(GlobalKeyboardShortcuts.onNewSource)
    }
}

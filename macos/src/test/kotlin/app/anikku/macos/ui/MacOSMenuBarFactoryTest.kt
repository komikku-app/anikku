package app.anikku.macos.ui

import org.junit.jupiter.api.Test
import java.awt.Frame
import java.awt.MenuBar
import java.awt.MenuItem
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class MacOSMenuBarFactoryTest {

    private fun createTestFrame(): Frame = Frame("Test")

    @Test
    fun `create returns MenuBar with 7 menus`() {
        val frame = createTestFrame()
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})

        assertEquals(7, menuBar.menuCount,
            "Should have 7 top-level menus: Anikku, File, Edit, View, Playback, Window, Help")
    }

    @Test
    fun `attach sets menuBar on the frame`() {
        val frame = createTestFrame()
        MacOSMenuBarFactory.attach(frame, onQuit = {})

        assertNotNull(frame.menuBar, "Frame should have a menu bar after attach()")
        assertEquals(7, frame.menuBar.menuCount)
    }

    @Test
    fun `Quit Anikku invokes onQuit callback`() {
        val frame = createTestFrame()
        var quitCalled = false
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = { quitCalled = true })

        val appMenu = menuBar.getMenu(0) // First menu = Anikku app menu
        val quitItem = findMenuItem(appMenu, "Quit Anikku")
        assertNotNull(quitItem)

        // Simulate menu item click
        fireAction(quitItem!!)

        assertTrue(quitCalled, "Quit should invoke onQuit callback")
    }

    @Test
    fun `Close Window invokes onQuit callback`() {
        val frame = createTestFrame()
        var quitCalled = false
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = { quitCalled = true })

        val fileMenu = menuBar.getMenu(1) // Second menu = File
        val closeItem = findMenuItem(fileMenu, "Close Window")
        assertNotNull(closeItem)

        fireAction(closeItem!!)

        assertTrue(quitCalled, "Close Window should invoke onQuit callback")
    }

    @Test
    fun `Settings invokes onSettings callback`() {
        val frame = createTestFrame()
        var settingsCalled = false
        val menuBar = MacOSMenuBarFactory.create(frame,
            onQuit = {},
            onSettings = { settingsCalled = true })

        val appMenu = menuBar.getMenu(0)
        val settingsItem = findMenuItem(appMenu, "Settings...")
        assertNotNull(settingsItem)

        fireAction(settingsItem!!)

        assertTrue(settingsCalled, "Settings should invoke onSettings callback")
    }

    @Test
    fun `Open Backup invokes onOpenBackup callback`() {
        val frame = createTestFrame()
        var backupCalled = false
        val menuBar = MacOSMenuBarFactory.create(frame,
            onQuit = {},
            onOpenBackup = { backupCalled = true })

        val fileMenu = menuBar.getMenu(1)
        val openItem = findMenuItem(fileMenu, "Open Backup...")
        assertNotNull(openItem)

        fireAction(openItem!!)

        assertTrue(backupCalled, "Open Backup should invoke onOpenBackup callback")
    }

    @Test
    fun `Hide Anikku minimizes frame`() {
        val frame = createTestFrame()
        frame.state = Frame.NORMAL
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})

        val appMenu = menuBar.getMenu(0)
        val hideItem = findMenuItem(appMenu, "Hide Anikku")
        assertNotNull(hideItem)

        fireAction(hideItem!!)

        assertEquals(Frame.ICONIFIED, frame.state,
            "Hide Anikku should minimize the frame")
    }

    @Test
    fun `Minimize minimizes frame`() {
        val frame = createTestFrame()
        frame.state = Frame.NORMAL
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})

        val windowMenu = menuBar.getMenu(5) // Window menu
        val minimizeItem = findMenuItem(windowMenu, "Minimize")
        assertNotNull(minimizeItem)

        fireAction(minimizeItem!!)

        assertEquals(Frame.ICONIFIED, frame.state,
            "Minimize should set frame state to ICONIFIED")
    }

    @Test
    fun `Zoom toggles extended state`() {
        val frame = createTestFrame()
        frame.extendedState = Frame.NORMAL
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})

        val windowMenu = menuBar.getMenu(5)
        val zoomItem = findMenuItem(windowMenu, "Zoom")
        assertNotNull(zoomItem)

        // First click: enter maximized
        fireAction(zoomItem!!)
        assertTrue(frame.extendedState and Frame.MAXIMIZED_BOTH != 0,
            "First Zoom click should maximize")

        // Second click: exit maximized
        fireAction(zoomItem)
        assertEquals(Frame.NORMAL, frame.extendedState and Frame.MAXIMIZED_BOTH,
            "Second Zoom click should restore normal")
    }

    @Test
    fun `Toggle Sidebar invokes the global sidebar callback`() {
        val frame = createTestFrame()
        var toggled = false
        GlobalKeyboardShortcuts.onToggleSidebar = { toggled = true }
        try {
            val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})
            val viewMenu = menuBar.getMenu(3)
            val toggleItem = findMenuItem(viewMenu, "Toggle Sidebar")
            assertNotNull(toggleItem)

            fireAction(toggleItem!!)
            assertTrue(toggled, "Toggle Sidebar should invoke the registered callback")
        } finally {
            GlobalKeyboardShortcuts.onToggleSidebar = null
        }
    }

    @Test
    fun `Find invokes global search callback with command F shortcut`() {
        val frame = createTestFrame()
        var opened = false
        GlobalKeyboardShortcuts.onOpenSearch = { opened = true }
        try {
            val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})
            val editMenu = menuBar.getMenu(2)
            val findItem = findMenuItem(editMenu, "Find…")
            assertNotNull(findItem)
            assertEquals(java.awt.event.KeyEvent.VK_F, findItem!!.shortcut.key)

            fireAction(findItem)
            assertTrue(opened)
        } finally {
            GlobalKeyboardShortcuts.onOpenSearch = null
        }
    }

    @Test
    fun `default callbacks are no-ops`() {
        val frame = createTestFrame()
        // Should not throw with no callbacks provided
        val menuBar = MacOSMenuBarFactory.create(frame, onQuit = {})

        assertNotNull(menuBar)
        assertEquals(7, menuBar.menuCount)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun findMenuItem(menu: java.awt.Menu, label: String): MenuItem? {
        for (i in 0 until menu.itemCount) {
            val item = menu.getItem(i)
            if (item?.label == label) return item
        }
        return null
    }

    private fun fireAction(item: MenuItem) {
        val listeners = item.actionListeners
        for (listener in listeners) {
            listener.actionPerformed(ActionEvent(item, ActionEvent.ACTION_PERFORMED, item.label))
        }
    }
}

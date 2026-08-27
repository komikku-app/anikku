package app.anikku.macos.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TabSwitchHandlerTest {

    @AfterEach
    fun tearDown() {
        // Clean up global state between tests
        TabSwitchHandler.onSwitchTab = null
    }

    @Test
    fun `switchTo invokes registered callback with correct index`() {
        val received = mutableListOf<Int>()
        TabSwitchHandler.onSwitchTab = { index -> received.add(index) }

        TabSwitchHandler.switchTo(0)
        TabSwitchHandler.switchTo(3)
        TabSwitchHandler.switchTo(4)

        assertEquals(listOf(0, 3, 4), received)
    }

    @Test
    fun `switchTo is no-op when callback is null`() {
        TabSwitchHandler.onSwitchTab = null
        // Should not throw
        TabSwitchHandler.switchTo(0)
        TabSwitchHandler.switchTo(-1)
        TabSwitchHandler.switchTo(999)
    }

    @Test
    fun `switchTo does not throw with negative index`() {
        val received = mutableListOf<Int>()
        TabSwitchHandler.onSwitchTab = { index -> received.add(index) }

        TabSwitchHandler.switchTo(-1)

        assertEquals(listOf(-1), received)
    }

    @Test
    fun `switchTo with large out-of-bounds index still invokes callback`() {
        val received = mutableListOf<Int>()
        TabSwitchHandler.onSwitchTab = { index -> received.add(index) }

        TabSwitchHandler.switchTo(999)

        // Handler passes index through; bounds checking is the caller's responsibility
        assertEquals(listOf(999), received)
    }

    @Test
    fun `callback can be replaced`() {
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()

        TabSwitchHandler.onSwitchTab = { index -> first.add(index) }
        TabSwitchHandler.switchTo(1)

        TabSwitchHandler.onSwitchTab = { index -> second.add(index) }
        TabSwitchHandler.switchTo(2)

        assertEquals(listOf(1), first)
        assertEquals(listOf(2), second)
    }

    @Test
    fun `callback can be set to null after being registered`() {
        var callCount = 0
        TabSwitchHandler.onSwitchTab = { callCount++ }
        TabSwitchHandler.switchTo(0)

        TabSwitchHandler.onSwitchTab = null
        TabSwitchHandler.switchTo(1)

        assertEquals(1, callCount)
    }

    @Test
    fun `callback is initially null`() {
        assertNull(TabSwitchHandler.onSwitchTab)
    }
}

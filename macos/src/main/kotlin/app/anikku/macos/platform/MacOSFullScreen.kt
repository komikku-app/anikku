package app.anikku.macos.platform

import app.anikku.macos.platform.ObjC
import java.awt.Frame

/**
 * macOS-native fullscreen toggle via JNA/Objective-C bridge.
 *
 * Calls `[NSWindow toggleFullScreen:]` on the NSWindow underlying
 * an AWT Frame, producing proper macOS fullscreen Space behavior
 * (like the green traffic light button), unlike AWT's game-style
 * exclusive fullscreen which hides the menu bar and Dock.
 *
 * Requires JVM args:
 *   --add-exports java.desktop/sun.lwawt.macosx=ALL-UNNAMED
 *   --add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED
 */
object MacOSFullScreen {

    /** True if we successfully toggled fullscreen at least once (implies JNA bridge works). */
    var isJnaAvailable: Boolean = false
        private set

    /**
     * Toggles macOS-native fullscreen Space for the given AWT Frame.
     *
     * Uses JNA to call the Objective-C runtime directly, sending
     * `toggleFullScreen:` to the Frame's underlying NSWindow.
     *
     * This must be called on the AWT Event Dispatch Thread (EDT),
     * which is the case when invoked from an AWT MenuItem action listener.
     *
     * @param frame The AWT Frame whose NSWindow should toggle fullscreen
     * @return true if the toggle was invoked successfully
     */
    fun toggleFullScreen(frame: Frame): Boolean {
        return try {
            val nsWindowPtr = getNSWindowPointer(frame)
            if (nsWindowPtr == 0L) {
                isJnaAvailable = false
                return false
            }

            val selector = ObjC.sel_registerName("toggleFullScreen:")
            ObjC.objc_msgSend(
                com.sun.jna.Pointer.createConstant(nsWindowPtr),
                selector,
            )
            isJnaAvailable = true
            true
        } catch (e: Exception) {
            // JNA bridge not available (e.g., wrong JDK, missing --add-exports)
            isJnaAvailable = false
            false
        }
    }

    /**
     * Retrieves the native NSWindow pointer from an AWT Frame.
     *
     * Uses the internal `sun.lwawt.macosx.CPlatformWindow` API.
     * Returns 0 if the peer is not yet initialized or the internal
     * API is inaccessible (missing --add-exports).
     */
    private fun getNSWindowPointer(frame: Frame): Long {
        return try {
            val peer = frame.javaClass.getMethod("getPeer").invoke(frame)
            val platformWindow = peer.javaClass
                .getMethod("getPlatformWindow")
                .invoke(peer)
            platformWindow.javaClass
                .getMethod("getNSWindowPtr")
                .invoke(platformWindow) as Long
        } catch (e: Exception) {
            0L
        }
    }
}

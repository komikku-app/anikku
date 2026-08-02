package app.anikku.macos.ui

import app.anikku.macos.platform.MacOSFullScreen
import app.anikku.macos.platform.web.BrowserLauncher
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Menu
import java.awt.MenuBar
import java.awt.MenuItem
import java.awt.MenuShortcut
import java.awt.event.KeyEvent
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent

/**
 * macOS native menu bar per AD-04 (Phase 9.1).
 *
 * Since Compose Desktop 1.11.x's `MenuBar`/`Menu`/`Item` composable API
 * is not available (scope resolution issues prevent compilation), this
 * implementation uses the standard `java.awt.MenuBar` API attached to
 * the underlying AWT Frame of the Compose Window.
 *
 * **AWT MenuShortcut limitations:** Can only express ⌘ and ⇧⌘ shortcuts.
 * AD-04 shortcuts using ⌃⌘ (ctrl+cmd) or ⌥⌘ (alt+cmd) are approximated:
 * - ⌃⌘S → ⌘S (Toggle Sidebar), ⇧⌘S (Save Backup)
 * - ⌃⌘F → ⌘F (Toggle Full Screen)
 * - ⌥⌘H → ⇧⌘H (Hide Others)
 *
 * On macOS, the first AWT Menu automatically becomes the application menu
 * (next to the Apple menu), and AWT MenuShortcuts render as ⌘-key shortcuts.
 *
 * Usage (inside Window content lambda):
 * ```
 * (window as? Frame)?.let { frame ->
 *     MacOSMenuBarFactory.attach(frame, onQuit, onSettings)
 * }
 * ```
 */
object MacOSMenuBarFactory {

    /** Callback for playback menu actions (set by AnikkuApp when player is active). */
    var onPlaybackAction: ((PlaybackAction) -> Unit)? = null

    /** Callback for save backup (set by AnikkuApp). */
    var onSaveBackup: (() -> Unit)? = null

    enum class PlaybackAction { PLAY_PAUSE, SKIP_FORWARD, SKIP_BACKWARD, VOLUME_UP, VOLUME_DOWN }

    /**
     * Creates the full macOS menu bar with all AD-04 menus.
     *
     * @param frame The AWT Frame (needed for minimize/zoom/fullscreen actions)
 * @param onQuit Called when Quit (⌘Q) or Close Window (⌘W) is selected
 * @param onSettings Called when Settings... (⌘,) is selected
 * @param onAbout Called when About Anikku is selected
 * @param onCheckForUpdates Called when Check for Updates... is selected
 */
fun create(
    frame: Frame,
    onQuit: () -> Unit,
    onSettings: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onAbout: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
): MenuBar {
        return MenuBar().apply {
            // App menu (first menu becomes macOS application menu)
            add(appMenu(frame, onQuit, onSettings, onAbout, onCheckForUpdates))
            add(fileMenu(onQuit, onOpenBackup))
            add(editMenu())
            add(viewMenu(frame))
            add(playbackMenu())
            add(windowMenu(frame))
            add(helpMenu())
        }
    }

    /**
     * Attaches the menu bar to the AWT Frame underlying a Compose Window.
     * Call this inside the Window content lambda.
     *
     * @param frame The AWT Frame (from FrameWindowScope.window)
     * @param onQuit Called when Quit (⌘Q) or Close Window (⌘W) is selected
     * @param onSettings Called when Settings... (⌘,) is selected
     */
    fun attach(
        frame: Frame,
        onQuit: () -> Unit,
        onSettings: () -> Unit = {},
        onOpenBackup: () -> Unit = {},
        onAbout: () -> Unit = {},
        onCheckForUpdates: () -> Unit = {},
    ) {
        frame.menuBar = create(frame, onQuit, onSettings, onOpenBackup, onAbout, onCheckForUpdates)
    }

    // ---------------------------------------------------------------------------
    // macOS Application Menu
    // ---------------------------------------------------------------------------
    private fun appMenu(
        frame: Frame,
        onQuit: () -> Unit,
        onSettings: () -> Unit,
        onAbout: () -> Unit,
        onCheckForUpdates: () -> Unit = {},
    ): Menu {
        return Menu("Anikku").apply {
            add(MenuItem("About Anikku").also {
                it.addActionListener { onAbout() }
            })
            add(MenuItem("Check for Updates...").also {
                it.addActionListener { onCheckForUpdates() }
            })
            addSeparator()
            add(MenuItem("Settings...", MenuShortcut(KeyEvent.VK_COMMA, false)).also {
                it.addActionListener { onSettings() }
            })
            addSeparator()
            add(MenuItem("Hide Anikku", MenuShortcut(KeyEvent.VK_H, false)).also {
                it.addActionListener { frame.state = Frame.ICONIFIED }
            })
            add(MenuItem("Hide Others", MenuShortcut(KeyEvent.VK_H, true)).also {
                it.addActionListener { hideOtherWindows(frame) }
            })
            add(MenuItem("Show All").also {
                it.addActionListener { showAllWindows() }
            })
            addSeparator()
            add(MenuItem("Quit Anikku", MenuShortcut(KeyEvent.VK_Q, false)).also {
                it.addActionListener { onQuit() }
            })
        }
    }

    // ---------------------------------------------------------------------------
    // File Menu
    // ---------------------------------------------------------------------------
    private fun fileMenu(onQuit: () -> Unit, onOpenBackup: () -> Unit): Menu {
        return Menu("File").apply {
            add(MenuItem("Open Backup...", MenuShortcut(KeyEvent.VK_O, false)).also {
                it.addActionListener { onOpenBackup() }
            })
            add(MenuItem("Save Backup...", MenuShortcut(KeyEvent.VK_S, true)).also {
                it.addActionListener { onSaveBackup?.invoke() ?: openBackupHelp() }
                // ⇧⌘S — AD-04 specifies ⌃⌘S but AWT cannot express ctrl+cmd;
                // using ⇧⌘S (Save As convention) to avoid conflict with Sidebar ⌘S
            })
            addSeparator()
            add(MenuItem("Close Window", MenuShortcut(KeyEvent.VK_W, false)).also {
                it.addActionListener { onQuit() }
            })
        }
    }

    // ---------------------------------------------------------------------------
    // Edit Menu
    //
    // Compose Desktop TextField composables handle ⌘C/⌘V/⌘X/⌘A/⌘Z/⌘⇧Z
    // natively through the Compose focus system. The action listeners below
    // dispatch to any focused AWT/Swing text component as a fallback for
    // non-Compose text fields (e.g., native dialogs, Swing interop).
    //
    // Action names use the well-known Swing action keys (strings, not fields)
    // that all JTextComponent subclasses register in their ActionMap.
    // ---------------------------------------------------------------------------
    private fun editMenu(): Menu {
        return Menu("Edit").apply {
            add(MenuItem("Undo", MenuShortcut(KeyEvent.VK_Z, false)).also {
                it.addActionListener { dispatchEditAction("undo") }
            })
            add(MenuItem("Redo", MenuShortcut(KeyEvent.VK_Z, true)).also {
                it.addActionListener { dispatchEditAction("redo") }
            })
            addSeparator()
            add(MenuItem("Cut", MenuShortcut(KeyEvent.VK_X, false)).also {
                it.addActionListener { dispatchEditAction(DefaultEditorKit.cutAction) }
            })
            add(MenuItem("Copy", MenuShortcut(KeyEvent.VK_C, false)).also {
                it.addActionListener { dispatchEditAction(DefaultEditorKit.copyAction) }
            })
            add(MenuItem("Paste", MenuShortcut(KeyEvent.VK_V, false)).also {
                it.addActionListener { dispatchEditAction(DefaultEditorKit.pasteAction) }
            })
            add(MenuItem("Select All", MenuShortcut(KeyEvent.VK_A, false)).also {
                it.addActionListener { dispatchEditAction(DefaultEditorKit.selectAllAction) }
            })
        }
    }

    /**
     * Dispatch an edit action to the currently focused AWT/Swing text component.
     * Compose Desktop TextFields handle these shortcuts natively; this fallback
     * covers non-Compose text components (Swing interop, native dialogs).
     */
    private fun dispatchEditAction(actionName: String) {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner is JTextComponent) {
            val actionMap = focusOwner.actionMap
            val action = actionMap.get(actionName)
            if (action != null) {
                val event = java.awt.event.ActionEvent(
                    focusOwner,
                    java.awt.event.ActionEvent.ACTION_PERFORMED,
                    actionName,
                )
                action.actionPerformed(event)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // View Menu
    // ---------------------------------------------------------------------------
    private fun viewMenu(frame: Frame): Menu {
        return Menu("View").apply {
            add(MenuItem("Library", MenuShortcut(KeyEvent.VK_1, false)).also {
                it.addActionListener { TabSwitchHandler.switchTo(0) }
            })
            add(MenuItem("Updates", MenuShortcut(KeyEvent.VK_2, false)).also {
                it.addActionListener { TabSwitchHandler.switchTo(1) }
            })
            add(MenuItem("History", MenuShortcut(KeyEvent.VK_3, false)).also {
                it.addActionListener { TabSwitchHandler.switchTo(2) }
            })
            add(MenuItem("Browse", MenuShortcut(KeyEvent.VK_4, false)).also {
                it.addActionListener { TabSwitchHandler.switchTo(3) }
            })
            addSeparator()
            add(MenuItem("Toggle Sidebar", MenuShortcut(KeyEvent.VK_S, false)).also {
                it.addActionListener { GlobalKeyboardShortcuts.onToggleSidebar?.invoke() }
            })
            add(MenuItem("Toggle Full Screen", MenuShortcut(KeyEvent.VK_F, false)).also {
                it.addActionListener { MacOSFullScreen.toggleFullScreen(frame) }
            })
        }
    }

    // ---------------------------------------------------------------------------
    // Playback Menu
    // ---------------------------------------------------------------------------
    private fun playbackMenu(): Menu {
        return Menu("Playback").apply {
            add(MenuItem("Play / Pause").also {
                it.addActionListener { onPlaybackAction?.invoke(PlaybackAction.PLAY_PAUSE) }
            })
            add(MenuItem("Skip Forward").also {
                it.addActionListener { onPlaybackAction?.invoke(PlaybackAction.SKIP_FORWARD) }
            })
            add(MenuItem("Skip Backward").also {
                it.addActionListener { onPlaybackAction?.invoke(PlaybackAction.SKIP_BACKWARD) }
            })
            addSeparator()
            add(MenuItem("Volume Up").also {
                it.addActionListener { onPlaybackAction?.invoke(PlaybackAction.VOLUME_UP) }
            })
            add(MenuItem("Volume Down").also {
                it.addActionListener { onPlaybackAction?.invoke(PlaybackAction.VOLUME_DOWN) }
            })
        }
    }

    // ---------------------------------------------------------------------------
    // Window Menu
    // ---------------------------------------------------------------------------
    private fun windowMenu(frame: Frame): Menu {
        return Menu("Window").apply {
            add(MenuItem("Minimize", MenuShortcut(KeyEvent.VK_M, false)).also {
                it.addActionListener { frame.state = Frame.ICONIFIED }
            })
            add(MenuItem("Zoom").also {
                it.addActionListener { toggleZoom(frame) }
            })
            addSeparator()
            add(MenuItem("Bring All to Front").also {
                it.addActionListener { /* macOS handles this via standard Window menu */ }
            })
        }
    }

    // ---------------------------------------------------------------------------
    // Help Menu
    // ---------------------------------------------------------------------------
    private fun helpMenu(): Menu {
        return Menu("Help").apply {
            add(MenuItem("Anikku Help").also {
                it.addActionListener { openHelpDocs() }
            })
            add(MenuItem("Report Issue...").also {
                it.addActionListener { openGitHubIssues() }
            })
        }
    }

    // ---------------------------------------------------------------------------
    // Window action helpers (Phase 9.2)
    // ---------------------------------------------------------------------------

    /** Opens the Anikku GitHub issues page in the system browser. */
    private fun openGitHubIssues() {
        BrowserLauncher.openSafe("https://github.com/ErnestHysa/anikku/issues/new")
    }

    /** Opens the Anikku help documentation in the system browser. */
    private fun openHelpDocs() {
        BrowserLauncher.openSafe("https://github.com/ErnestHysa/anikku/blob/master/BUILDING.md")
    }

    /** Opens backup help info in the browser. */
    private fun openBackupHelp() {
        BrowserLauncher.openSafe("https://github.com/ErnestHysa/anikku/blob/master/macos/README.md")
    }

    /** Minimize every other app window while leaving the active window unchanged. */
    private fun hideOtherWindows(activeFrame: Frame) {
        Frame.getFrames()
            .filter { it !== activeFrame && it.isDisplayable }
            .forEach { it.state = Frame.ICONIFIED }
    }

    /** Restore every displayable app window. */
    private fun showAllWindows() {
        Frame.getFrames()
            .filter { it.isDisplayable }
            .forEach {
                it.isVisible = true
                it.state = Frame.NORMAL
            }
    }

    /** Toggles the frame between maximized and normal state. */
    private fun toggleZoom(frame: Frame) {
        frame.extendedState = if (frame.extendedState and Frame.MAXIMIZED_BOTH != 0) {
            Frame.NORMAL
        } else {
            Frame.MAXIMIZED_BOTH
        }
    }

}

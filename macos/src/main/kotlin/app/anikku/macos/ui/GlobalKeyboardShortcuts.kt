package app.anikku.macos.ui

/**
 * Global keyboard shortcut handler for the Anikku macOS app.
 *
 * Bridges AWT keyboard events from the Window to appropriate app actions.
 * Acts as a companion to [TabSwitchHandler] for tab-related shortcuts.
 *
 * ## Shortcuts (Phase 9.2)
 *
 * | Shortcut | Action |
 * |---|---|
 * | ⌘, | Open Settings |
 * | ⌘F | Search (when applicable) |
 * | ⌘N | New / Browse source |
 * | ⌘W | Close window |
 * | ⌘Q | Quit |
 * | ⌘1–⌘5 | Switch tabs (Library, Updates, History, Browse, More) |
 * | Space | Play/Pause (when player focused) |
 * | ← → | Seek backward/forward |
 * | ↑ ↓ | Volume up/down |
 * | F / ⌘⌃F | Toggle Full Screen |
 * | ⌘S | Toggle Sidebar |
 * | ⌘, | Settings |
 *
 * These are dispatched to the focused composable via Compose's onKeyEvent.
 * The menu bar (AWT) handles ⌘-prefixed shortcuts globally.
 */
object GlobalKeyboardShortcuts {

    /** Callback for toggling the sidebar visibility. */
    var onToggleSidebar: (() -> Unit)? = null

    /** Callback for opening search. */
    var onOpenSearch: (() -> Unit)? = null

    /** Callback for opening settings. */
    var onOpenSettings: (() -> Unit)? = null

    /** Callback for triggering a new source browse. */
    var onNewSource: (() -> Unit)? = null

    /** Callback for opening the keyboard-shortcuts dialog (Help menu). */
    var onShowShortcuts: (() -> Unit)? = null

    /**
     * Initialize all global shortcut callbacks.
     *
     * @param onToggleSidebar Toggle navigation sidebar visibility.
     * @param onOpenSearch Focus the search bar in the current screen.
     * @param onOpenSettings Navigate to the Settings screen.
     * @param onNewSource Open the source browser.
     */
    fun initialize(
        onToggleSidebar: () -> Unit,
        onOpenSearch: () -> Unit,
        onOpenSettings: () -> Unit,
        onNewSource: () -> Unit,
    ) {
        this.onToggleSidebar = onToggleSidebar
        this.onOpenSearch = onOpenSearch
        this.onOpenSettings = onOpenSettings
        this.onNewSource = onNewSource
    }

    /**
     * Reset all callbacks (for cleanup).
     */
    fun reset() {
        onToggleSidebar = null
        onOpenSearch = null
        onOpenSettings = null
        onNewSource = null
    }
}

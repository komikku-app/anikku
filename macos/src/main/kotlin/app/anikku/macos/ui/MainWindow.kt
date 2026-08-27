package app.anikku.macos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import app.anikku.macos.platform.logging.UIActionLogger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import app.anikku.macos.ui.screens.BrowseScreen
import app.anikku.macos.ui.screens.HistoryScreen
import app.anikku.macos.ui.screens.LibraryScreen
import app.anikku.macos.ui.screens.MoreScreen
import app.anikku.macos.ui.screens.UpdatesScreen
import app.anikku.macos.ui.screens.discover.DiscoverTab
import app.anikku.macos.ui.screens.downloads.DownloadsTab
import app.anikku.macos.ui.screens.stats.StatsTab
import app.anikku.macos.ui.screens.torrent.TorrentTab
import app.anikku.macos.platform.MacOSDeepLinkHandler
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.ui.components.CommandPaletteDialog
import app.anikku.macos.ui.components.PaletteAction
import app.anikku.macos.ui.screens.browse.ExtensionsScreen
import app.anikku.macos.ui.screens.crashlog.CrashLogViewerScreen
import app.anikku.macos.ui.screens.downloads.DownloadQueueScreen
import app.anikku.macos.ui.screens.stats.StatsScreen
import app.anikku.macos.ui.screens.browse.GlobalSearchScreen
import app.anikku.macos.ui.screens.browse.SourceBrowseScreen
import app.anikku.macos.ui.screens.browse.SourceHealthChecker
import app.anikku.macos.ui.screens.player.PlayerScreen
import app.anikku.macos.ui.settings.LocalSettingsState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator

/**
 * Main application window composable.
 *
 * Sets up the Voyager TabNavigator with 9 tabs:
 * Library, Updates, History, Stats, Browse, Torrents, Downloads, Discover, More
 *
 * Uses a Material 3 NavigationRail (side navigation) for desktop layout,
 * matching macOS conventions where horizontal space is abundant.
 *
 * Ported from the Android HomeScreen.kt and MainActivity.kt.
 */
/** Ordered tab names matching [orderedTabs] (logging, ⌘1-9 shortcuts). */
internal val TAB_NAMES: List<String> = listOf(
    "Library", "Updates", "History", "Stats", "Browse", "Torrents", "Downloads", "Discover", "More",
)

/** Ordered tabs matching the View menu shortcuts (⌘1-9). */
internal val orderedTabs: List<Tab> = listOf(
    LibraryScreen,
    UpdatesScreen,
    HistoryScreen,
    StatsTab,
    BrowseScreen,
    TorrentTab,
    DownloadsTab,
    DiscoverTab,
    MoreScreen,
)

@Composable
fun MainWindow(
    initialTabIndex: Int = 0,
    onTabIndexChange: (Int) -> Unit = {},
    pendingBrowseSource: Pair<Long, String>? = null,
    onPendingBrowseSourceConsumed: () -> Unit = {},
) {
    val initialIndex = initialTabIndex.coerceIn(orderedTabs.indices)
    TabNavigator(
        tab = orderedTabs[initialIndex],
        key = "MainWindowTabs",
    ) { tabNavigator ->
        // Track current tab index via state — updated ONLY in event
        // handlers (onClick / TabSwitchHandler callback). We NEVER read
        // tabNavigator.current during composition because Voyager's
        // getter internally casts navigator.items.last() as Tab, which
        // throws ClassCastException when a non-Tab screen like
        // AnimeDetailScreen is on the tab's inner navigator stack.
        var currentTabIndex by remember { mutableStateOf(initialIndex) }
        val settings = LocalSettingsState.current
        var sidebarVisible by remember { mutableStateOf(settings.sidebarVisible) }
        var searchRequestId by remember { mutableLongStateOf(0L) }
        var handledSearchRequestId by remember { mutableLongStateOf(0L) }
        val extensionManager = LocalExtensionManager.current
        // Each tab's inner Navigator (declared before the rail so both the
        // rail handler and the back handlers can reach it).
        val tabNavigators = remember { mutableMapOf<Int, Navigator>() }

        /** Pop the current tab's pushed screen (if any). Player keeps its own Escape. */
        fun navigateBack(): Boolean {
            val navigator = tabNavigators[currentTabIndex] ?: return false
            if (!navigator.canPop) return false
            if (navigator.lastItemOrNull is PlayerScreen) return false
            navigator.pop()
            return true
        }

        /**
         * Tab activation hook: gates expensive per-tab work. Health checks for
         * the 60+ installed sources only run once the Browse tab is actually
         * on screen — otherwise the network burst stalls startup input.
         */
        fun onTabActivated(index: Int) {
            SourceHealthChecker.setChecksEnabled(index == 4)
        }

        // Reflect the restored initial tab.
        LaunchedEffect(Unit) {
            onTabActivated(initialIndex)
        }

        // Onboarding "open this source" jump: when the Browse tab is active
        // and a target is pending, push that source's browser once.
        LaunchedEffect(currentTabIndex, pendingBrowseSource) {
            val target = pendingBrowseSource ?: return@LaunchedEffect
            if (currentTabIndex != 4) return@LaunchedEffect
            val navigator = tabNavigators[4] ?: return@LaunchedEffect
            if (navigator.lastItemOrNull is SourceBrowseScreen) return@LaunchedEffect
            navigator.push(
                SourceBrowseScreen(
                    sourceId = target.first,
                    sourceName = target.second,
                    extensionManager = extensionManager,
                )
            )
            UIActionLogger.logNavigation("Onboarding", "SourceBrowse", "sourceId=${target.first}")
            onPendingBrowseSourceConsumed()
        }

        // Bridge the native View > Toggle Sidebar action to the Compose rail.
        DisposableEffect(tabNavigator) {
            val sidebarToggleHandler: () -> Unit = {
                sidebarVisible = !sidebarVisible
                settings.sidebarVisible = sidebarVisible
            }
            val searchHandler: () -> Unit = {
                tabNavigator.current = BrowseScreen
                currentTabIndex = 4
                onTabActivated(4)
                onTabIndexChange(4)
                searchRequestId++
            }
            GlobalKeyboardShortcuts.onToggleSidebar = sidebarToggleHandler
            GlobalKeyboardShortcuts.onOpenSearch = searchHandler
            onDispose {
                if (GlobalKeyboardShortcuts.onToggleSidebar === sidebarToggleHandler) {
                    GlobalKeyboardShortcuts.onToggleSidebar = null
                }
                if (GlobalKeyboardShortcuts.onOpenSearch === searchHandler) {
                    GlobalKeyboardShortcuts.onOpenSearch = null
                }
            }
        }

        // Bridge ⌘1-4/⌘5 View menu shortcuts to Voyager tab switching
        DisposableEffect(tabNavigator) {
            TabSwitchHandler.onSwitchTab = { index ->
                orderedTabs.getOrNull(index)?.let { tab ->
                    onTabActivated(index)
                    // Re-selecting the current tab returns to its root — the
                    // desktop convention (Settings → Downloads → ⌘8 → Settings).
                    if (index == currentTabIndex) {
                        tabNavigators[index]?.popUntilRoot()
                    } else {
                        tabNavigator.current = tab
                        currentTabIndex = index
                        onTabIndexChange(index)
                    }
                    val tabNames = TAB_NAMES
                    UIActionLogger.logNavigation("MenuShortcut", tabNames.getOrElse(index) { "?" }, "tab=$index")
                }
            }
            onDispose {
                TabSwitchHandler.onSwitchTab = null
            }
        }

        // Window-level back: Escape / ⌘[ (AWT dispatcher registered in
        // AnikkuApp) call this when the main window owns the key event.
        DisposableEffect(tabNavigators) {
            GlobalKeyboardShortcuts.onEscapeBack = ::navigateBack
            onDispose {
                GlobalKeyboardShortcuts.onEscapeBack = null
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            Row {
                // Side Navigation Rail — pass index to avoid reading
                // tabNavigator.current during composition
                if (sidebarVisible) {
                    NavigationRailSidebar(
                        currentTabIndex = currentTabIndex,
                        onSelectTab = { index ->
                        orderedTabs.getOrNull(index)?.let { tab ->
                            val tabNames = TAB_NAMES
                            UIActionLogger.logNavigation("NavigationRail", tabNames.getOrElse(index) { "?" }, "tab=$index")
                            onTabActivated(index)
                            // Re-selecting the current tab returns to its root —
                            // Settings → Downloads → More brings you back to
                            // Settings instead of re-showing the downloads screen.
                            if (index == currentTabIndex) {
                                tabNavigators[index]?.popUntilRoot()
                            } else {
                                tabNavigator.current = tab
                                currentTabIndex = index
                                onTabIndexChange(index)
                            }
                        }
                        },
                    )
                }

                // Tab content — all tabs stay composed so switching preserves
                // every tab's UI state (search/filter/scroll), inner navigator
                // stacks, and fetched data (Discover/Torrent load once at
                // startup instead of re-fetching on every switch). The active
                // tab is visible with a short fade; hidden tabs are kept alive
                // but inert (alpha 0 + input consumed).

                // anikku:// deep links (Watch Together join-page "Open in
                // Anikku"): push the player for the linked episode onto the
                // current tab. Delivered on the AWT event thread.
                DisposableEffect(tabNavigators, extensionManager) {
                    val deepLinkHandler: (MacOSDeepLinkHandler.WatchTarget) -> Unit = { target ->
                        val navigator = tabNavigators[currentTabIndex]
                        if (navigator != null) {
                            navigator.push(
                                PlayerScreen(
                                    animeId = target.animeId,
                                    episodeId = target.episodeId,
                                    sourceId = target.sourceId,
                                    episodeUrl = target.episodeUrl,
                                    animeTitle = target.animeTitle,
                                    episodeName = target.episodeName,
                                    episodeNumber = target.episodeNumber,
                                    coverUrl = target.coverUrl,
                                    extensionManager = extensionManager,
                                )
                            )
                            UIActionLogger.logNavigation("DeepLink", "anikku://watch", "episodeId=${target.episodeId}")
                        }
                    }
                    MacOSDeepLinkHandler.onWatchDeepLink = deepLinkHandler
                    onDispose {
                        if (MacOSDeepLinkHandler.onWatchDeepLink === deepLinkHandler) {
                            MacOSDeepLinkHandler.onWatchDeepLink = null
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            // Desktop back navigation: Escape or ⌘[ pops the
                            // current tab's pushed screen. The player owns its
                            // own Escape handling (exit fullscreen / back), so
                            // pass the event through when it's on top. (The
                            // AWT dispatcher in AnikkuApp normally handles
                            // this first; this Compose path covers focus cases
                            // the dispatcher can't see.)
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val isBack =
                                (!event.isMetaPressed && !event.isCtrlPressed && !event.isAltPressed && event.key == Key.Escape) ||
                                    (event.isMetaPressed && event.key == Key.LeftBracket)
                            if (!isBack) return@onPreviewKeyEvent false
                            navigateBack()
                        },
                ) {
                    orderedTabs.forEachIndexed { index, tab ->
                        val isCurrent = index == currentTabIndex
                        val alpha by animateFloatAsState(
                            targetValue = if (isCurrent) 1f else 0f,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab-fade-$index",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isCurrent) 1f else 0f)
                                .graphicsLayer { this.alpha = alpha }
                                .then(
                                    if (isCurrent) {
                                        Modifier
                                    } else {
                                        Modifier.pointerInput(tab) {
                                            // Keep-alive tabs must not swallow
                                            // clicks meant for the active tab.
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent().changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    },
                                ),
                        ) {
                            // Each tab gets its own nested Navigator so that
                            // non-Tab screens pushed from inside the tab (e.g.
                            // ExtensionsScreen, AnimeDetailScreen,
                            // SourceBrowseScreen) go to the inner navigator's
                            // stack instead of polluting the TabNavigator's.
                            //
                            // IMPORTANT: Use CurrentScreen() here, NOT
                            // CurrentTab(). CurrentScreen() renders whatever is
                            // on top of the inner Navigator's stack — initially
                            // the tab content, but also any pushed screens.
                            Navigator(tab) { navigator ->
                                tabNavigators[index] = navigator
                                LaunchedEffect(currentTabIndex, searchRequestId) {
                                    if (
                                        index == 4 &&
                                        searchRequestId > handledSearchRequestId
                                    ) {
                                        handledSearchRequestId = searchRequestId
                                        // ⌘F dedup: don't stack a second search
                                        // screen when one is already open.
                                        if (tabNavigators[index]?.lastItemOrNull !is GlobalSearchScreen) {
                                            navigator.push(GlobalSearchScreen(extensionManager = extensionManager))
                                        }
                                    }
                                }
                                CurrentScreen()
                            }
                        }
                    }
                }
            }
        }

        // ⌘K command palette — jump to any tab or pushed screen by typing.
        var showCommandPalette by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            val paletteHandler: () -> Unit = { showCommandPalette = true }
            GlobalKeyboardShortcuts.onOpenPalette = paletteHandler
            onDispose {
                if (GlobalKeyboardShortcuts.onOpenPalette === paletteHandler) {
                    GlobalKeyboardShortcuts.onOpenPalette = null
                }
            }
        }
        if (showCommandPalette) {
            CommandPaletteDialog(
                actions = rememberPaletteActions(tabNavigators, extensionManager),
                onClose = { showCommandPalette = false },
            )
        }
    }
}

/**
 * The ⌘K palette's action index. Tabs switch through [TabSwitchHandler];
 * pushed screens are pushed onto the target tab's inner navigator, so the
 * palette lands you on the exact screen without navigating by hand.
 */
@Composable
private fun rememberPaletteActions(
    tabNavigators: MutableMap<Int, Navigator>,
    extensionManager: MacOSExtensionManager?,
): List<PaletteAction> = remember(tabNavigators, extensionManager) {
    fun switchTo(index: Int) = TabSwitchHandler.switchTo(index)

    fun pushOn(index: Int, screen: Screen) {
        tabNavigators[index]?.push(screen)
    }

    listOf(
        PaletteAction("Library", "Tab ⌘1", keywords = listOf("library", "tab"), icon = Icons.Outlined.Book) {
            switchTo(0)
        },
        PaletteAction("Updates", "Tab ⌘2", keywords = listOf("updates", "new episodes"), icon = Icons.Outlined.Refresh) {
            switchTo(1)
        },
        PaletteAction("History", "Tab ⌘3", keywords = listOf("history", "watched"), icon = Icons.Outlined.History) {
            switchTo(2)
        },
        PaletteAction("Watch Stats", "Tab ⌘4 · also in Settings", keywords = listOf("stats", "statistics"), icon = Icons.Outlined.BarChart) {
            switchTo(3)
        },
        PaletteAction("Browse", "Tab ⌘5", keywords = listOf("browse", "sources"), icon = Icons.Outlined.Explore) {
            switchTo(4)
        },
        PaletteAction("Torrents", "Tab ⌘6", keywords = listOf("torrents", "nyaa"), icon = Icons.Outlined.Download) {
            switchTo(5)
        },
        PaletteAction("Downloads", "Tab ⌘7", keywords = listOf("downloads", "queue"), icon = Icons.Outlined.CloudDownload) {
            switchTo(6)
        },
        PaletteAction("Discover", "Tab ⌘8", keywords = listOf("discover", "trending", "seasonal"), icon = Icons.Outlined.Explore) {
            switchTo(7)
        },
        PaletteAction("Settings", "Tab ⌘9", keywords = listOf("settings", "more", "preferences"), icon = Icons.Outlined.Settings) {
            switchTo(8)
        },
        PaletteAction("Global Search", "Search every source (⌘F)", keywords = listOf("search", "global"), icon = Icons.Outlined.Search) {
            switchTo(4)
            pushOn(4, GlobalSearchScreen(extensionManager = extensionManager))
        },
        PaletteAction("Extensions", "Install and manage sources", keywords = listOf("extensions", "sources", "install"), icon = Icons.Outlined.Extension) {
            switchTo(4)
            pushOn(4, ExtensionsScreen(extensionManager = extensionManager))
        },
        PaletteAction("View Downloads", "Settings → queue", keywords = listOf("downloads", "queue", "settings"), icon = Icons.Outlined.CloudDownload) {
            switchTo(8)
            pushOn(8, DownloadQueueScreen())
        },
        PaletteAction("Watch Statistics", "Settings → stats", keywords = listOf("stats", "statistics", "settings"), icon = Icons.Outlined.BarChart) {
            switchTo(8)
            pushOn(8, StatsScreen())
        },
        PaletteAction("Crash & Error Logs", "Settings → diagnostics", keywords = listOf("crash", "logs", "errors", "settings"), icon = Icons.Outlined.Warning) {
            switchTo(8)
            pushOn(8, CrashLogViewerScreen())
        },
    )
}

/**
 * Desktop NavigationRail sidebar composable.
 *
 * Renders the 5 primary tabs as NavigationRailItems.
 * Uses [currentTabIndex] for selection state instead of reading
 * [tabNavigator.current] during composition (which throws
 * ClassCastException when non-Tab screens like AnimeDetailScreen
 * are on the tab's inner navigator stack).
 *
 * Tab switching (via [onSelectTab]) is performed in an event-driven
 * onClick lambda, where tabNavigator.current = tab is safe.
 */
@Composable
internal fun NavigationRailSidebar(
    currentTabIndex: Int,
    onSelectTab: (Int) -> Unit,
) {
    androidx.compose.material3.NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        orderedTabs.forEachIndexed { index, tab ->
            val selected = index == currentTabIndex
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        onSelectTab(index)
                    }
                },
                icon = {
                    tab.options.icon?.let { painter ->
                        Icon(
                            painter = painter,
                            contentDescription = tab.options.title,
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.options.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}

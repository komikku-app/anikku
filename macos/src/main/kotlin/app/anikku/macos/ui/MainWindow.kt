package app.anikku.macos.ui

import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.ui.components.AnimatedTabFade
import app.anikku.macos.ui.screens.BrowseScreen
import app.anikku.macos.ui.screens.HistoryScreen
import app.anikku.macos.ui.screens.LibraryScreen
import app.anikku.macos.ui.screens.MoreScreen
import app.anikku.macos.ui.screens.UpdatesScreen
import app.anikku.macos.ui.screens.discover.DiscoverTab
import app.anikku.macos.ui.screens.downloads.DownloadsTab
import app.anikku.macos.ui.screens.stats.StatsTab
import app.anikku.macos.ui.screens.torrent.TorrentTab
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.screens.browse.GlobalSearchScreen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator

/**
 * Main application window composable.
 *
 * Sets up the Voyager TabNavigator with 5 primary tabs:
 * Library, Updates, History, Browse, More
 *
 * Uses a Material 3 NavigationRail (side navigation) for desktop layout,
 * matching macOS conventions where horizontal space is abundant.
 *
 * Ported from the Android HomeScreen.kt and MainActivity.kt.
 */
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
fun MainWindow() {
    TabNavigator(
        tab = LibraryScreen,
        key = "MainWindowTabs",
    ) { tabNavigator ->
        // Track current tab index via state — updated ONLY in event
        // handlers (onClick / TabSwitchHandler callback). We NEVER read
        // tabNavigator.current during composition because Voyager's
        // getter internally casts navigator.items.last() as Tab, which
        // throws ClassCastException when a non-Tab screen like
        // AnimeDetailScreen is on the tab's inner navigator stack.
        var currentTabIndex by remember { mutableStateOf(0) }
        var sidebarVisible by remember { mutableStateOf(true) }
        var searchRequestId by remember { mutableLongStateOf(0L) }
        var handledSearchRequestId by remember { mutableLongStateOf(0L) }
        val extensionManager = LocalExtensionManager.current

        // Bridge the native View > Toggle Sidebar action to the Compose rail.
        DisposableEffect(tabNavigator) {
            val sidebarToggleHandler: () -> Unit = { sidebarVisible = !sidebarVisible }
            val searchHandler: () -> Unit = {
                tabNavigator.current = BrowseScreen
                currentTabIndex = 4
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
                    tabNavigator.current = tab
                    currentTabIndex = index
                    val tabNames = listOf("Library", "Updates", "History", "Stats", "Browse", "Torrents", "Downloads", "Discover", "More")
                    UIActionLogger.logNavigation("MenuShortcut", tabNames.getOrElse(index) { "?" }, "tab=$index")
                }
            }
            onDispose {
                TabSwitchHandler.onSwitchTab = null
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
                            val tabNames = listOf("Library", "Updates", "History", "Stats", "Browse", "Torrents", "Downloads", "Discover", "More")
                            UIActionLogger.logNavigation("NavigationRail", tabNames.getOrElse(index) { "?" }, "tab=$index")
                            tabNavigator.current = tab
                            currentTabIndex = index
                        }
                        },
                    )
                }

                // Tab content with saveable-state-safe fade transition.
                // Each tab gets its own nested Navigator so that non-Tab screens
                // pushed from inside the tab (e.g. ExtensionsScreen, AnimeDetailScreen,
                // SourceBrowseScreen) go to the inner navigator's stack instead of
                // polluting the TabNavigator's stack. This prevents the
                // ClassCastException in tabNavigator.current which internally does
                // navigator.items.last() as Tab.
                AnimatedTabFade(contentKey = orderedTabs[currentTabIndex].key) {
                    // Each tab gets its own nested Navigator so that non-Tab screens
                    // pushed from inside the tab (e.g. ExtensionsScreen, AnimeDetailScreen,
                    // SourceBrowseScreen) go to the inner navigator's stack instead of
                    // polluting the TabNavigator's stack. This prevents the
                    // ClassCastException in tabNavigator.current which internally does
                    // navigator.items.last() as Tab.
                    //
                    // IMPORTANT: Use CurrentScreen() here, NOT CurrentTab().
                    // CurrentScreen() renders whatever is on top of the inner Navigator's
                    // stack — initially the tab content, but also any pushed screens.
                    // CurrentTab() always renders the tab content and ignores pushes,
                    // making navigation "appear to do nothing."
                    Navigator(orderedTabs[currentTabIndex]) { navigator ->
                        LaunchedEffect(currentTabIndex, searchRequestId) {
                            if (
                                currentTabIndex == 4 &&
                                searchRequestId > handledSearchRequestId
                            ) {
                                handledSearchRequestId = searchRequestId
                                navigator.push(GlobalSearchScreen(extensionManager = extensionManager))
                            }
                        }
                        CurrentScreen()
                    }
                }
            }
        }
    }
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

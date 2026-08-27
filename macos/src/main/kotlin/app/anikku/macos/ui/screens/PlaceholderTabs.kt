package app.anikku.macos.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.screens.browse.BrowseTab
import app.anikku.macos.ui.screens.history.HistoryTab
import app.anikku.macos.ui.screens.library.LibraryTab
import app.anikku.macos.ui.screens.updates.UpdatesTab
import app.anikku.macos.ui.settings.SettingsScreen
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

/**
 * Tab declarations for the main navigation rail.
 *
 * Phase 5 — Each tab now renders a fully functional screen.
 * Placeholder content has been replaced with real implementations.
 */
object LibraryScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        LibraryTab.Content()
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = "Library",
            icon = rememberVectorPainter(Icons.Outlined.Book),
        )
}

object UpdatesScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        UpdatesTab.Content()
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = "Updates",
            icon = rememberVectorPainter(Icons.Outlined.Refresh),
        )
}

object HistoryScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        HistoryTab.Content()
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u,
            title = "History",
            icon = rememberVectorPainter(Icons.Outlined.History),
        )
}

object BrowseScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        BrowseTab.Content()
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = "Browse",
            icon = rememberVectorPainter(Icons.Outlined.Explore),
        )
}

object MoreScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        SettingsScreen()
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 8u,
            title = "More",
            icon = rememberVectorPainter(Icons.Outlined.MoreVert),
        )
}

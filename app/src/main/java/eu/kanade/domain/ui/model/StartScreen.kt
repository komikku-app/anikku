package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.MR

enum class StartScreen(val titleRes: StringResource, val tab: Tab) {
    LIBRARY(MR.strings.label_anime, LibraryTab),
    UPDATES(MR.strings.label_recent_updates, UpdatesTab),
    HISTORY(MR.strings.label_recent_manga, HistoryTab),
    BROWSE(MR.strings.browse, BrowseTab),
}

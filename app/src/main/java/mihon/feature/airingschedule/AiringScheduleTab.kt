package mihon.feature.airingschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.more.settings.screen.SettingsScheduleScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.coroutines.launch
import mihon.feature.airingschedule.components.BellNotifyState
import mihon.feature.airingschedule.components.ScheduleAnimeCard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val orderedDays = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

data object AiringScheduleTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            // Index must match the position produced by NavStyle.tabs after the moreTab is
            // removed.  Base order: Library(0) Updates(1) History(2) Schedule(3) Browse(4) More(5).
            val index: UShort = when (currentNavigationStyle()) {
                // Updates removed → Library(0) History(1) Schedule(2) Browse(3) More(4)
                eu.kanade.domain.ui.model.NavStyle.MOVE_UPDATES_TO_MORE -> 2u
                // History removed → Library(0) Updates(1) Schedule(2) Browse(3) More(4)
                eu.kanade.domain.ui.model.NavStyle.MOVE_HISTORY_TO_MORE -> 2u
                // Browse removed → Library(0) Updates(1) History(2) Schedule(3) More(4)
                eu.kanade.domain.ui.model.NavStyle.MOVE_BROWSE_TO_MORE -> 3u
                // Schedule itself is in More; use 4u (slot is unused in the nav bar)
                eu.kanade.domain.ui.model.NavStyle.MOVE_SCHEDULE_TO_MORE -> 4u
            }
            return TabOptions(
                index = index,
                title = "Schedule",
                icon = rememberVectorPainter(Icons.Outlined.DateRange),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {}

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AiringScheduleScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val todayIndex = orderedDays.indexOf(state.selectedDay).coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = todayIndex) { orderedDays.size }

        LaunchedEffect(pagerState.currentPage) {
            screenModel.selectDay(orderedDays[pagerState.currentPage])
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = stringResource(MR.strings.label_airing_schedule),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            val weekRange = buildWeekRangeLabel(state.weekStartDate, state.weekEndDate)
                            if (weekRange.isNotEmpty()) {
                                Text(
                                    text = weekRange,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { screenModel.loadSchedule() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(MR.strings.cd_refresh_schedule))
                        }
                        IconButton(onClick = { navigator.push(SettingsScheduleScreen) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.cd_schedule_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { paddingValues ->
            when {
                state.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
                state.error != null -> ScheduleErrorContent(
                    error = state.error!!,
                    onRetry = { screenModel.loadSchedule() },
                    modifier = Modifier.padding(paddingValues),
                )
                else -> Column(modifier = Modifier.padding(paddingValues)) {
                    ScheduleDayTabRow(
                        pagerState = pagerState,
                        weekStartDate = state.weekStartDate,
                        scheduleByDay = state.scheduleByDay,
                        onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        val day = orderedDays[pageIndex]
                        val entries = state.scheduleByDay[day] ?: emptyList()
                        ScheduleDayContent(
                            entries = entries,
                            titleLanguage = state.titleLanguage,
                            sourceDelays = state.sourceDelays,
                            manualDelayMinutes = state.manualDelayMinutes,
                            favoriteSourceIds = state.favoriteSourceIds,
                            pinnedSourceIds = state.pinnedSourceIds,
                            autoAddFromPinnedSources = state.autoAddFromPinnedSources,
                            libraryAnimeTitles = state.libraryAnimeTitles,
                            onSearchClick = { title ->
                                navigator.push(
                                    GlobalSearchScreen(
                                        searchQuery = title,
                                        initialSourceFilter = SourceFilter.All,
                                    ),
                                )
                            },
                            onAddToLibraryClick = { title ->
                                if (state.autoAddFromPinnedSources && state.pinnedSourceIds.isNotEmpty()) {
                                    navigator.push(
                                        GlobalSearchScreen(
                                            searchQuery = title,
                                            initialSourceFilter = SourceFilter.PinnedOnly,
                                        ),
                                    )
                                } else {
                                    navigator.push(
                                        GlobalSearchScreen(
                                            searchQuery = title,
                                            initialSourceFilter = SourceFilter.All,
                                        ),
                                    )
                                }
                            },
                            notifyOnceMediaIds = state.notifyOnceMediaIds,
                            notifySeriesMediaIds = state.notifySeriesMediaIds,
                            onToggleNotifyOnce = { entry -> screenModel.toggleNotifyOnce(entry) },
                            onToggleNotifySeries = { entry -> screenModel.toggleNotifySeries(entry) },
                        )
                    }
                }
            }
        }
    }
}

private fun buildWeekRangeLabel(start: LocalDate?, end: LocalDate?): String {
    if (start == null || end == null) return ""
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    return "${start.format(fmt)} – ${end.format(fmt)}"
}

@Composable
private fun ScheduleDayTabRow(
    pagerState: PagerState,
    weekStartDate: LocalDate?,
    scheduleByDay: Map<DayOfWeek, List<AiringScheduleEntry>>,
    onTabClick: (Int) -> Unit,
) {
    val today = LocalDate.now().dayOfWeek
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 8.dp,
    ) {
        orderedDays.forEachIndexed { index, day ->
            val isToday = day == today
            val dayDate = weekStartDate?.plusDays(orderedDays.indexOf(day).toLong())
            val count = scheduleByDay[day]?.size ?: 0
            val dayShort = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { onTabClick(index) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = dayShort,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (isToday && pagerState.currentPage != index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        dayDate?.let { date ->
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (count > 0) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ScheduleDayContent(
    entries: List<AiringScheduleEntry>,
    titleLanguage: SchedulePreferences.TitleLanguage,
    sourceDelays: Map<String, Long>,
    manualDelayMinutes: Long?,
    favoriteSourceIds: Set<String>,
    pinnedSourceIds: Set<String>,
    autoAddFromPinnedSources: Boolean,
    libraryAnimeTitles: Set<String>,
    onSearchClick: (String) -> Unit,
    onAddToLibraryClick: (String) -> Unit,
    notifyOnceMediaIds: Set<String>,
    notifySeriesMediaIds: Set<String>,
    onToggleNotifyOnce: (AiringScheduleEntry) -> Unit,
    onToggleNotifySeries: (AiringScheduleEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyScreen(stringRes = tachiyomi.i18n.MR.strings.information_no_airing_today)
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = entries, key = { it.scheduleId }) { entry ->
            val mediaKey = entry.mediaId.toString()
            val notifyState = when {
                mediaKey in notifySeriesMediaIds -> BellNotifyState.SERIES
                mediaKey in notifyOnceMediaIds -> BellNotifyState.ONCE
                else -> BellNotifyState.NONE
            }
            val isInLibrary = entry.titleUserPreferred.trim().lowercase() in libraryAnimeTitles ||
                entry.titleEnglish?.trim()?.lowercase() in libraryAnimeTitles ||
                entry.titleRomaji?.trim()?.lowercase() in libraryAnimeTitles ||
                entry.titleNative?.trim()?.lowercase() in libraryAnimeTitles
            ScheduleAnimeCard(
                entry = entry,
                titleLanguage = titleLanguage,
                sourceDelays = sourceDelays,
                manualDelayMinutes = manualDelayMinutes,
                favoriteSourceIds = favoriteSourceIds,
                pinnedSourceIds = pinnedSourceIds,
                autoAddFromPinnedSources = autoAddFromPinnedSources,
                isInLibrary = isInLibrary,
                notifyState = notifyState,
                onSearchClick = onSearchClick,
                onAddToLibraryClick = onAddToLibraryClick,
                onToggleNotifyOnce = { onToggleNotifyOnce(entry) },
                onToggleNotifySeries = { onToggleNotifySeries(entry) },
            )
        }
    }
}

@Composable
private fun ScheduleErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        ) {
            Text(
                text = stringResource(MR.strings.schedule_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(stringResource(MR.strings.action_retry))
            }
        }
    }
}

package app.anikku.macos.ui.screens.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.anilist.AniListAnime
import app.anikku.macos.platform.anilist.AiringEpisode
import app.anikku.macos.platform.anilist.LocalAniListSearchClient
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.EmptyState
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Discover tab — airing schedule + AniList discovery feed.
 *
 * All data comes from AniList's public GraphQL API (no login needed); the
 * "Because you watched" section additionally uses the user's AniList username
 * when they're logged in. Tapping any entry opens the anime detail screen,
 * which auto-links to an installed source so it can be played immediately.
 */
object DiscoverTab : AnikkuScreen(), Tab {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val anilistClient = LocalAniListSearchClient.current

        var schedule by remember { mutableStateOf<List<AiringEpisode>>(emptyList()) }
        var trending by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
        var seasonalList by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
        var recommendations by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }

        // "Because you watched" needs the user's AniList username (best-effort).
        val anilistUsername = remember {
            runCatching {
                org.koin.core.context.GlobalContext.get()
                    .getOrNull<TrackerTokenStore>()?.getUsername("anilist")
            }.getOrNull()
        }

        // Clock tick every 30s so "in 3h 24m" countdowns stay fresh.
        var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000L)
                nowMillis = System.currentTimeMillis()
            }
        }

        var retryToken by remember { mutableIntStateOf(0) }
        LaunchedEffect(anilistClient, anilistUsername, retryToken) {
            val client = anilistClient ?: return@LaunchedEffect
            isLoading = true
            loadError = null
            try {
                val now = nowMillis / 1000
                val (season, year) = seasonalFor(now)
                coroutineScope {
                    val scheduleDeferred = async { client.airingThisWeek(nowEpochSeconds = now) }
                    val trendingDeferred = async { client.trending() }
                    val seasonalDeferred = async { client.seasonal(season, year) }
                    val recsDeferred = async { client.recommendationsFor(anilistUsername) }
                    schedule = scheduleDeferred.await()
                    trending = trendingDeferred.await()
                    seasonalList = seasonalDeferred.await()
                    recommendations = recsDeferred.await()
                }
            } catch (e: Exception) {
                loadError = "Could not load Discover — check your connection and try again"
            }
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Discover") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    anilistClient == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "AniList search unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    isLoading && schedule.isEmpty() && trending.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Loading schedule…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    loadError != null && schedule.isEmpty() && trending.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = loadError ?: "Nothing to show",
                            hint = "AniList couldn't be reached right now",
                            actionLabel = "Retry",
                            onAction = { retryToken++ },
                        )
                    }

                    schedule.isEmpty() && trending.isEmpty() && seasonalList.isEmpty() && recommendations.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Outlined.Explore,
                            title = "Nothing airing right now",
                            hint = "Discover pulls what's airing this week, trending shows and seasonal picks — try again in a bit.",
                            actionLabel = "Refresh",
                            onAction = { retryToken++ },
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            if (schedule.isNotEmpty()) {
                                item(key = "schedule_header") {
                                    SectionHeader("Airing This Week", "${schedule.size} episodes")
                                }
                                val byDay = schedule.groupBy { localDayOf(it.airingAt) }.toSortedMap()
                                byDay.forEach { (day, episodes) ->
                                    item(key = "day_$day") {
                                        DayHeader(day = day, today = LocalDate.now(), epochNow = nowMillis / 1000)
                                    }
                                    items(episodes, key = { "${it.media.id}_${it.episode}_${it.airingAt}" }) { entry ->
                                        ScheduleRow(
                                            entry = entry,
                                            epochNow = nowMillis / 1000,
                                            onClick = { open(navigator, entry.media) },
                                        )
                                    }
                                }
                            }

                            if (trending.isNotEmpty()) {
                                item(key = "trending_header") {
                                    SectionHeader("Trending Now", "${trending.size}")
                                }
                                item(key = "trending_row") {
                                    AnimeCardRow(items = trending, onOpen = { open(navigator, it) })
                                }
                            }

                            if (seasonalList.isNotEmpty()) {
                                item(key = "seasonal_header") {
                                    SectionHeader("Current Season", "${seasonalList.size}")
                                }
                                item(key = "seasonal_row") {
                                    AnimeCardRow(items = seasonalList, onOpen = { open(navigator, it) })
                                }
                            }

                            if (recommendations.isNotEmpty()) {
                                item(key = "recs_header") {
                                    SectionHeader("Because You Watched…", "${recommendations.size}")
                                }
                                item(key = "recs_row") {
                                    AnimeCardRow(items = recommendations, onOpen = { open(navigator, it) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun open(navigator: cafe.adriel.voyager.navigator.Navigator, anime: AniListAnime) {
        navigator.push(
            AnimeDetailScreen(
                animeId = anime.id.toLong(),
                sourceId = null,
                animeUrl = null,
                animeTitle = anime.displayName,
            ),
        )
    }

    /** Current AniList season name + year for [epochSeconds]. */
    internal fun seasonalFor(epochSeconds: Long): Pair<String, Int> {
        val month = Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault()).monthValue
        val season = when (month) {
            in 1..3 -> "WINTER"
            in 4..6 -> "SPRING"
            in 7..9 -> "SUMMER"
            else -> "FALL"
        }
        val year = Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault()).year
        return season to year
    }

    private fun localDayOf(epochSeconds: Long): LocalDate =
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 7u,
            title = "Discover",
            icon = rememberVectorPainter(Icons.Outlined.AutoAwesome),
        )
}

// ── Sections ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (count != null) {
                Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, epochNow: Long) {
    val label = when (ChronoUnit.DAYS.between(today, day)) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ScheduleRow(entry: AiringEpisode, epochNow: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimeCoverImage(
            thumbnailUrl = entry.media.coverUrl,
            contentDescription = entry.media.displayName,
            title = entry.media.displayName,
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.media.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Ep ${entry.episode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val timeLabel = when {
            entry.airingAt <= epochNow -> "airing"
            ChronoUnit.DAYS.between(
                Instant.ofEpochSecond(epochNow).atZone(ZoneId.systemDefault()).toLocalDate(),
                Instant.ofEpochSecond(entry.airingAt).atZone(ZoneId.systemDefault()).toLocalDate(),
            ) == 0L -> countdown(entry.airingAt - epochNow)
            else -> Instant.ofEpochSecond(entry.airingAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("h:mm a"))
        }
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(16.dp),
        )
    }
}

private fun countdown(secondsUntil: Long): String {
    val minutes = (secondsUntil / 60).coerceAtLeast(0)
    return if (minutes >= 60) "in ${minutes / 60}h ${minutes % 60}m" else "in ${minutes}m"
}

@Composable
private fun AnimeCardRow(items: List<AniListAnime>, onOpen: (AniListAnime) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { anime ->
            Column(
                modifier = Modifier
                    .width(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(anime) },
            ) {
                AnimeCoverImage(
                    thumbnailUrl = anime.coverUrl,
                    contentDescription = anime.displayName,
                    title = anime.displayName,
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = anime.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

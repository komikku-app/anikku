package app.anikku.macos.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.stats.AnimeWatchSummary
import app.anikku.macos.platform.stats.WatchStats
import app.anikku.macos.platform.stats.WatchStatsCalculator
import app.anikku.macos.ui.AnikkuScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Watch statistics screen — history-driven dashboard (total episodes/hours,
 * streaks, last-14-days activity, most-watched anime).
 *
 * Pushed from Settings; the same dashboard is exposed as the [StatsTab] in the
 * sidebar. Pure aggregation over [WatchStatsCalculator].
 */
class StatsScreen : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val historyRepo = LocalHistoryRepository.current
        val historyRevision = historyRepo?.revision?.collectAsState()?.value ?: 0L
        val history = remember(historyRevision) { historyRepo?.getAll().orEmpty() }
        val stats = remember(history) { WatchStatsCalculator.computeStats(history) }

        StatsDashboard(stats = stats)
    }
}

/** Sidebar tab exposing the same watch-stats dashboard. */
object StatsTab : AnikkuScreen(), Tab {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val historyRepo = LocalHistoryRepository.current
        val historyRevision = historyRepo?.revision?.collectAsState()?.value ?: 0L
        val history = remember(historyRevision) { historyRepo?.getAll().orEmpty() }
        val stats = remember(history) { WatchStatsCalculator.computeStats(history) }

        StatsDashboard(stats = stats)
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 3u,
            title = "Stats",
            icon = rememberVectorPainter(Icons.Outlined.BarChart),
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsDashboard(stats: WatchStats) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch Stats") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (stats.totalEpisodes == 0 && stats.totalWatchSeconds == 0L) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No watch history yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Watch some episodes and your stats will show up here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = "Episodes",
                            value = stats.totalEpisodes.toString(),
                            sub = "watched",
                            icon = Icons.Outlined.PlayCircle,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "Time",
                            value = formatHours(stats.totalWatchSeconds),
                            sub = "watched",
                            icon = Icons.Outlined.Timer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = "Current streak",
                            value = "${stats.currentStreakDays}d",
                            sub = if (stats.currentStreakDays == 1) "day in a row" else "days in a row",
                            icon = Icons.Outlined.LocalFireDepartment,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "Longest streak",
                            value = "${stats.longestStreakDays}d",
                            sub = "record",
                            icon = Icons.Outlined.TrendingUp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    SectionTitle("Last ${WatchStatsCalculator.DAYS} days")
                }
                items(stats.dailyWatchSeconds, key = { it.first }) { (dayStart, seconds) ->
                    DailyBarRow(
                        dayStart = dayStart,
                        seconds = seconds,
                        maxSeconds = stats.dailyWatchSeconds.maxOfOrNull { it.second } ?: 1L,
                    )
                }

                if (stats.mostWatched.isNotEmpty()) {
                    item {
                        SectionTitle("Most watched")
                    }
                    items(stats.mostWatched, key = { it.animeId }) { summary ->
                        MostWatchedRow(summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$label · $sub", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DailyBarRow(dayStart: Long, seconds: Long, maxSeconds: Long) {
    val label = remember(dayStart) {
        SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(dayStart))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (seconds > 0) {
                val fraction = (seconds.toFloat() / maxSeconds).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (seconds >= 3_600) formatHours(seconds) else if (seconds > 0) "${seconds / 60}m" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
private fun MostWatchedRow(summary: AnimeWatchSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                summary.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${summary.episodes} ep · ${formatHours(summary.watchSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatHours(seconds: Long): String {
    val hours = seconds / 3_600.0
    return if (hours >= 100) String.format(Locale.US, "%.0fh", hours)
    else String.format(Locale.US, "%.1fh", hours)
}

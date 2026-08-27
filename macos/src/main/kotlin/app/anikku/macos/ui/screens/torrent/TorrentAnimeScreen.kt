package app.anikku.macos.ui.screens.torrent

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.anilist.AniListAnime
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.torrent.TorrentGroup
import app.anikku.macos.platform.torrent.TorrentRelease
import app.anikku.macos.platform.torrent.TorrentSeason
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.screens.player.PlayerScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

/**
 * Per-anime torrent menu: every season and episode of an anime that was
 * grouped out of the flat Nyaa search results (see [TorrentAnimeGrouper]).
 *
 * Episodes are ordered, one row per episode playing the best-quality release;
 * alternative releases of the same episode are one tap away. Batch releases
 * and unparseable entries get their own sections so nothing is hidden.
 * Clicking any row hands its magnet to the player's existing torrent engine.
 */
data class TorrentAnimeScreen(
    val displayTitle: String,
    val group: TorrentGroup,
    val anilistMatch: AniListAnime?,
    val sourceId: Long,
    val extensionManager: MacOSExtensionManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val downloadManager = LocalDownloadManager.current

        fun play(release: TorrentRelease) {
            navigator.push(
                PlayerScreen(
                    animeId = release.pageUrl.hashCode().toLong().let { if (it == 0L) 1L else it },
                    episodeId = release.magnetUrl.hashCode().toLong().let { if (it == 0L) 1L else it },
                    sourceId = sourceId,
                    episodeUrl = release.magnetUrl,
                    animeUrl = release.pageUrl,
                    animeTitle = displayTitle,
                    extensionManager = extensionManager,
                    downloadManager = downloadManager,
                ),
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "header") {
                    TorrentAnimeHeader(group = group, match = anilistMatch)
                }

                group.seasons.forEach { season ->
                    item(key = "season_${season.season}") {
                        SeasonHeader(season)
                    }
                    items(
                        items = season.episodes,
                        key = { it.episode },
                    ) { row ->
                        EpisodeRow(
                            row = row,
                            onPlay = { play(row.best) },
                        )
                    }
                }

                if (group.batches.isNotEmpty()) {
                    item(key = "batches_header") {
                        SectionHeader("Batch releases", count = "${group.batches.size}")
                    }
                    items(group.batches, key = { it.magnetUrl }) { release ->
                        BatchRow(release = release, onPlay = { play(release) })
                    }
                }

                if (group.other.isNotEmpty()) {
                    item(key = "other_header") {
                        SectionHeader("Other releases", count = "${group.other.size}")
                    }
                    items(group.other, key = { it.magnetUrl }) { release ->
                        OtherRow(release = release, onPlay = { play(release) })
                    }
                }
            }
        }
    }
}

// ── Header ───────────────────────────────────────────────────────────

@Composable
private fun TorrentAnimeHeader(group: TorrentGroup, match: AniListAnime?) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AnimeCoverImage(
                thumbnailUrl = match?.coverUrl,
                contentDescription = group.displayTitle,
                title = group.displayTitle,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = group.displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(6.dp))

                val stats = buildString {
                    append("${group.episodeCount} episodes")
                    if (group.seasonCount > 1) append(" · ${group.seasonCount} seasons")
                }
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (match != null) {
                    Spacer(Modifier.height(4.dp))
                    val meta = listOfNotNull(
                        match.format?.takeIf { it != "TV" } ?: "TV",
                        match.seasonYear?.toString(),
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (match != null && !match.synopsis.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = cleanSynopsis(match.synopsis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Torrent pipeline hint — keeps the "these play via the torrent engine" story visible.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Badge(if (match != null) "Matched on AniList" else "Not on AniList")
            Badge("${group.totalReleases} releases")
            if (group.batches.isNotEmpty()) Badge("${group.batches.size} batch")
        }
    }
}

/** Strip HTML tags and entities from AniList's synopsis for display. */
private fun cleanSynopsis(html: String): String {
    var text = html.replace(Regex("<[^>]*>"), "")
    text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&nbsp;", " ")
    return text.replace(Regex("\\s+"), " ").trim()
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ── Sections ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: String) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SeasonHeader(season: TorrentSeason) {
    SectionHeader(
        title = "Season ${season.season}",
        count = "${season.episodes.size} episodes",
    )
}

// ── Rows ─────────────────────────────────────────────────────────────

/**
 * One episode: the best-quality release plays on click; "N more" expands the
 * alternative releases of the same episode.
 */
@Composable
private fun EpisodeRow(row: app.anikku.macos.platform.torrent.TorrentEpisodeRow, onPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ep ${row.episode}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(72.dp),
            )
            ReleaseMeta(release = row.best, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (row.alternatives.isNotEmpty()) {
            var expandedState by remember { mutableStateOf(false) }
            TextButton(
                onClick = { expandedState = !expandedState },
                modifier = Modifier.padding(start = 8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(
                    if (expandedState) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    if (expandedState) "Hide other releases" else "${row.alternatives.size} more release${if (row.alternatives.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (expandedState) {
                row.alternatives.forEach { alt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPlay)
                            .padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReleaseMeta(release = alt, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun BatchRow(release: TorrentRelease, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val range = release.parsed.episodeEnd?.let { end ->
            "Ep ${release.parsed.episode ?: 1}–$end"
        } ?: "Complete"
        Text(
            text = range,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp),
        )
        ReleaseMeta(release = release, modifier = Modifier.weight(1f))
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun OtherRow(release: TorrentRelease, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = release.rawTitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Subtitle line: group · quality · size · seeders, e.g. "SubsPlease · 1080p · 1.2 GiB · ▲123". */
@Composable
private fun ReleaseMeta(release: TorrentRelease, modifier: Modifier = Modifier) {
    val parts = buildList {
        release.parsed.group?.let { add(it) }
        release.parsed.quality?.let { add(it) }
        parseSize(release.sizeSeeders)?.let { add(it) }
        parseSeeders(release.sizeSeeders)?.let { add(it) }
    }
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun parseSize(desc: String): String? =
    Regex("Size:\\s*([^▲▼⬇]+)").find(desc)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

private fun parseSeeders(desc: String): String? =
    Regex("▲(\\d+)").find(desc)?.groupValues?.get(1)?.let { "▲$it" }

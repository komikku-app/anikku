package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.presentation.anime.components.IndicatorSize
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FetchType
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.toEpisodeUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
internal fun MigrateDialog(
    oldAnime: Anime,
    newAnime: Anime,
    screenModel: MigrateDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onClickSeasons: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val flags = remember { MigrationFlags.getFlags(oldAnime, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }
    val canMigrate = remember { oldAnime.fetchType == newAnime.fetchType }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    if (canMigrate) {
                        flags.forEachIndexed { index, flag ->
                            LabeledCheckbox(
                                label = stringResource(flag.titleId),
                                checked = selectedFlags[index],
                                onCheckedChange = { selectedFlags[index] = it },
                            )
                        }
                    } else {
                        val message = if (oldAnime.fetchType == FetchType.Seasons) {
                            MR.strings.label_cant_migrate_season
                        } else {
                            MR.strings.label_cant_migrate_episode
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(IndicatorSize),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_show_anime))
                    }

                    if (newAnime.fetchType != FetchType.Episodes) {
                        TextButton(
                            onClick = {
                                onDismissRequest()
                                onClickSeasons()
                            },
                        ) {
                            Text(text = stringResource(MR.strings.label_show_seasons))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (canMigrate) {
                        TextButton(
                            onClick = {
                                scope.launchIO {
                                    screenModel.migrateAnime(
                                        oldAnime,
                                        newAnime,
                                        false,
                                        MigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                    )
                                    withUIContext { onPopScreen() }
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.copy))
                        }
                        TextButton(
                            onClick = {
                                scope.launchIO {
                                    screenModel.migrateAnime(
                                        oldAnime,
                                        newAnime,
                                        true,
                                        MigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                    )

                                    withUIContext { onPopScreen() }
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.migrate))
                        }
                    }
                }
            },
        )
    }
}

internal class MigrateDialogScreenModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private val enhancedServices by lazy {
        Injekt.get<TrackerManager>().trackers.filterIsInstance<EnhancedTracker>()
    }

    suspend fun migrateAnime(
        oldAnime: Anime,
        newAnime: Anime,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)
        val source = sourceManager.get(newAnime.source) ?: return
        val prevSource = sourceManager.get(oldAnime.source)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            val episodes = source.getEpisodeList(newAnime.toSAnime())

            migrateAnimeInternal(
                oldSource = prevSource,
                newSource = source,
                oldAnime = oldAnime,
                newAnime = newAnime,
                sourceEpisodes = episodes,
                replace = replace,
                flags = flags,
            )
        } catch (_: Throwable) {
            // Explicitly stop if an error occurred; the dialog normally gets popped at the end
            // anyway
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    private suspend fun migrateAnimeInternal(
        oldSource: Source?,
        newSource: Source,
        oldAnime: Anime,
        newAnime: Anime,
        sourceEpisodes: List<SEpisode>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateEpisodes = MigrationFlags.hasEpisodes(flags)
        val migrateCategories = MigrationFlags.hasCategories(flags)
        val migrateCustomCover = MigrationFlags.hasCustomCover(flags)
        val deleteDownloaded = MigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncEpisodesWithSource.await(sourceEpisodes, newAnime, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced
        }

        // Update chapters read, bookmark and dateFetch
        if (migrateEpisodes) {
            val prevAnimeEpisodes = getEpisodesByAnimeId.await(oldAnime.id)
            val animeEpisodes = getEpisodesByAnimeId.await(newAnime.id)

            val maxEpisodeSeen = prevAnimeEpisodes
                .filter { it.seen }
                .maxOfOrNull { it.episodeNumber }

            val updatedAnimeEpisodes = animeEpisodes.map { animeEpisode ->
                var updatedEpisode = animeEpisode
                if (updatedEpisode.isRecognizedNumber) {
                    val prevEpisode = prevAnimeEpisodes
                        .find { it.isRecognizedNumber && it.episodeNumber == updatedEpisode.episodeNumber }

                    if (prevEpisode != null) {
                        updatedEpisode = updatedEpisode.copy(
                            dateFetch = prevEpisode.dateFetch,
                            bookmark = prevEpisode.bookmark,
                        )
                    }

                    if (maxEpisodeSeen != null && updatedEpisode.episodeNumber <= maxEpisodeSeen) {
                        updatedEpisode = updatedEpisode.copy(seen = true)
                    }
                }

                updatedEpisode
            }

            val episodeUpdates = updatedAnimeEpisodes.map { it.toEpisodeUpdate() }
            updateEpisode.awaitAll(episodeUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(oldAnime.id).map { it.id }
            setAnimeCategories.await(newAnime.id, categoryIds)
        }

        // Update track
        getTracks.await(oldAnime.id).mapNotNull { track ->
            val updatedTrack = track.copy(animeId = newAnime.id)

            val service = enhancedServices
                .firstOrNull { it.isTrackFrom(updatedTrack, oldAnime, oldSource) }

            if (service != null) {
                service.migrateTrack(updatedTrack, newAnime, newSource)
            } else {
                updatedTrack
            }
        }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        // Delete downloaded
        if (deleteDownloaded) {
            if (oldSource != null) {
                downloadManager.deleteAnime(oldAnime, oldSource)
            }
        }

        if (replace) {
            updateAnime.await(AnimeUpdate(oldAnime.id, favorite = false, dateAdded = 0))
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldAnime.hasCustomCover()) {
            coverCache.setCustomCoverToCache(
                newAnime,
                coverCache.getCustomCoverFile(oldAnime.id).inputStream(),
            )
        }

        updateAnime.await(
            AnimeUpdate(
                id = newAnime.id,
                favorite = true,
                episodeFlags = oldAnime.episodeFlags,
                viewerFlags = oldAnime.viewerFlags,
                dateAdded = if (replace) oldAnime.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}

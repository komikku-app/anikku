package mihon.feature.migration.dialog

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.domain.manga.model.hasCustomBackground
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.manga.components.IndicatorSize
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.update
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    // AY -->
    onClickSeasons: () -> Unit,
    // <-- AY
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val screenModel = rememberScreenModel { MigrateDialogScreenModel() }
    LaunchedEffect(current, target) {
        screenModel.init(current, target)
    }
    val state by screenModel.state.collectAsState()
    // AY -->
    val canMigrate = remember(current.fetchType, target.fetchType) { current.fetchType == target.fetchType }
    // <-- AY

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // AY -->
                if (canMigrate) {
                    // <-- AY
                    state.applicableFlags.fastForEach { flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.getLabel()),
                            checked = flag in state.selectedFlags,
                            onCheckedChange = { screenModel.toggleSelection(flag) },
                        )
                    }
                    // AY -->
                } else {
                    val message = if (current.fetchType == FetchType.Seasons) {
                        AYMR.strings.label_cant_migrate_season
                    } else {
                        AYMR.strings.label_cant_migrate_episode
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
                    // <-- AY
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        // KMK -->
                        // Allow `migrate` mangas when using `Bulk-favorite`
                        // onDismissRequest()
                        // KMK <--
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(AYMR.strings.action_show_anime))
                }

                // AY -->
                if (target.fetchType != FetchType.Episodes) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickSeasons()
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.label_show_seasons))
                    }
                }
                // <-- AY

                Spacer(modifier = Modifier.weight(1f))

                // AY -->
                if (canMigrate) {
                    // <-- AY
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateManga(replace = false)
                                withUIContext { onComplete() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateManga(replace = true)
                                withUIContext { onComplete() }
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

private class MigrateDialogScreenModel(
    val sourcePreference: SourcePreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    // AY -->
    private val backgroundCache: BackgroundCache = Injekt.get(),
    // <-- AY
    private val downloadManager: DownloadManager = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = Injekt.get(),
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    fun init(current: Manga, target: Manga) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    // KMK -->
                    MigrationFlag.TRACK -> true
                    // KMK <--
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    // AY -->
                    MigrationFlag.CUSTOM_BACKGROUND -> current.hasCustomBackground(backgroundCache)
                    // <-- AY
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                    // KMK -->
                    MigrationFlag.EXTRA -> true
                    // KMK <--
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags().get()
        mutableState.update {
            it.copy(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
                isMigrated = false,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateManga(replace: Boolean) {
        val state = state.value
        val current = state.current ?: return
        val target = state.target ?: return
        // KMK -->
        // sourcePreference.migrationFlags().set(state.selectedFlags)
        // KMK <--
        mutableState.update { it.copy(isMigrating = true) }
        try {
            migrateManga(current, target, replace, /* KMK --> */ state.selectedFlags /* KMK <-- */)
            mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
            // KMK -->
        } catch (_: Throwable) {
            mutableState.update { it.copy(isMigrating = false, isMigrated = false) }
            // KMK <--
        }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    )
}

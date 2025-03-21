package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrationListScreen
import eu.kanade.presentation.browse.components.MigrationAnimeDialog
import eu.kanade.presentation.browse.components.MigrationExitDialog
import eu.kanade.presentation.browse.components.MigrationProgressDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.MigrationBottomSheetDialog
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.util.overEq
import exh.util.underEq
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.sy.SYMR

/**
 * Screen showing a list of pair of source-dest manga entries being migrated.
 */
class MigrationListScreen(private val config: MigrationProcedureConfig) : Screen() {

    var newSelectedItem: Pair<Long, Long>? = null

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { MigrationListScreenModel(config) }
        val items by screenModel.migratingItems.collectAsState()
        val migrationDone by screenModel.migrationDone.collectAsState()
        val finishedCount by screenModel.finishedCount.collectAsState()
        val dialog by screenModel.dialog.collectAsState()
        val migrateProgress by screenModel.migratingProgress.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        LaunchedEffect(items) {
            if (items?.isEmpty() == true) {
                val manualMigrations = screenModel.manualMigrations.value
                context.toast(
                    context.pluralStringResource(
                        SYMR.plurals.entry_migrated,
                        manualMigrations,
                        manualMigrations,
                    ),
                )
                if (!screenModel.hideNotFound) {
                    navigator.pop()
                }
            }
        }

        LaunchedEffect(newSelectedItem) {
            if (newSelectedItem != null) {
                val (oldId, newId) = newSelectedItem!!
                screenModel.useMangaForMigration(context, newId, oldId)
                newSelectedItem = null
            }
        }

        LaunchedEffect(screenModel) {
            screenModel.navigateOut.collect {
                if (items.orEmpty().size == 1 && navigator.items.any { it is AnimeScreen }) {
                    val mangaId = (items.orEmpty().firstOrNull()?.searchResult?.value as? MigratingAnime.SearchResult.Result)?.id
                    withUIContext {
                        if (mangaId != null) {
                            val newStack = navigator.items.filter {
                                it !is AnimeScreen &&
                                    it !is MigrationListScreen &&
                                    it !is PreMigrationScreen
                            } + AnimeScreen(mangaId)
                            navigator replaceAll newStack.first()
                            navigator.push(newStack.drop(1))

                            // need to set the navigator in a pop state to dispose of everything properly
                            navigator.push(this@MigrationListScreen)
                            navigator.pop()
                        } else {
                            navigator.pop()
                        }
                    }
                } else {
                    withUIContext {
                        navigator.pop()
                    }
                }
            }
        }
        MigrationListScreen(
            items = items ?: persistentListOf(),
            migrationDone = migrationDone,
            finishedCount = finishedCount,
            getManga = screenModel::getManga,
            getEpisodeInfo = screenModel::getChapterInfo,
            getSourceName = screenModel::getSourceName,
            onMigrationItemClick = {
                navigator.push(AnimeScreen(it.id, true))
            },
            openMigrationDialog = screenModel::openMigrateDialog,
            skipManga = { screenModel.removeManga(it) },
            // KMK -->
            cancelManga = { screenModel.cancelManga(it) },
            navigateUp = { navigator.pop() },
            openMigrationOptionsDialog = screenModel::openMigrationOptionsDialog,
            // KMK <--
            searchManually = { migrationItem ->
                val sources = screenModel.getMigrationSources()
                val validSources = if (sources.size == 1) {
                    sources
                } else {
                    sources.filter { it.id != migrationItem.manga.source }
                }
                val searchScreen = MigrateSearchScreen(migrationItem.manga.id, validSources.map { it.id })
                navigator push searchScreen
            },
            migrateNow = { screenModel.migrateManga(it, false) },
            copyNow = { screenModel.migrateManga(it, true) },
        )

        val onDismissRequest = { screenModel.dialog.value = null }
        when
            (
            @Suppress("NAME_SHADOWING")
            val dialog = dialog
        ) {
            is MigrationListScreenModel.Dialog.MigrateAnimeDialog -> {
                MigrationAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    copy = dialog.copy,
                    mangaSet = dialog.mangaSet,
                    mangaSkipped = dialog.mangaSkipped,
                    copyManga = screenModel::copyMangas,
                    migrateManga = screenModel::migrateMangas,
                )
            }
            MigrationListScreenModel.Dialog.MigrationExitDialog -> {
                MigrationExitDialog(
                    onDismissRequest = onDismissRequest,
                    exitMigration = navigator::pop,
                )
            }
            // KMK -->
            MigrationListScreenModel.Dialog.MigrationOptionsDialog -> {
                MigrationBottomSheetDialog(
                    onDismissRequest = onDismissRequest,
                    onStartMigration = { _ ->
                        onDismissRequest()
                        screenModel.updateOptions()
                    },
                    fullSettings = false,
                )
            }
            // KMK <--
            null -> Unit
        }

        if (!migrateProgress.isNaN() && migrateProgress overEq 0f && migrateProgress underEq 1f) {
            MigrationProgressDialog(
                progress = migrateProgress,
                exitMigration = screenModel::cancelMigrate,
            )
        }

        BackHandler(true) {
            screenModel.dialog.value = MigrationListScreenModel.Dialog.MigrationExitDialog
        }
    }
}

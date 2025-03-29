package eu.kanade.tachiyomi.ui.anime.merged

import android.content.Context
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.databinding.EditMergedSettingsDialogBinding
import eu.kanade.tachiyomi.ui.anime.MergedAnimeData
import eu.kanade.tachiyomi.util.system.toast
import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.MergedAnimeReference
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Stable
class EditMergedSettingsState(
    private val context: Context,
    private val onDeleteClick: (MergedAnimeReference) -> Unit,
    private val onDismissRequest: () -> Unit,
    private val onPositiveClick: (List<MergedAnimeReference>) -> Unit,
) : EditMergedMangaAdapter.EditMergedMangaItemListener {
    var mergedMangas: List<Pair<Anime?, MergedAnimeReference>> by mutableStateOf(emptyList())
    var mergeReference: MergedAnimeReference? by mutableStateOf(null)
    private var mergedMangaAdapter: EditMergedMangaAdapter? by mutableStateOf(null)
    private var mergedMangaHeaderAdapter: EditMergedSettingsHeaderAdapter? by mutableStateOf(null)

    fun onViewCreated(
        context: Context,
        binding: EditMergedSettingsDialogBinding,
        mergedManga: List<Anime>,
        mergedReferences: List<MergedAnimeReference>,
    ) {
        if (mergedReferences.isEmpty() || mergedReferences.size == 1) {
            context.toast(SYMR.strings.merged_references_invalid)
            onDismissRequest()
        }
        mergedMangas += mergedReferences.filter {
            it.animeSourceId != MERGED_SOURCE_ID
        }.map { reference -> mergedManga.firstOrNull { it.id == reference.animeId } to reference }
        mergeReference = mergedReferences.firstOrNull { it.animeSourceId == MERGED_SOURCE_ID }

        val isPriorityOrder =
            mergeReference?.let { it.episodeSortMode == MergedAnimeReference.EPISODE_SORT_PRIORITY } ?: false

        mergedMangaAdapter = EditMergedMangaAdapter(this, isPriorityOrder)
        mergedMangaHeaderAdapter = EditMergedSettingsHeaderAdapter(this, mergedMangaAdapter!!)

        binding.recycler.adapter = ConcatAdapter(mergedMangaHeaderAdapter, mergedMangaAdapter)
        binding.recycler.layoutManager = LinearLayoutManager(context)

        mergedMangaAdapter?.isHandleDragEnabled = isPriorityOrder

        mergedMangaAdapter?.updateDataSet(
            mergedMangas.map {
                it.toModel()
            }.sortedBy { it.mergedAnimeReference.episodePriority },
        )
    }

    override fun onItemReleased(position: Int) {
        val mergedMangaAdapter = mergedMangaAdapter ?: return
        mergedMangas = mergedMangas.map { (manga, reference) ->
            manga to reference.copy(
                episodePriority = mergedMangaAdapter.currentItems.indexOfFirst {
                    reference.id == it.mergedAnimeReference.id
                },
            )
        }
    }

    override fun onDeleteClick(position: Int) {
        val mergedMangaAdapter = mergedMangaAdapter ?: return
        val mergeMangaReference = mergedMangaAdapter.currentItems.getOrNull(position)?.mergedAnimeReference ?: return

        MaterialAlertDialogBuilder(context)
            .setTitle(SYMR.strings.delete_merged_entry.getString(context))
            .setMessage(SYMR.strings.delete_merged_entry_desc.getString(context))
            .setPositiveButton(MR.strings.action_ok.getString(context)) { _, _ ->
                onDeleteClick(mergeMangaReference)
                onDismissRequest()
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    override fun onToggleChapterUpdatesClicked(position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle(SYMR.strings.chapter_updates_merged_entry.getString(context))
            .setMessage(SYMR.strings.chapter_updates_merged_entry_desc.getString(context))
            .setPositiveButton(MR.strings.action_ok.getString(context)) { _, _ ->
                toggleChapterUpdates(position)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    private fun toggleChapterUpdates(position: Int) {
        val adapterReference = mergedMangaAdapter?.currentItems?.getOrNull(position)?.mergedAnimeReference
            ?: return
        mergedMangas = mergedMangas.map { pair ->
            val (manga, reference) = pair
            if (reference.id != adapterReference.id) return@map pair

            mergedMangaAdapter?.allBoundViewHolders?.firstOrNull {
                it is EditMergedMangaHolder && it.reference.id == reference.id
            }?.let {
                if (it is EditMergedMangaHolder) {
                    it.updateChapterUpdatesIcon(!reference.getEpisodeUpdates)
                }
            } ?: context.toast(SYMR.strings.merged_chapter_updates_error)

            manga to reference.copy(getEpisodeUpdates = !reference.getEpisodeUpdates)
        }
    }

    override fun onToggleChapterDownloadsClicked(position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle(SYMR.strings.download_merged_entry.getString(context))
            .setMessage(SYMR.strings.download_merged_entry_desc.getString(context))
            .setPositiveButton(MR.strings.action_ok.getString(context)) { _, _ ->
                toggleChapterDownloads(position)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    private fun toggleChapterDownloads(position: Int) {
        val adapterReference = mergedMangaAdapter?.currentItems?.getOrNull(position)?.mergedAnimeReference
            ?: return
        mergedMangas = mergedMangas.map { pair ->
            val (manga, reference) = pair
            if (reference.id != adapterReference.id) return@map pair

            mergedMangaAdapter?.allBoundViewHolders?.firstOrNull {
                it is EditMergedMangaHolder && it.reference.id == reference.id
            }?.let {
                if (it is EditMergedMangaHolder) {
                    it.updateDownloadChaptersIcon(!reference.downloadEpisodes)
                }
            } ?: context.toast(SYMR.strings.merged_toggle_download_chapters_error)

            manga to reference.copy(downloadEpisodes = !reference.downloadEpisodes)
        }
    }

    fun onPositiveButtonClick() {
        onPositiveClick(listOfNotNull(mergeReference) + mergedMangas.map { it.second })
        onDismissRequest()
    }
}

@Composable
fun EditMergedSettingsDialog(
    onDismissRequest: () -> Unit,
    mergedData: MergedAnimeData,
    onDeleteClick: (MergedAnimeReference) -> Unit,
    onPositiveClick: (List<MergedAnimeReference>) -> Unit,
) {
    val context = LocalContext.current
    val state = remember {
        EditMergedSettingsState(context, onDeleteClick, onDismissRequest, onPositiveClick)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = state::onPositiveButtonClick) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                AndroidView(
                    factory = { factoryContext ->
                        val binding = EditMergedSettingsDialogBinding.inflate(LayoutInflater.from(factoryContext))
                        state.onViewCreated(factoryContext, binding, mergedData.anime.values.toList(), mergedData.references)
                        binding.root
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
    )
}

private fun Pair<Anime?, MergedAnimeReference>.toModel(): EditMergedMangaItem {
    return EditMergedMangaItem(first, second)
}

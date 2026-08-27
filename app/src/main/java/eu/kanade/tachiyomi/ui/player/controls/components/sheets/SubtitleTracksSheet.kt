/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.utils.JimakuCallbacks
import eu.kanade.tachiyomi.ui.player.utils.JimakuFile
import eu.kanade.tachiyomi.ui.player.utils.JimakuState
import eu.kanade.tachiyomi.ui.player.utils.JimakuUiError
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitlesSheet(
    tracks: ImmutableList<VideoTrack>,
    selectedTracks: ImmutableList<Int>,
    onSelect: (Int) -> Unit,
    onAddSubtitle: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onOpenSubtitleDelay: () -> Unit,
    onDismissRequest: () -> Unit,
    jimakuState: JimakuState,
    jimakuCallbacks: JimakuCallbacks,
    modifier: Modifier = Modifier,
) {
    var showJimakuDialog by remember { mutableStateOf(false) }

    PlayerSheet(onDismissRequest) {
        Column(modifier) {
            LazyColumn {
                item {
                    TrackSheetTitle(
                        title = stringResource(MR.strings.pref_player_subtitle),
                        actions = {
                            TextButton(onClick = onOpenSubtitleSettings) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                                ) {
                                    Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                                    Text(text = stringResource(MR.strings.player_sheets_track_palette))
                                }
                            }
                            TextButton(onClick = onOpenSubtitleDelay) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                                ) {
                                    Icon(imageVector = Icons.Default.MoreTime, contentDescription = null)
                                    Text(text = stringResource(MR.strings.player_sheets_track_delay))
                                }
                            }
                        },
                    )
                }
                item {
                    AddTrackRow(
                        title = stringResource(MR.strings.player_sheets_add_ext_sub),
                        onClick = onAddSubtitle,
                    )
                }
                if (jimakuState.enabled) {
                    item {
                        AddTrackRow(
                            title = stringResource(MR.strings.player_jimaku_download_external),
                            onClick = { showJimakuDialog = true },
                        )
                    }
                }
                items(tracks) { track ->
                    SubtitleTrackRow(
                        title = getTrackTitle(track),
                        selected = selectedTracks.indexOf(track.id),
                        onClick = { onSelect(track.id) },
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .padding(MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Icon(Icons.Outlined.Info, null)
                        Text(stringResource(MR.strings.player_sheets_subtitles_footer_secondary_sid_no_styles))
                    }
                }
            }
        }
    }

    if (showJimakuDialog) {
        JimakuDialog(
            files = jimakuState.files,
            loading = jimakuState.loading,
            error = jimakuState.error,
            addedUrls = jimakuState.addedUrls,
            isAllFiles = jimakuState.isAllFiles,
            onFetch = jimakuCallbacks.onFetch,
            onSearch = jimakuCallbacks.onSearch,
            onAddFile = jimakuCallbacks.onAddSubtitle,
            onDismiss = { showJimakuDialog = false },
        )
    }
}

@Composable
private fun JimakuDialog(
    files: List<JimakuFile>,
    loading: Boolean,
    error: JimakuUiError?,
    addedUrls: Set<String>,
    isAllFiles: Boolean,
    onFetch: (forceRefresh: Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onAddFile: (JimakuFile) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (files.isEmpty()) onFetch(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.pref_jimaku_group)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                if (loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.padding.medium),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                if (error != null) {
                    val errorText = when (error) {
                        is JimakuUiError.AuthError -> stringResource(MR.strings.player_jimaku_error_auth)
                        is JimakuUiError.RateLimited -> stringResource(MR.strings.player_jimaku_error_rate_limit)
                        is JimakuUiError.NetworkError -> stringResource(MR.strings.player_jimaku_error_network)
                        is JimakuUiError.NotFound -> stringResource(MR.strings.player_jimaku_no_results)
                        is JimakuUiError.Unknown -> error.message
                    }
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                var searchQuery by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(MR.strings.player_jimaku_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) onSearch(searchQuery)
                        },
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isAllFiles && files.isNotEmpty()) {
                    Text(
                        text = stringResource(MR.strings.player_jimaku_showing_all_files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val sortedFiles = remember(files) { files.sortedBy { it.name.lowercase() } }
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(sortedFiles, key = { it.url }) { file ->
                        val isAdded = file.url in addedUrls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isAdded) onAddFile(file)
                                }
                                .padding(vertical = MaterialTheme.padding.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Subtitles,
                                contentDescription = null,
                            )
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isAdded) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!loading && files.isNotEmpty()) {
                TextButton(onClick = { onFetch(true) }) {
                    Text(stringResource(MR.strings.player_jimaku_refresh))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_close))
            }
        },
    )
}

@Composable
fun SubtitleTrackRow(
    title: String,
    selected: Int, // -1 unselected, otherwise return 0 and 1 for the selected indices
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected > -1,
            onCheckedChange = { _ -> onClick() },
        )
        Text(
            text = title,
            fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selected != -1) {
            Text(
                text = "#${selected + 1}",
                fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}

package eu.kanade.presentation.browse.anime

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.anime.interactor.AnimeExtensionSourceItem
import eu.kanade.presentation.browse.anime.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.manga.NsfwWarningDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: AnimeExtensionDetailsScreenModel.State,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickWhatsNew: () -> Unit,
    onClickReadme: () -> Unit,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickClearCookies: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(R.string.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        actions = buildList {
                            if (state.extension?.isUnofficial == false) {
                                add(
                                    AppBar.Action(
                                        title = stringResource(R.string.whats_new),
                                        icon = Icons.Outlined.History,
                                        onClick = onClickWhatsNew,
                                    ),
                                )
                                add(
                                    AppBar.Action(
                                        title = stringResource(R.string.action_faq_and_guides),
                                        icon = Icons.Outlined.HelpOutline,
                                        onClick = onClickReadme,
                                    ),
                                )
                            }
                            addAll(
                                listOf(
                                    AppBar.OverflowAction(
                                        title = stringResource(R.string.action_enable_all),
                                        onClick = onClickEnableAll,
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(R.string.action_disable_all),
                                        onClick = onClickDisableAll,
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(R.string.pref_clear_cookies),
                                        onClick = onClickClearCookies,
                                    ),
                                ),
                            )
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.extension == null) {
            EmptyScreen(
                textResource = R.string.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        AnimeExtensionDetails(
            contentPadding = paddingValues,
            extension = state.extension,
            sources = state.sources,
            onClickSourcePreferences = onClickSourcePreferences,
            onClickUninstall = onClickUninstall,
            onClickSource = onClickSource,
        )
    }
}

@Composable
private fun AnimeExtensionDetails(
    contentPadding: PaddingValues,
    extension: AnimeExtension.Installed,
    sources: List<AnimeExtensionSourceItem>,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    val context = LocalContext.current
    var showNsfwWarning by remember { mutableStateOf(false) }

    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        when {
            extension.isUnofficial ->
                item {
                    WarningBanner(R.string.unofficial_anime_extension_message)
                }
            extension.isObsolete ->
                item {
                    WarningBanner(R.string.obsolete_extension_message)
                }
        }

        item {
            DetailsHeader(
                extension = extension,
                onClickUninstall = onClickUninstall,
                onClickAppInfo = {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", extension.pkgName, null)
                        context.startActivity(this)
                    }
                },
                onClickAgeRating = {
                    showNsfwWarning = true
                },
            )
        }

        items(
            items = sources,
            key = { it.source.id },
        ) { source ->
            SourceSwitchPreference(
                modifier = Modifier.animateItemPlacement(),
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
            )
        }
    }
    if (showNsfwWarning) {
        NsfwWarningDialog(
            onClickConfirm = {
                showNsfwWarning = false
            },
        )
    }
}

@Composable
private fun DetailsHeader(
    extension: AnimeExtension,
    onClickAgeRating: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickAppInfo: () -> Unit,
) {
    val context = LocalContext.current

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.small,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimeExtensionIcon(
                modifier = Modifier
                    .size(112.dp),
                extension = extension,
                density = DisplayMetrics.DENSITY_XXXHIGH,
            )

            Text(
                text = extension.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            val strippedPkgName = extension.pkgName.substringAfter("eu.kanade.tachiyomi.animeextension.")

            Text(
                text = strippedPkgName,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.extraLarge,
                    vertical = MaterialTheme.padding.small,
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoText(
                modifier = Modifier.weight(1f),
                primaryText = extension.versionName,
                secondaryText = stringResource(R.string.ext_info_version),
            )

            InfoDivider()

            InfoText(
                modifier = Modifier.weight(if (extension.isNsfw) 1.5f else 1f),
                primaryText = LocaleHelper.getSourceDisplayName(extension.lang, context),
                secondaryText = stringResource(R.string.ext_info_language),
            )

            if (extension.isNsfw) {
                InfoDivider()

                InfoText(
                    modifier = Modifier.weight(1f),
                    primaryText = stringResource(R.string.ext_nsfw_short),
                    primaryTextStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    ),
                    secondaryText = stringResource(R.string.ext_info_age_rating),
                    onClick = onClickAgeRating,
                )
            }
        }

        Row(
            modifier = Modifier.padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.medium,
            ),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClickUninstall,
            ) {
                Text(stringResource(R.string.ext_uninstall))
            }

            Spacer(Modifier.width(16.dp))

            Button(
                modifier = Modifier.weight(1f),
                onClick = onClickAppInfo,
            ) {
                Text(
                    text = stringResource(R.string.ext_app_info),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun InfoText(
    modifier: Modifier,
    primaryText: String,
    primaryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    secondaryText: String,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource, indication = null) { onClick() }
    } else {
        Modifier
    }

    Column(
        modifier = modifier.then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = primaryTextStyle,
        )

        Text(
            text = secondaryText + if (onClick != null) " ⓘ" else "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun InfoDivider() {
    VerticalDivider(
        modifier = Modifier.height(20.dp),
    )
}

@Composable
private fun SourceSwitchPreference(
    modifier: Modifier = Modifier,
    source: AnimeExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    val context = LocalContext.current

    TextPreferenceWidget(
        modifier = modifier,
        title = if (source.labelAsName) {
            source.source.toString()
        } else {
            LocaleHelper.getSourceDisplayName(source.source.lang, context)
        },
        widget = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (source.source is ConfigurableAnimeSource) {
                    IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.label_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Switch(
                    checked = source.enabled,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            }
        },
        onPreferenceClick = { onClickSource(source.source.id) },
    )
}

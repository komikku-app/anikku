package app.anikku.macos.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ScreenScaffold
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.browse.KnownGoodSources
import app.anikku.macos.ui.screens.browse.SourceHealthBadge
import app.anikku.macos.ui.settings.LocalSettingsState
import app.anikku.macos.ui.settings.SettingsState
import app.anikku.macos.ui.settings.ThemeMode
import app.anikku.macos.ui.theme.AnikkuTheme
import app.anikku.macos.ui.theme.getThemeColorScheme
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Onboarding screen shown on first launch.
 *
 * Guides the user through:
 * 1. Welcome — App introduction
 * 2. Appearance — Choose color scheme + light/dark mode (applied instantly)
 * 3. Sources — See installed sources / jump to Browse
 * 4. Tips — Keyboard shortcuts and features overview
 * 5. Done — Mark onboarding as complete and proceed
 *
 * Skipped automatically if [onboardingComplete] preference is set.
 */
class OnboardingScreen(
    private val onComplete: () -> Unit,
    private val initialStep: Int = 0,
    private val onStepChanged: (Int) -> Unit = {},
    private val onBrowseSources: () -> Unit = {},
    private val onOpenSource: (sourceId: Long, sourceName: String) -> Unit = { _, _ -> },
) : AnikkuScreen() {

    @Composable
    override fun Content() {
        var currentStep by remember { mutableIntStateOf(initialStep.coerceIn(0, 4)) }
        val totalSteps = 5
        val settings = LocalSettingsState.current
        val stepTitles = listOf(
            "Welcome to Anikku",
            "Choose Your Look",
            "Add Sources",
            "Quick Tips",
            "You're All Set!",
        )

        ScreenScaffold(
            title = stepTitles[currentStep],
            actions = {
                // Skip — the app is fully usable without onboarding; never
                // force a new user through five informational screens.
                TextButton(onClick = onComplete) {
                    Text("Skip")
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Step indicator dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (i in 0 until totalSteps) {
                        Box(
                            modifier = Modifier
                                .size(if (i == currentStep) 10.dp else 8.dp)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i <= currentStep) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                        )
                        if (i < totalSteps - 1) Spacer(Modifier.width(6.dp))
                    }
                }

                Spacer(Modifier.height(48.dp))

                // Step content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (currentStep) {
                            0 -> StepWelcome()
                            1 -> StepAppearance(settings)
                            2 -> StepSources(
                                onBrowseSources = {
                                    onComplete()
                                    onBrowseSources()
                                },
                                onOpenSource = onOpenSource,
                            )
                            3 -> StepTips()
                            4 -> StepReady()
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (currentStep > 0) {
                        TextButton(onClick = {
                            currentStep--
                            onStepChanged(currentStep)
                        }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }

                    if (currentStep < totalSteps - 1) {
                        Button(
                            onClick = {
                                currentStep++
                                onStepChanged(currentStep)
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Continue")
                        }
                    } else {
                        Button(
                            onClick = {
                                onStepChanged(0)
                                onComplete()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Get Started!")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Icon(
        imageVector = Icons.Outlined.FavoriteBorder,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Your ultimate anime watching companion for macOS.\nBrowse sources, track your progress, and enjoy smooth playback with hardware-accelerated video.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepAppearance(settings: SettingsState) {
    Icon(
        imageVector = Icons.Outlined.Palette,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Pick a color scheme and whether Anikku follows your macOS appearance. You can change all of this anytime from Settings.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))

    // Color scheme swatches — tapping one applies it instantly. Derived from
    // the canonical theme list so new schemes show up here automatically.
    val presets = AnikkuTheme.allThemes.take(8)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        presets.forEach { theme ->
            val selected = settings.theme == theme
            val swatchColor = getThemeColorScheme(
                theme = theme,
                isAmoledOLED = false,
            ).primary
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { settings.theme = theme },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    // Light / Dark / System — follows macOS by default.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val modes = listOf(
            ThemeMode.SYSTEM to "Auto",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark",
        )
        modes.forEach { (mode, label) ->
            FilterChip(
                selected = settings.themeMode == mode,
                onClick = { settings.themeMode = mode },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun StepSources(
    onBrowseSources: () -> Unit,
    onOpenSource: (Long, String) -> Unit,
) {
    Icon(
        imageVector = Icons.Outlined.Extension,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Anime comes from extensions (sources) you install from the Browse tab — like streaming sites and torrent trackers.\n\nDownloads are saved automatically to Anikku's data folder, so you can watch offline anytime.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    val extensionManager = LocalExtensionManager.current
    val scope = rememberCoroutineScope()
    val toastHost = LocalToastHost.current
    val installedExtensions by (extensionManager?.installedExtensionsFlow?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) })
    val installedPkgs = remember(installedExtensions) {
        installedExtensions.mapTo(mutableSetOf()) { it.pkgName }
    }
    val firstSourceByPkg = remember(installedExtensions) {
        val map = mutableMapOf<String, Source>()
        installedExtensions.forEach { ext ->
            val src = ext.sources.filterIsInstance<Source>().firstOrNull()
            if (src != null) map.putIfAbsent(ext.pkgName, src)
        }
        map
    }
    var installingPkg by remember { mutableStateOf<String?>(null) }

    // Curated fleet-verified sources — browse them right away, or install
    // any that were removed. This is the one place a new user learns which
    // of the 60+ bundled extensions are actually worth trying first.
    Text(
        text = "Recommended — known to work",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    KnownGoodSources.RECOMMENDED.forEach { rec ->
        val installed = rec.pkgName in installedPkgs
        val source = firstSourceByPkg[rec.pkgName]
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installed && source != null) {
                    SourceHealthBadge(source = source)
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rec.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                installed && source != null -> {
                    Button(
                        onClick = { onOpenSource(source.id, source.name) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Browse", style = MaterialTheme.typography.labelMedium)
                    }
                }
                installingPkg == rec.pkgName -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    TextButton(
                        onClick = {
                            scope.launch {
                                installingPkg = rec.pkgName
                                try {
                                    val manager = extensionManager
                                        ?: throw IllegalStateException("Extension manager unavailable")
                                    val avail = manager
                                        .findAvailableExtensions(ONBOARDING_REPO_URL, force = false)
                                        .firstOrNull { it.pkgName == rec.pkgName }
                                    if (avail == null) {
                                        toastHost.show(
                                            "\"${rec.displayName}\" isn't in the extension repo yet",
                                            ToastDuration.LONG,
                                            isError = true,
                                        )
                                    } else {
                                        UIActionLogger.logExtension(rec.pkgName, "install (onboarding)", rec.pkgName)
                                        manager.installExtension(avail) { step ->
                                            if (step is InstallStep.Error) {
                                                toastHost.show(
                                                    text = step.message,
                                                    duration = ToastDuration.LONG,
                                                    isError = true,
                                                    source = rec.pkgName,
                                                    location = "Onboarding.installExtension",
                                                )
                                            }
                                        }
                                        // First installs land untrusted — trust + reload so
                                        // the source is actually usable.
                                        var untrusted: Extension.Untrusted? = null
                                        repeat(30) {
                                            untrusted = manager.untrustedExtensionsFlow.value
                                                .firstOrNull { it.pkgName == rec.pkgName }
                                            if (untrusted != null) return@repeat
                                            delay(100)
                                        }
                                        if (untrusted != null) manager.trustExtension(untrusted)
                                        toastHost.show("Installed ${rec.displayName}", ToastDuration.SHORT)
                                    }
                                } catch (e: Exception) {
                                    toastHost.show(
                                        "Install failed: ${e.message ?: "Unknown error"}",
                                        ToastDuration.LONG,
                                        isError = true,
                                    )
                                } finally {
                                    installingPkg = null
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("Install", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    val installedCount = installedExtensions.size
    Text(
        text = if (installedCount > 0) {
            "$installedCount source${if (installedCount == 1) "" else "s"} ready to use"
        } else {
            "No sources installed yet — add them from the Browse tab"
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onBrowseSources, shape = RoundedCornerShape(12.dp)) {
        Text("Browse sources")
    }
}

/** Repo used to fetch a recommended extension that isn't bundled/installed. */
private const val ONBOARDING_REPO_URL = "https://raw.githubusercontent.com/ErnestHysa/anikku-extensions-jar/main/"

@Composable
private fun StepTips() {
    Icon(
        imageVector = Icons.Outlined.Keyboard,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))

    val tips = listOf(
        "⌘1–⌘9 — Switch tabs",
        "Space — Play / Pause in the player",
        "← → — Seek ±10s · ↑ ↓ — Volume · [ ] — Speed · , . — Subtitle delay",
        "F — Full screen · M — Mute · S — Screenshot · G — GIF clip",
        "⌘, — Settings · ⌘S — Sidebar · ⌘F — Global search",
        "⌘D / ⌘E / ⌘⇧C / ⌘⇧O — Library / Share / Copy / Open on anime pages",
    )

    tips.forEach { tip ->
        Text(
            text = "• $tip",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        )
    }
}

@Composable
private fun StepReady() {
    Icon(
        imageVector = Icons.Outlined.FavoriteBorder,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Start by adding sources from the Browse tab,\nthen find your favorite anime and begin watching.\n\nYour library, history, and preferences will be\nsaved automatically.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

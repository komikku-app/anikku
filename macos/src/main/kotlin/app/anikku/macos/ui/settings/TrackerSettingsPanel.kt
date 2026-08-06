package app.anikku.macos.ui.settings
import app.anikku.macos.ui.theme.AnikkuStatusColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.AnilistConfig
import app.anikku.macos.platform.auth.LocalAniListSyncService
import app.anikku.macos.platform.auth.LocalTrackerLibrarySyncService
import app.anikku.macos.platform.auth.TrackerLibrarySyncService
import app.anikku.macos.platform.auth.TrackerManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.platform.library.LocalLibraryAutoLinkService
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.SelectItem
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.tracker.TrackerListScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OAuth client credentials for tracker services.
 *
 * Defaults are empty — users must provide their own credentials
 * by registering an app with each tracker service.
 *
 * - **MyAnimeList**: https://myanimelist.net/apiconfig
 * - **AniList**: https://anilist.co/settings/developer
 * - **Kitsu**: https://kitsu.io/settings/apps
 * - **Shikimori**: https://shikimori.one/oauth/apps
 */
data class TrackerCredentials(
    val clientId: String = "",
    val clientSecret: String = "",
)

/**
 * Tracker settings panel — login/logout for each supported tracker.
 *
 * Displays a card for each tracker service with:
 * - Tracker name and login status
 * - Login button (starts OAuth flow via system browser)
 * - Logout button (removes stored token)
 *
 * @param trackerManager The [TrackerManager] handling OAuth flows.
 * @param onLoginStart Called when login starts (tracker name passed).
 * @param onLoginResult Called when login completes (tracker name, success, message).
 */
@Composable
fun TrackerSettingsPanel(
    trackerManager: TrackerManager?,
    onTrackerChanged: () -> Unit = {},
) {
    val toastHost = LocalToastHost.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettingsState.current
    var loggingInTracker by remember { mutableStateOf<String?>(null) }

    // Collect login statuses reactively
    val statuses by remember(trackerManager) {
        trackerManager?.loginStatuses ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    HeadingItem("Tracking")

    // Re-authentication warnings — set when a token refresh failed (the token
    // is dead but the UI would otherwise keep showing a logged-in state).
    val authIssues by remember(trackerManager) {
        trackerManager?.authIssues ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    if (authIssues.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                    "Re-authentication required",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                authIssues.forEach { tracker ->
                    Text(
                        "• ${tracker.replaceFirstChar { it.uppercase() }} — your session expired. Sign in again below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    statuses.forEach { status ->
        // Load stored credentials from the token store
        val creds = remember(trackerManager, status.tracker) {
            trackerManager?.tokenStore?.getClientCredentials(status.tracker)
        }
        val clientId = creds?.first.orEmpty()
        val clientSecret = creds?.second.orEmpty()
        // Baked-in AniList client credentials take precedence when configured
        // (see gradle.properties anilist.clientId/clientSecret).
        val bakedId = if (status.tracker == "anilist") AnilistConfig.CLIENT_ID else ""
        val bakedSecret = if (status.tracker == "anilist") AnilistConfig.CLIENT_SECRET else ""
        val effectiveClientId = bakedId.ifBlank { clientId }
        val effectiveClientSecret = bakedSecret.ifBlank { clientSecret }

        TrackerCard(
            tracker = status.tracker,
            displayName = status.displayName,
            isLoggedIn = status.isLoggedIn,
            username = status.username,
            isLoggingIn = loggingInTracker == status.tracker,
            hasCredentials = effectiveClientId.isNotBlank(),
            onLogin = {
                if (trackerManager == null) {
                    toastHost.show(
                        text = "Tracker manager not available",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = status.tracker,
                        location = "TrackerSettingsPanel.onLogin",
                    )
                    return@TrackerCard
                }

                if (effectiveClientId.isBlank()) {
                    toastHost.show("${status.displayName}: Set client ID/secret in the detail screen", ToastDuration.LONG)
                    return@TrackerCard
                }

                loggingInTracker = status.tracker
                toastHost.show("Opening login screen for ${status.displayName}...", ToastDuration.LONG)

                trackerManager.login(
                    tracker = status.tracker,
                    clientId = effectiveClientId,
                    clientSecret = effectiveClientSecret,
                ) { success, message ->
                    loggingInTracker = null
                    toastHost.show(
                        text = message,
                        duration = ToastDuration.SHORT,
                        isError = !success,
                        source = status.tracker,
                        location = "TrackerSettingsPanel.loginResult",
                    )
                    if (success) onTrackerChanged()
                }
            },
            onLogout = {
                val removed = trackerManager?.logout(status.tracker) == true
                toastHost.show(
                    text = if (removed) "Logged out of ${status.displayName}" else "Could not clear ${status.displayName} credentials",
                    duration = ToastDuration.LONG,
                    isError = !removed,
                    source = status.tracker,
                    location = "TrackerSettingsPanel.logout",
                )
                if (removed) onTrackerChanged()
            },
        )
        if (status.tracker == "anilist" && bakedId.isNotBlank()) {
            Text(
                text = "Using baked-in AniList credentials — no setup needed",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }

    if (statuses.isEmpty()) {
        Text(
            text = "No trackers available",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // AniList library sync (2-way) — only meaningful when logged in.
    val anilistLoggedIn = statuses.any { it.tracker == "anilist" && it.isLoggedIn }
    if (anilistLoggedIn) {
        val syncService = LocalAniListSyncService.current
        // Captured at composition — composition locals can't be read inside
        // coroutine launches (their getters are @Composable).
        val autoLinkService = LocalLibraryAutoLinkService.current
        var syncing by remember { mutableStateOf(false) }

        Spacer(Modifier.height(8.dp))
        HeadingItem("AniList Library Sync")

        val syncOptions = arrayOf("Off", "Every 12 hours", "Daily", "Weekly")
        val syncValues = intArrayOf(0, 12, 24, 168)
        var intervalIndex by remember(settings.anilistSyncIntervalHours) {
            mutableStateOf(syncValues.indexOf(settings.anilistSyncIntervalHours).coerceAtLeast(0))
        }
        SelectItem(
            label = "Auto-sync",
            options = syncOptions,
            selectedIndex = intervalIndex,
            onSelect = { index ->
                intervalIndex = index
                settings.anilistSyncIntervalHours = syncValues[index]
            },
        )

        val lastSync = settings.anilistLastSyncAt
        Text(
            text = if (lastSync > 0) {
                "Last synced: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastSync))}"
            } else {
                "Never synced"
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                if (syncService == null) {
                    toastHost.show("AniList sync unavailable", ToastDuration.SHORT, true)
                    return@Button
                }
                syncing = true
                scope.launch {
                    val result = syncService.syncNow()
                    // Attach streaming sources to any source-less (AniList-imported)
                    // entries so they become playable without the manual link flow.
                    val autoLink = autoLinkService?.autoLink()
                    syncing = false
                    if (result != null) {
                        settings.anilistLastSyncAt = System.currentTimeMillis()
                        val message = buildString {
                            append(result.toMessage())
                            if (autoLink != null && autoLink.linked > 0) {
                                append(" · ").append(autoLink.describe())
                            }
                        }
                        toastHost.show(
                            text = message,
                            duration = if (result.errors.isNotEmpty()) ToastDuration.LONG else ToastDuration.SHORT,
                            isError = result.errors.isNotEmpty(),
                            source = "anilist",
                            location = "TrackerSettingsPanel.syncNow",
                        )
                    } else {
                        toastHost.show(
                            text = "AniList sync failed",
                            duration = ToastDuration.LONG,
                            isError = true,
                            source = "anilist",
                            location = "TrackerSettingsPanel.syncNow",
                        )
                    }
                }
            },
            enabled = !syncing && syncService != null,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(6.dp),
        ) {
            Icon(
                Icons.Outlined.SyncAlt,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (syncing) "Syncing…" else "Sync library now", style = MaterialTheme.typography.labelMedium)
        }
    }

    // MAL + Kitsu library sync (2-way) — same pull/push model as AniList,
    // driven by the shared TrackerLibrarySyncService. Shown only for trackers
    // the user has actually logged in to.
    if (statuses.any { it.tracker == "myanimelist" && it.isLoggedIn }) {
        TrackerSyncSection(
            tracker = "myanimelist",
            displayName = "MyAnimeList",
            syncService = LocalTrackerLibrarySyncService.current,
            intervalHours = settings.malSyncIntervalHours,
            lastSyncAt = settings.malLastSyncAt,
            onIntervalChange = { settings.malSyncIntervalHours = it },
            onLastSyncAt = { settings.malLastSyncAt = it },
        )
    }
    if (statuses.any { it.tracker == "kitsu" && it.isLoggedIn }) {
        TrackerSyncSection(
            tracker = "kitsu",
            displayName = "Kitsu",
            syncService = LocalTrackerLibrarySyncService.current,
            intervalHours = settings.kitsuSyncIntervalHours,
            lastSyncAt = settings.kitsuLastSyncAt,
            onIntervalChange = { settings.kitsuSyncIntervalHours = it },
            onLastSyncAt = { settings.kitsuLastSyncAt = it },
        )
    }

    // Info text about credentials
    Text(
        text = "To use tracker sync, set up your OAuth app credentials in the Manage Trackers screen.",
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )

    Spacer(Modifier.height(8.dp))

    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

    Spacer(Modifier.height(4.dp))

    // Navigate to the full TrackerListScreen
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clickable {
                navigator?.push(TrackerListScreen())
                    ?: toastHost.show(
                        text = "Navigation not available",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = "tracker",
                        location = "TrackerSettingsPanel.navigateToTrackerList",
                    )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SyncAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Manage Trackers",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Full login management screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TrackerSyncSection(
    tracker: String,
    displayName: String,
    syncService: TrackerLibrarySyncService?,
    intervalHours: Int,
    lastSyncAt: Long,
    onIntervalChange: (Int) -> Unit,
    onLastSyncAt: (Long) -> Unit,
) {
    val toastHost = LocalToastHost.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }

    Spacer(Modifier.height(8.dp))
    HeadingItem("$displayName Library Sync")

    val syncOptions = arrayOf("Off", "Every 12 hours", "Daily", "Weekly")
    val syncValues = intArrayOf(0, 12, 24, 168)
    var intervalIndex by remember(intervalHours) {
        mutableStateOf(syncValues.indexOf(intervalHours).coerceAtLeast(0))
    }
    SelectItem(
        label = "Auto-sync",
        options = syncOptions,
        selectedIndex = intervalIndex,
        onSelect = { index ->
            intervalIndex = index
            onIntervalChange(syncValues[index])
        },
    )

    Text(
        text = if (lastSyncAt > 0) {
            "Last synced: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastSyncAt))}"
        } else {
            "Never synced"
        },
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = {
            if (syncService == null) {
                toastHost.show("$displayName sync unavailable", ToastDuration.SHORT, true)
                return@Button
            }
            syncing = true
            scope.launch {
                val result = syncService.syncNow(tracker)
                syncing = false
                if (result != null) {
                    onLastSyncAt(System.currentTimeMillis())
                    toastHost.show(
                        text = result.toMessage(displayName),
                        duration = if (result.errors.isNotEmpty()) ToastDuration.LONG else ToastDuration.SHORT,
                        isError = result.errors.isNotEmpty(),
                        source = tracker,
                        location = "TrackerSettingsPanel.${tracker}Sync",
                    )
                } else {
                    toastHost.show(
                        text = "$displayName sync failed",
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = tracker,
                        location = "TrackerSettingsPanel.${tracker}Sync",
                    )
                }
            }
        },
        enabled = !syncing && syncService != null,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(6.dp),
    ) {
        Icon(
            Icons.Outlined.SyncAlt,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (syncing) "Syncing…" else "Sync library now", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TrackerCard(
    tracker: String,
    displayName: String,
    isLoggedIn: Boolean,
    username: String?,
    isLoggingIn: Boolean,
    hasCredentials: Boolean = false,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = when {
                        isLoggingIn -> "Logging in..."
                        isLoggedIn -> "Logged in${if (username != null) " as $username" else ""}"
                        !hasCredentials -> "Credentials not configured"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isLoggingIn -> MaterialTheme.colorScheme.tertiary
                        isLoggedIn -> AnikkuStatusColors.success()
                        !hasCredentials -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            if (isLoggedIn) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = AnikkuStatusColors.success(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !isLoggingIn,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logout", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onLogin,
                    enabled = !isLoggingIn,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Outlined.Login, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Login", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

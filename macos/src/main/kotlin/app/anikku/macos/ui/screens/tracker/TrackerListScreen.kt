package app.anikku.macos.ui.screens.tracker
import app.anikku.macos.ui.theme.AnikkuStatusColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.components.EmptyState
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Tracker list screen — shows all supported trackers with their login status.
 *
 * Displays cards for each tracker (MyAnimeList, AniList, Kitsu, Shikimori)
 * with connect/disconnect actions. Tapping a tracker navigates to the
 * [TrackerDetailScreen] for that specific tracker.
 */
class TrackerListScreen : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val trackerManager = LocalTrackerManager.current

        // Collect login statuses reactively
        val statuses by remember(trackerManager) {
            trackerManager?.loginStatuses ?: emptyFlow()
        }.collectAsState(initial = emptyList())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Tracker Login") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp),
            ) {
                // Header
                Text(
                    text = "Connect your anime tracking accounts to automatically sync your watch progress.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                Spacer(Modifier.height(4.dp))

                if (statuses.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Info,
                        title = "No trackers available",
                        hint = "Connect a tracker in Settings > Tracking to sync your progress",
                    )
                }

                statuses.forEach { status ->
                    TrackerListItem(
                        status = status,
                        onClick = {
                            navigator.push(
                                TrackerDetailScreen(
                                    tracker = status.tracker,
                                    displayName = status.displayName,
                                )
                            )
                        },
                        onLogin = {
                            if (trackerManager == null) {
                                toastHost.show(
                                    text = "Tracker manager not available",
                                    duration = ToastDuration.SHORT,
                                    isError = true,
                                    source = status.tracker,
                                    location = "TrackerListScreen.onLogin",
                                )
                                return@TrackerListItem
                            }
                            toastHost.show("Opening login screen for ${status.displayName}...", ToastDuration.LONG)
                            navigator.push(
                                TrackerDetailScreen(
                                    tracker = status.tracker,
                                    displayName = status.displayName,
                                )
                            )
                        },
                        onLogout = {
                            val removed = trackerManager?.logout(status.tracker) == true
                            toastHost.show(
                                text = if (removed) "Logged out of ${status.displayName}" else "Could not clear ${status.displayName} credentials",
                                duration = ToastDuration.LONG,
                                isError = !removed,
                                source = status.tracker,
                                location = "TrackerListScreen.logout",
                            )
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Info section about how tracker login works
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "How Tracker Login Works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoStep("1.", "Click a tracker to open its login page")
                        InfoStep(
                            "2.",
                            "Authorize Anikku in your browser — you'll be redirected back to the app automatically"
                        )
                        InfoStep("3.", "Your watch progress will sync automatically when you watch episodes")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Note: You need a developer API client ID/secret from each service to enable OAuth login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InfoStep(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Color mapping for tracker brand identities.
 */
internal fun trackerBrandColor(tracker: String): Color = when (tracker) {
    "myanimelist" -> Color(0xFF2E51A2) // MAL blue
    "anilist" -> Color(0xFF02A9FF)     // AniList light blue
    "kitsu" -> Color(0xFFE53E3E)       // Kitsu red
    "shikimori" -> Color(0xFF4A90D9)   // Shikimori blue
    else -> Color(0xFF6750A4)           // Default purple
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackerListItem(
    status: TrackerTokenStore.TrackerLoginStatus,
    onClick: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(trackerBrandColor(status.tracker).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = status.displayName.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = trackerBrandColor(status.tracker),
                )
            }

            Spacer(Modifier.width(14.dp))

            // Tracker info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = when {
                        status.isLoggedIn && status.username != null ->
                            "Connected as ${status.username}"
                        status.isLoggedIn -> "Connected"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.isLoggedIn) {
                        AnikkuStatusColors.success()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Action button
            if (status.isLoggedIn) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = AnikkuStatusColors.success(),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Logout", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onLogin,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Login,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Login", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

package app.anikku.macos.ui.screens.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Possible states during the OAuth login flow.
 */
private enum class LoginState {
    /** Waiting for user action — show Connect or credential input. */
    IDLE,
    /** OAuth flow in progress — browser opened, waiting for user to authorize. */
    AUTHORIZING,
    /** Login succeeded — show success animation. */
    SUCCESS,
    /** Login failed — show error with retry. */
    ERROR,
}

/**
 * Tracker detail screen — OAuth login flow for a single tracker service.
 *
 * Shows a detailed view of the tracker with:
 * - Brand icon and name
 * - Login status and username
 * - OAuth flow with animated states (IDLE → AUTHORIZING → SUCCESS/ERROR)
 * - Credential input fields when no OAuth app is configured
 * - Save & Connect flow to persist credentials before authorizing
 *
 * @param tracker The tracker name (e.g., "myanimelist", "anilist").
 * @param displayName The human-readable tracker name (e.g., "MyAnimeList").
 */
data class TrackerDetailScreen(
    val tracker: String,
    val displayName: String,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    // Class-level state to survive Voyager backstack disposal
    private val _loginState = mutableStateOf(LoginState.IDLE)
    private val _errorMessage = mutableStateOf<String?>(null)

    // Credential input state
    private val _clientId = mutableStateOf("")
    private val _clientSecret = mutableStateOf("")
    private val _showSecret = mutableStateOf(false)
    private val _saveCredentialsMode = mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val scope = rememberCoroutineScope()
        val trackerManager = LocalTrackerManager.current

        // Collect login status reactively to update on external changes
        val statuses by remember(trackerManager) {
            trackerManager?.loginStatuses ?: emptyFlow()
        }.collectAsState(initial = emptyList())

        val status = statuses.find { it.tracker == tracker }
        val isLoggedIn = status?.isLoggedIn == true
        val username = status?.username

        // Reset login state if logged in externally
        if (isLoggedIn && _loginState.value != LoginState.SUCCESS && _loginState.value != LoginState.IDLE) {
            _loginState.value = LoginState.SUCCESS
        }

        val brandColor = trackerBrandColor(tracker)

        // Tracker-specific OAuth app registration URL
        val registrationUrl = when (tracker) {
            "myanimelist" -> "https://myanimelist.net/apiconfig"
            "anilist" -> "https://anilist.co/settings/developer"
            "kitsu" -> "https://kitsu.io/settings/apps"
            "shikimori" -> "https://shikimori.one/oauth/apps"
            else -> null
        }

        // Load stored credentials or use the ones already typed
        val storedCreds = remember(tracker, trackerManager) {
            trackerManager?.tokenStore?.getClientCredentials(tracker)
        }

        val hasCredentials = _clientId.value.isNotBlank() && _clientSecret.value.isNotBlank() ||
            storedCreds != null

        // Seed input fields from stored credentials on first composition
        if (storedCreds != null && _clientId.value.isEmpty() && _clientSecret.value.isEmpty()) {
            _clientId.value = storedCreds.first
            _clientSecret.value = storedCreds.second
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(displayName) },
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
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))

                // =====================================================================
                // Brand icon — large circle with initial
                // =====================================================================
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.take(1),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = brandColor,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // =====================================================================
                // Tracker name
                // =====================================================================
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(4.dp))

                // =====================================================================
                // Login status
                // =====================================================================
                AnimatedContent(
                    targetState = if (isLoggedIn) "logged_in" else "not_logged_in",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status",
                ) { state ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (state == "logged_in") {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (username != null) "Connected as $username" else "Connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // =====================================================================
                // OAuth flow state — animated content
                // =====================================================================
                val loginState = _loginState.value

                AnimatedContent(
                    targetState = loginState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "login_flow",
                ) { state ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (state) {
                            LoginState.IDLE -> {
                                if (isLoggedIn) {
                                    // ---- Already logged in — show disconnect ----
                                    Button(
                                        onClick = {
                                            val removed = trackerManager?.logout(tracker) == true
                                            if (removed) {
                                                _loginState.value = LoginState.IDLE
                                                toastHost.show("Logged out of $displayName", ToastDuration.SHORT)
                                            } else {
                                                toastHost.show(
                                                    "Could not clear $displayName credentials",
                                                    ToastDuration.LONG,
                                                    true,
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Logout,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Disconnect $displayName",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                } else if (hasCredentials && !_saveCredentialsMode.value) {
                                    // ---- Have credentials — show Connect + Edit buttons ----
                                    // Compute a single source of truth for credentials
                                    val activeClientId = _clientId.value.ifBlank { storedCreds?.first.orEmpty() }
                                    val activeClientSecret = _clientSecret.value.ifBlank { storedCreds?.second.orEmpty() }

                                    Button(
                                        onClick = {
                                            _loginState.value = LoginState.AUTHORIZING
                                            _errorMessage.value = null
                                            if (trackerManager != null) {
                                                trackerManager.login(
                                                    tracker = tracker,
                                                    clientId = activeClientId,
                                                    clientSecret = activeClientSecret,
                                                ) { success, message ->
                                                    scope.launch {
                                                        if (success) {
                                                            _loginState.value = LoginState.SUCCESS
                                                            toastHost.show(
                                                                "Connected to $displayName",
                                                                ToastDuration.SHORT,
                                                            )
                                                        } else {
                                                            _loginState.value = LoginState.ERROR
                                                            _errorMessage.value = message
                                                            toastHost.show(
                                                                text = "$displayName: $message",
                                                                duration = ToastDuration.LONG,
                                                                isError = true,
                                                                source = tracker,
                                                                location = "TrackerDetailScreen.login.connect",
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                _loginState.value = LoginState.ERROR
                                                _errorMessage.value = "Tracker manager not available"
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = brandColor,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Launch,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Connect $displayName",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Edit credentials button
                                    TextButton(
                                        onClick = { _saveCredentialsMode.value = true },
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Edit OAuth credentials",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                } else {
                                    // ---- No credentials or edit mode — show input fields ----
                                    val isEditMode = _saveCredentialsMode.value
                                    InputCredentialsCard(
                                        displayName = displayName,
                                        clientId = _clientId.value,
                                        clientSecret = _clientSecret.value,
                                        showSecret = _showSecret.value,
                                        registrationUrl = registrationUrl,
                                        onClientIdChange = { _clientId.value = it },
                                        onClientSecretChange = { _clientSecret.value = it },
                                        onToggleSecret = { _showSecret.value = !_showSecret.value },
                                        onSave = {
                                            if (_clientId.value.isNotBlank() && _clientSecret.value.isNotBlank()) {
                                                if (trackerManager != null) {
                                                    trackerManager.tokenStore.saveClientCredentials(
                                                        tracker = tracker,
                                                        clientId = _clientId.value,
                                                        clientSecret = _clientSecret.value,
                                                    )
                                                    toastHost.show(
                                                        "Credentials saved for $displayName",
                                                        ToastDuration.SHORT,
                                                    )
                                                }
                                                _saveCredentialsMode.value = false
                                                // Auto-trigger OAuth login after saving credentials
                                                if (!isEditMode && trackerManager != null) {
                                                    _loginState.value = LoginState.AUTHORIZING
                                                    _errorMessage.value = null
                                                    trackerManager.login(
                                                        tracker = tracker,
                                                        clientId = _clientId.value,
                                                        clientSecret = _clientSecret.value,
                                                    ) { success, message ->
                                                        scope.launch {
                                                            if (success) {
                                                                _loginState.value = LoginState.SUCCESS
                                                                toastHost.show(
                                                                    "Connected to $displayName",
                                                                    ToastDuration.SHORT,
                                                                )
                                                            } else {
                                                                _loginState.value = LoginState.ERROR
                                                                _errorMessage.value = message
                                                                toastHost.show(
                                                                    text = "$displayName: $message",
                                                                    duration = ToastDuration.LONG,
                                                                    isError = true,
                                                                    source = tracker,
                                                                    location = "TrackerDetailScreen.login.saveAndConnect",
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                toastHost.show(
                                                    "Please enter both Client ID and Client Secret",
                                                    ToastDuration.SHORT,
                                                )
                                            }
                                        },
                                        onCancel = {
                                            // Restore stored credential values on cancel
                                            _saveCredentialsMode.value = false
                                            val stored = trackerManager?.tokenStore?.getClientCredentials(tracker)
                                            _clientId.value = stored?.first.orEmpty()
                                            _clientSecret.value = stored?.second.orEmpty()
                                        },
                                        onClear = {
                                            // Clear all credentials
                                            trackerManager?.tokenStore?.removeClientCredentials(tracker)
                                            _clientId.value = ""
                                            _clientSecret.value = ""
                                            _saveCredentialsMode.value = false
                                            toastHost.show("Credentials cleared for $displayName", ToastDuration.SHORT)
                                        },
                                        showCancel = isEditMode,
                                        showClear = isEditMode,
                                        saveLabel = if (isEditMode) "Save" else "Save & Connect",
                                    )
                                }
                            }

                            LoginState.AUTHORIZING -> {
                                // ---- Browser opened, waiting for user ----
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .height(4.dp),
                                            color = brandColor,
                                        )
                                        Spacer(Modifier.height(20.dp))
                                        Text(
                                            text = "Waiting for authorization...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "A browser window has opened. Please sign in to $displayName and authorize Anikku.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        OutlinedButton(
                                            onClick = { _loginState.value = LoginState.IDLE },
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            LoginState.SUCCESS -> {
                                // ---- Login successful ----
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(64.dp),
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = "Connected Successfully!",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = if (username != null) "Signed in as $username" else "Signed in to $displayName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(24.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val removed = trackerManager?.logout(tracker) == true
                                                if (removed) {
                                                    _loginState.value = LoginState.IDLE
                                                    toastHost.show("Logged out of $displayName", ToastDuration.SHORT)
                                                } else {
                                                    toastHost.show(
                                                        "Could not clear $displayName credentials",
                                                        ToastDuration.LONG,
                                                        true,
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Logout,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Disconnect",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }

                            LoginState.ERROR -> {
                                // ---- Login failed ----
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(56.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "Connection Failed",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = _errorMessage.value ?: "An unknown error occurred.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                _loginState.value = LoginState.IDLE
                                                _errorMessage.value = null
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Try Again", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // =====================================================================
                // Info card about OAuth data usage (only when idle)
                // =====================================================================
                if (loginState == LoginState.IDLE) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "What gets shared",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            BulletPoint("Your watch progress (episode numbers)")
                            BulletPoint("Anime titles you watch")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "We never share your email, password, or personal information.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Card with credential input fields for OAuth client ID and secret.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputCredentialsCard(
    displayName: String,
    clientId: String,
    clientSecret: String,
    showSecret: Boolean,
    registrationUrl: String?,
    onClientIdChange: (String) -> Unit,
    onClientSecretChange: (String) -> Unit,
    onToggleSecret: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit = {},
    onClear: () -> Unit = {},
    showCancel: Boolean = false,
    showClear: Boolean = false,
    saveLabel: String = "Save & Connect",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Connect to $displayName",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            // Instructions
            Text(
                text = "Enter your OAuth app credentials to enable tracker sync:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "1. Open the developer portal",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "2. Create a new application with redirect URI:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "http://127.0.0.1:0/callback",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "3. Copy the Client ID and Client Secret below",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(12.dp))

            // Open developer portal button
            if (registrationUrl != null) {
                OutlinedButton(
                    onClick = { app.anikku.macos.platform.web.BrowserLauncher.openSafe(registrationUrl) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Open ${displayName} Developer Portal",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Client ID field
            OutlinedTextField(
                value = clientId,
                onValueChange = onClientIdChange,
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Client Secret field (with show/hide toggle)
            OutlinedTextField(
                value = clientSecret,
                onValueChange = onClientSecretChange,
                label = { Text("Client Secret") },
                singleLine = true,
                visualTransformation = if (showSecret) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    TextButton(onClick = onToggleSecret) {
                        Text(
                            if (showSecret) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (showClear) {
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(saveLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

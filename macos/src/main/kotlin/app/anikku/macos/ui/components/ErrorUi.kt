package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.logging.TerminalErrorLogger

/**
 * Error type enum for categorizing error states across the app.
 */
enum class ErrorType(
    val icon: ImageVector,
    val title: String,
    val message: String,
    val primaryActionLabel: String? = null,
) {
    NO_INTERNET(
        icon = Icons.Outlined.CloudOff,
        title = "No Internet Connection",
        message = "Check your network connection and try again. Some features require internet access.",
        primaryActionLabel = "Retry",
    ),
    MISSING_MPV(
        icon = Icons.Outlined.Storage,
        title = "Video Player Not Found",
        message = "libmpv is required for video playback. Install it with:\n\nbrew install mpv\n\nAfter installing, restart the app.",
    ),
    DATABASE_CORRUPTION(
        icon = Icons.Outlined.ErrorOutline,
        title = "Database Issue",
        message = "The anime database may be corrupted. You can try repairing it or resetting to defaults.",
        primaryActionLabel = "Repair Database",
    ),
    PERMISSION_DENIED(
        icon = Icons.Outlined.FolderOff,
        title = "Permission Denied",
        message = "Anikku doesn't have permission to access the required files or directories. Check your system settings.",
        primaryActionLabel = "Open Settings",
    ),
    GENERIC_ERROR(
        icon = Icons.Outlined.ErrorOutline,
        title = "Something Went Wrong",
        message = "An unexpected error occurred. Please try again or restart the app.",
        primaryActionLabel = "Try Again",
    ),
}

/**
 * Full-screen error UI composable.
 *
 * Displays a centered error card with icon, title, description,
 * and optional action buttons. Suitable for error states in
 * screens that depend on network, file access, or external
 * dependencies.
 *
 * Usage:
 * ```kotlin
 * if (hasError) {
 *     ErrorUi(
 *         errorType = ErrorType.NO_INTERNET,
 *         onPrimaryAction = { retryConnect() },
 *     )
 * } else {
 *     // Normal content
 * }
 * ```
 */
@Composable
fun ErrorUi(
    errorType: ErrorType,
    modifier: Modifier = Modifier,
    onPrimaryAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    source: String? = null,
    location: String? = null,
) {
    // Log the error to the terminal as soon as the full-screen error is composed.
    LaunchedEffect(errorType) {
        TerminalErrorLogger.logUiError(
            message = "${errorType.title}: ${errorType.message}",
            source = source,
            location = location ?: "ErrorUi.${errorType.name}",
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            // Error icon
            Icon(
                imageVector = errorType.icon,
                contentDescription = errorType.title,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                text = errorType.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            // Message
            Text(
                text = errorType.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Primary action
            if (onPrimaryAction != null && errorType.primaryActionLabel != null) {
                Button(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = errorType.primaryActionLabel)
                }
            }

            // Secondary action
            if (onSecondaryAction != null && secondaryActionLabel != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = secondaryActionLabel)
                }
            }
        }
    }
}

/**
 * Compact inline error banner for embedding within screens.
 * Renders a smaller version of the error UI suitable for
 * use in List headers or as a snackbar-like element.
 *
 * Errors shown via this banner are also logged to the terminal so they can be
 * copy-pasted and included in the shutdown summary.
 */
@Composable
fun ErrorBanner(
    errorType: ErrorType,
    onDismiss: (() -> Unit)? = null,
    source: String? = null,
    location: String? = null,
    title: String? = null,
    message: String? = null,
) {
    // Log the error to the terminal as soon as the banner is composed.
    LaunchedEffect(errorType) {
        TerminalErrorLogger.logUiError(
            message = "${errorType.title}: ${errorType.message}",
            source = source,
            location = location ?: "ErrorBanner.${errorType.name}",
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        icon = {
            Icon(
                imageVector = errorType.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = title ?: errorType.title,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(text = message ?: errorType.message)
        },
        confirmButton = {
            if (onDismiss != null) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        },
    )
}

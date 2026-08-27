package app.anikku.macos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.logging.TerminalErrorLogger
import kotlinx.coroutines.delay

/**
 * Duration constants matching Android Toast conventions.
 */
enum class ToastDuration {
    /** ~2 seconds */
    SHORT,
    /** ~4 seconds */
    LONG,
}

/**
 * Internal data for a single toast message.
 */
internal data class ToastMessage(
    val id: Long,
    val text: String,
    val duration: ToastDuration,
    val isError: Boolean = false,
    val source: String? = null,
    val throwable: Throwable? = null,
    val location: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * State holder for the toast host.
 *
 * Call [show] from any composable to display a brief message.
 *
 * Usage:
 * ```kotlin
 * val toastHost = LocalToastHost.current
 * toastHost.show("Settings saved")
 * ```
 */
class ToastHostState {

    private var nextId = 0L
    internal var currentToast by mutableStateOf<ToastMessage?>(null)
        private set

    /**
     * Show a toast message with the given [text] and [duration].
     *
     * If a toast is already visible, it will be replaced by this one
     * (the new toast appears immediately with a fresh animation).
     *
     * @param text The message to display.
     * @param duration How long the toast should remain visible.
     * @param isError Whether this toast represents an error. When true, the error
     *        is also logged to the terminal and included in the shutdown summary.
     * @param source Optional source/extension that caused the error.
     * @param throwable Optional underlying exception.
     * @param location Optional code location where the error was reported.
     */
    fun show(
        text: String,
        duration: ToastDuration = ToastDuration.SHORT,
        isError: Boolean = false,
        source: String? = null,
        throwable: Throwable? = null,
        location: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        if (isError) {
            TerminalErrorLogger.logUiError(
                message = text,
                source = source,
                throwable = throwable,
                location = location,
            )
        }
        currentToast = ToastMessage(
            id = nextId++,
            text = text,
            duration = duration,
            isError = isError,
            source = source,
            throwable = throwable,
            location = location,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }

    /**
     * Convenience method for showing an error toast and logging it to the terminal.
     *
     * @param text The error message to display.
     * @param duration How long the toast should remain visible.
     * @param source Optional source/extension that caused the error.
     * @param throwable Optional underlying exception.
     * @param location Optional code location where the error was reported.
     */
    fun showError(
        text: String,
        duration: ToastDuration = ToastDuration.LONG,
        source: String? = null,
        throwable: Throwable? = null,
        location: String? = null,
    ) {
        show(
            text = text,
            duration = duration,
            isError = true,
            source = source,
            throwable = throwable,
            location = location,
        )
    }

    /**
     * Dismiss the current toast immediately.
     */
    internal fun dismiss() {
        currentToast = null
    }
}

/**
 * CompositionLocal providing the [ToastHostState] for showing toasts.
 *
 * Provide this at the app root via [CompositionLocalProvider]:
 * ```kotlin
 * val toastHostState = remember { ToastHostState() }
 * CompositionLocalProvider(LocalToastHost provides toastHostState) {
 *     // Your app content
 *     ToastHost(state = toastHostState)
 * }
 * ```
 */
val LocalToastHost = staticCompositionLocalOf { ToastHostState() }

/**
 * Renders the animated toast overlay at the bottom center of the screen.
 *
 * Place this composable as an overlay on top of your app content, typically
 * as the last child of a [Box] that fills the screen.
 *
 * @param state The [ToastHostState] to observe for new toasts.
 */
@Composable
fun ToastHost(
    state: ToastHostState = LocalToastHost.current,
) {
    val toast = state.currentToast

    // Auto-dismiss after the configured duration
    val displayMillis = when (toast?.duration) {
        ToastDuration.SHORT -> 2000L
        ToastDuration.LONG -> 4000L
        null -> 0L
    }

    LaunchedEffect(toast?.id) {
        if (toast != null && displayMillis > 0L) {
            delay(displayMillis)
            state.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = toast,
            transitionSpec = {
                val enter: EnterTransition = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn()
                val exit: ExitTransition = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                enter togetherWith exit
            },
            label = "toast",
        ) { visibleToast ->
            if (visibleToast != null) {
                MacOSToastContent(
                    text = visibleToast.text,
                    actionLabel = visibleToast.actionLabel,
                    onAction = visibleToast.onAction,
                )
            }
        }
    }
}

/**
 * The visual toast card composable.
 *
 * Renders a Material 3 surface with rounded corners, appropriate padding,
 * and the standard "onSurface" color scheme.
 */
@Composable
private fun MacOSToastContent(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.85f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Medium,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

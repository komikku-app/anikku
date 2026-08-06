package app.anikku.macos.ui.components
import app.anikku.macos.ui.theme.AnikkuStatusColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A small animated checkmark overlay that fades-and-bounces in when
 * [isOfflinePlayback] first becomes `true`, then auto-dismisses after
 * [durationMs] milliseconds.
 *
 * Use this in the player screen to give a satisfying visual confirmation
 * when playback switches to offline (downloaded) mode. Because the
 * [OfflineBadge] only appears inside the controls overlay (which may be
 * hidden during playback), this provides immediate feedback regardless
 * of the controls state.
 *
 * ## Behaviour
 * - When [isOfflinePlayback] transitions `false → true`, the checkmark
 *   circle and label animate in with a spring scale + fade.
 * - After [durationMs] the view fades out automatically.
 * - Subsequent transitions are ignored while the animation is still
 *   visible (avoids re-trigger flicker).
 * - When [isOfflinePlayback] becomes `false` again, the view is hidden
 *   immediately so it doesn't linger when leaving the player.
 *
 * @param isOfflinePlayback Whether the current playback is offline.
 * @param durationMs How long to show the checkmark before fading out.
 * @param modifier Optional Modifier (typically used for alignment).
 */
@Composable
fun OfflineCheckmarkAnimation(
    isOfflinePlayback: Boolean,
    durationMs: Long = 2000L,
    modifier: Modifier = Modifier,
) {
    // Track whether we've already triggered the animation for the current
    // offline session to avoid re-firing on recompositions.
    var hasShownForCurrentSession by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    // When entering offline mode: show checkmark animation, then auto-dismiss.
    // When leaving offline mode: reset immediately so it can re-trigger later.
    LaunchedEffect(isOfflinePlayback) {
        if (isOfflinePlayback && !hasShownForCurrentSession) {
            hasShownForCurrentSession = true
            isVisible = true
            delay(durationMs)
            isVisible = false
        } else if (!isOfflinePlayback) {
            isVisible = false
            hasShownForCurrentSession = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
            scaleIn(
                initialScale = 0.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 400)),
        modifier = modifier,
    ) {
        // Green checkmark circle with "Offline" label
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AnikkuStatusColors.success().copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // White checkmark inside a slightly translucent circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

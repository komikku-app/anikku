package app.anikku.macos.ui.screens.onboarding

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
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anikku.macos.ui.AnikkuScreen

/**
 * Onboarding screen shown on first launch (Phase 5.12).
 *
 * Guides the user through:
 * 1. Welcome — App introduction
 * 2. Appearance — Choose theme preference
 * 3. Storage — Choose download directory (placeholder)
 * 4. Tips — Keyboard shortcuts and features overview
 * 5. Done — Mark onboarding as complete and proceed
 *
 * Skipped automatically if [onboardingComplete] preference is set.
 */
class OnboardingScreen(
    private val onComplete: () -> Unit,
    private val initialStep: Int = 0,
    private val onStepChanged: (Int) -> Unit = {},
) : AnikkuScreen() {

    @Composable
    override fun Content() {
        var currentStep by remember { mutableIntStateOf(initialStep.coerceIn(0, 4)) }
        val totalSteps = 5

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            1 -> StepAppearance()
                            2 -> StepStorage()
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
        text = "Welcome to Anikku",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Your ultimate anime watching companion for macOS.\nBrowse sources, track your progress, and enjoy smooth playback with hardware-accelerated video.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepAppearance() {
    Icon(
        imageVector = Icons.Outlined.Palette,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Choose Your Look",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Anikku follows your macOS appearance setting by default.\nYou can switch between Light, Dark, or System theme anytime from Settings.\n\nThere are 18+ handcrafted color schemes to choose from — find your favorite!",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepStorage() {
    Icon(
        imageVector = Icons.Outlined.Folder,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Download Location",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Episodes will be saved to:\n~/Library/Application Support/Anikku/downloads/\n\nYou can change this location anytime in Settings.\nFor now, the default location works great.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepTips() {
    Icon(
        imageVector = Icons.Outlined.Keyboard,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Quick Tips",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))

    val tips = listOf(
        "⌘1–⌘5 — Switch tabs (Library / Updates / History / Browse / More)",
        "Space — Play / Pause (when watching an episode)",
        "← → — Seek backward / forward (10 seconds)",
        "⌘, — Open Settings",
        "⌘F — Toggle Full Screen in player",
        "Right-click an anime — Quick actions menu",
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
        text = "You're All Set!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Start by adding sources from the Browse tab,\nthen find your favorite anime and begin watching.\n\nYour library, history, and preferences will be\nsaved automatically.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

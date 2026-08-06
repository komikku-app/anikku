package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.ui.components.ScreenScaffold
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.io.File

/**
 * Voyager Screen wrapper for [BackupRestorePanel].
 *
 * Provides proper Voyager navigation support with a unique screen key
 * so the navigator can differentiate between instances.
 */
data class BackupRestoreScreen(
    val backupManager: MacOSBackupManager,
    val backupsDir: File,
) : Screen {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        ScreenScaffold(
            title = "Backup & Restore",
            onBack = { navigator.pop() },
        ) { padding ->
            BackupRestorePanel(
                backupManager = backupManager,
                backupsDir = backupsDir,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

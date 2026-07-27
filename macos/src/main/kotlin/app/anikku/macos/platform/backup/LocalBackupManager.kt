package app.anikku.macos.platform.backup

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for MacOSBackupManager.
 *
 * Provided in AnikkuApp.kt via CompositionLocalProvider so any screen
 * can access the backup manager without threading it through constructors.
 *
 * Usage:
 * ```kotlin
 * val backupManager = LocalBackupManager.current
 * backupManager?.exportToDir(backupsDir)
 * ```
 */
val LocalBackupManager = compositionLocalOf<MacOSBackupManager?> { null }

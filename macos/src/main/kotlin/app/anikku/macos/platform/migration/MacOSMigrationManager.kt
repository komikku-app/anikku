package app.anikku.macos.platform.migration

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.storage.MacOSStorageProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val migrationLogger = KotlinLogging.logger {}

data class MacOSMigration(
    val version: Int,
    val name: String,
    val always: Boolean = false,
    val action: suspend MacOSMigrationContext.() -> Boolean,
)

data class MacOSMigrationContext(
    val preferences: MacOSPreferenceStore,
    val storageProvider: MacOSStorageProvider,
    val secretStore: MacOSSecretStore,
    val dryRun: Boolean,
)

data class MacOSMigrationResult(
    val success: Boolean,
    val previousVersion: Int,
    val currentVersion: Int,
    val executed: List<String>,
    val failedMigration: String? = null,
)

/** Version-gated, retry-safe startup migrations mirroring Android's Migrator. */
class MacOSMigrationManager(
    private val preferences: MacOSPreferenceStore,
    private val storageProvider: MacOSStorageProvider,
    private val secretStore: MacOSSecretStore,
    private val currentVersion: Int = CURRENT_VERSION,
    private val migrations: List<MacOSMigration> = defaultMigrations(),
) {
    suspend fun migrate(dryRun: Boolean = false): MacOSMigrationResult {
        val versionPreference = preferences.getInt(KEY_LAST_VERSION, 0)
        val previousVersion = versionPreference.get().coerceAtLeast(0)
        if (previousVersion > currentVersion) {
            return MacOSMigrationResult(
                success = false,
                previousVersion = previousVersion,
                currentVersion = previousVersion,
                executed = emptyList(),
                failedMigration = "Downgrade from schema $previousVersion to $currentVersion is unsupported",
            )
        }

        val applicable = migrations
            .filter { migration ->
                migration.always || (previousVersion != 0 && migration.version in (previousVersion + 1)..currentVersion)
            }
            .sortedWith(compareBy<MacOSMigration> { if (it.always) Int.MIN_VALUE else it.version }.thenBy { it.name })
        val context = MacOSMigrationContext(preferences, storageProvider, secretStore, dryRun)
        val executed = mutableListOf<String>()
        for (migration in applicable) {
            if (dryRun) {
                executed += migration.name
                continue
            }
            val succeeded = try {
                migration.action(context)
            } catch (error: Exception) {
                migrationLogger.warn(error) { "Migration ${migration.name} failed" }
                false
            }
            if (!succeeded) {
                return MacOSMigrationResult(
                    success = false,
                    previousVersion = previousVersion,
                    currentVersion = previousVersion,
                    executed = executed,
                    failedMigration = migration.name,
                )
            }
            executed += migration.name
        }

        if (!dryRun && previousVersion < currentVersion) versionPreference.set(currentVersion)
        return MacOSMigrationResult(
            success = true,
            previousVersion = previousVersion,
            currentVersion = if (dryRun) previousVersion else maxOf(previousVersion, currentVersion),
            executed = executed,
        )
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val KEY_LAST_VERSION = "macos_last_version_code"

        fun defaultMigrations(): List<MacOSMigration> = listOf(
            MacOSMigration(
                version = CURRENT_VERSION,
                name = "move legacy tracker secrets to Keychain",
                always = true,
            ) {
                val legacyKeys = preferences.snapshotJson().keys.filter { key ->
                    key.startsWith("tracker_token_") ||
                        key.startsWith("creds_id_") ||
                        key.startsWith("creds_secret_")
                }
                if (legacyKeys.isEmpty()) return@MacOSMigration true
                if (!secretStore.isAvailable) return@MacOSMigration false
                for (key in legacyKeys) {
                    val value = preferences.snapshotJson()[key]?.jsonPrimitive?.content.orEmpty()
                    if (value.isNotBlank() && !secretStore.store(key, value)) return@MacOSMigration false
                    preferences.getString(key, "").delete()
                }
                true
            },
            MacOSMigration(
                version = CURRENT_VERSION,
                name = "remove obsolete certificate override",
                always = true,
            ) {
                val candidates = listOf(
                    File(storageProvider.dataDirectory, "cacert.pem"),
                    File(storageProvider.directory(), "cacert.pem"),
                )
                candidates.all { file -> !file.exists() || file.delete() }
            },
        )
    }
}

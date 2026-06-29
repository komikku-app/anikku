package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class LegacyBackup(
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    // Bump by 100 to specify this is a 0.x value
    // @ProtoNumber(102) var backupBrokenAnimeSources, legacy source model with non-compliant proto number,
    @ProtoNumber(103) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(107) var backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(109) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
) {
    fun toBackup(): Backup {
        return Backup(
            backupPreferences = backupPreferences,
            backupSourcePreferences = backupSourcePreferences,

            isLegacy = false, // Only used for detection
            backupAnime = backupAnime,
            backupAnimeCategories = backupAnimeCategories,
            backupSources = backupSources,
            backupExtensions = backupExtensions,
            backupAnimeExtensionRepo = backupAnimeExtensionRepo,
            backupCustomButton = backupCustomButton,
        )
    }
}

@Serializable
data class Backup(
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),

    // Aniyomi specific values
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(504) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(505) var backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(506) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
)

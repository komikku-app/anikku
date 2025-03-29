package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    @ProtoNumber(3) val backupAnime: List<BackupAnime>,
    @ProtoNumber(4) var backupCategories: List<BackupCategory> = emptyList(),
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(103) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(107) var backupExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(109) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
    // SY specific values
    @ProtoNumber(600) var backupSavedSearches: List<BackupSavedSearch> = emptyList(),
    // KMK -->
    // Global Popular/Latest feeds
    @ProtoNumber(610) var backupFeeds: List<BackupFeed> = emptyList(),
    // KMK <--
)

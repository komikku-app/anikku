package eu.kanade.tachiyomi.data.sync.models

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.sy.SYMR
import tachiyomi.i18n.tail.TLMR

data class SyncTriggerOptions(
    val syncOnEpisodeSeen: Boolean = false,
    val syncOnEpisodeOpen: Boolean = false,
    val syncOnAppStart: Boolean = false,
    val syncOnAppResume: Boolean = false,
) {
    fun asBooleanArray() = booleanArrayOf(
        syncOnEpisodeSeen,
        syncOnEpisodeOpen,
        syncOnAppStart,
        syncOnAppResume,
    )

    fun anyEnabled() = syncOnEpisodeSeen ||
        syncOnEpisodeOpen ||
        syncOnAppStart ||
        syncOnAppResume

    companion object {
        val mainOptions = persistentListOf(
            Entry(
                label = TLMR.strings.sync_on_episode_seen,
                getter = SyncTriggerOptions::syncOnEpisodeSeen,
                setter = { options, enabled -> options.copy(syncOnEpisodeSeen = enabled) },
            ),
            Entry(
                label = TLMR.strings.sync_on_episode_open,
                getter = SyncTriggerOptions::syncOnEpisodeOpen,
                setter = { options, enabled -> options.copy(syncOnEpisodeOpen = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_app_start,
                getter = SyncTriggerOptions::syncOnAppStart,
                setter = { options, enabled -> options.copy(syncOnAppStart = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_app_resume,
                getter = SyncTriggerOptions::syncOnAppResume,
                setter = { options, enabled -> options.copy(syncOnAppResume = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = SyncTriggerOptions(
            syncOnEpisodeSeen = array[0],
            syncOnEpisodeOpen = array[1],
            syncOnAppStart = array[2],
            syncOnAppResume = array[3],
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (SyncTriggerOptions) -> Boolean,
        val setter: (SyncTriggerOptions, Boolean) -> SyncTriggerOptions,
        val enabled: (SyncTriggerOptions) -> Boolean = { true },
    )
}

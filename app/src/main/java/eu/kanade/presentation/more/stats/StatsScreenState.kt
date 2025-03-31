package eu.kanade.presentation.more.stats

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.more.stats.data.StatsData

sealed interface StatsScreenState {
    @Immutable
    data object Loading : StatsScreenState

    @Immutable
    data class SuccessManga(
        val overview: StatsData.AnimeOverview,
        val titles: StatsData.AnimeTitles,
        val episodes: StatsData.Episodes,
        val trackers: StatsData.Trackers,
    ) : StatsScreenState
}

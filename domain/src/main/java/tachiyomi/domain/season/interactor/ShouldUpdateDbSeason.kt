// AY -->
package tachiyomi.domain.season.interactor

import tachiyomi.domain.anime.model.Anime

class ShouldUpdateDbSeason {
    fun await(dbSeason: Anime, sourceSeason: Anime): Boolean {
        return dbSeason.title != sourceSeason.title ||
            dbSeason.seasonNumber != sourceSeason.seasonNumber ||
            dbSeason.seasonSourceOrder != sourceSeason.seasonSourceOrder
    }
}
// <-- AY

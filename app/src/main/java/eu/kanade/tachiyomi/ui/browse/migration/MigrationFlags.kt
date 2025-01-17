package eu.kanade.tachiyomi.ui.browse.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

data class MigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): MigrationFlag {
            return MigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object MigrationFlags {

    const val CHAPTERS = 0b000001
    const val CATEGORIES = 0b000010
    const val TRACK = 0b000100
    const val CUSTOM_COVER = 0b001000
    const val EXTRA = 0b010000
    const val DELETE_CHAPTERS = 0b100000

    private val coverCache: CoverCache by injectLazy()
    private val downloadCache: DownloadCache by injectLazy()

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasTracks(value: Int): Boolean {
        return value and TRACK != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasExtra(value: Int): Boolean {
        return value and EXTRA != 0
    }

    fun hasDeleteChapters(value: Int): Boolean {
        return value and DELETE_CHAPTERS != 0
    }

    /** Returns information about applicable flags with default selections. */
    fun getFlags(anime: Anime?, defaultSelectedBitMap: Int): List<MigrationFlag> {
        val flags = mutableListOf<MigrationFlag>()
        flags += MigrationFlag.create(CHAPTERS, defaultSelectedBitMap, MR.strings.episodes)
        flags += MigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)

        if (anime != null) {
            if (anime.hasCustomCover(coverCache)) {
                flags += MigrationFlag.create(
                    CUSTOM_COVER,
                    defaultSelectedBitMap,
                    MR.strings.custom_cover,
                )
            }
            if (downloadCache.getDownloadCount(anime) > 0) {
                flags += MigrationFlag.create(
                    DELETE_CHAPTERS,
                    defaultSelectedBitMap,
                    MR.strings.delete_downloaded,
                )
            }
        }
        return flags
    }

    /** Returns a bit map of selected flags. */
    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<MigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}

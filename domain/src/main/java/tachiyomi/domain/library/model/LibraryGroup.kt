package tachiyomi.domain.library.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.sy.SYMR

object LibraryGroup {

    const val BY_DEFAULT = 0
    const val BY_SOURCE = 1
    const val BY_STATUS = 2
    const val BY_TRACK_STATUS = 3
    const val BY_TAG = 4
    const val UNGROUPED = 5

    fun groupTypeStringRes(type: Int, hasCategories: Boolean = true): StringResource {
        return when (type) {
            BY_STATUS -> MR.strings.status
            BY_SOURCE -> MR.strings.label_sources
            BY_TRACK_STATUS -> SYMR.strings.tracking_status
            BY_TAG -> AMR.strings.tag
            UNGROUPED -> SYMR.strings.ungrouped
            else -> if (hasCategories) MR.strings.categories else SYMR.strings.ungrouped
        }
    }
}

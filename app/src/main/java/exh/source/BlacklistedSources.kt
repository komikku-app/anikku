@file:Suppress("PropertyName")

package exh.source

object BlacklistedSources {
    val EHENTAI_EXT_SOURCES = longArrayOf()

    val BLACKLISTED_EXT_SOURCES = EHENTAI_EXT_SOURCES

    val BLACKLISTED_EXTENSIONS = arrayOf(
        "eu.kanade.tachiyomi.extension.all.ehentai",
    )

    var HIDDEN_SOURCES = setOf(
        MERGED_SOURCE_ID,
    )
}

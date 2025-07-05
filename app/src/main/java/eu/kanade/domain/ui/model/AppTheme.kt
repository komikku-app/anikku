package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.kmk.KMR

enum class AppTheme(val titleRes: StringResource?) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),

    // Kuukiyomi themes
    CUSTOM(KMR.strings.theme_custom),

    // Aniyomi themes
    COTTONCANDY(AYMR.strings.theme_cottoncandy),
    MOCHA(AYMR.strings.theme_mocha),
    CLOUDFLARE(AYMR.strings.theme_cloudflare),
    DOOM(AYMR.strings.theme_doom),
    MATRIX(AYMR.strings.theme_matrix),
    SAPPHIRE(AYMR.strings.theme_sapphire),

    CATPPUCCIN(MR.strings.theme_catppuccin),
    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),
    NORD(MR.strings.theme_nord),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),
    MONOCHROME(AMR.strings.theme_monochrome),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),

    // SY -->
    PURE_RED(null),
    // SY <--
}

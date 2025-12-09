package exh.util

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tachiyomi.presentation.core.icons.FlagEmoji.Companion.getEmojiLangFlag
import java.util.Locale

@Preview
@Composable
private fun LanguageFlagPreview() {
    val locales = listOf(
        Locale.forLanguageTag("en"),
        Locale.forLanguageTag("ja"),
        Locale.forLanguageTag("zh"),
        Locale.forLanguageTag("es"),
        Locale.forLanguageTag("ko"),
        Locale.forLanguageTag("ru"),
        Locale.forLanguageTag("fr"),
        Locale.forLanguageTag("pt"),
        Locale.forLanguageTag("th"),
        Locale.forLanguageTag("de"),
        Locale.forLanguageTag("it"),
        Locale.forLanguageTag("vi"),
        Locale.forLanguageTag("pl"),
        Locale.forLanguageTag("hu"),
        Locale.forLanguageTag("nl"),
    )
    Column {
        FlowRow {
            locales.forEach {
                Text(text = getEmojiLangFlag(it.toLanguageTag()))
            }
        }
    }
}

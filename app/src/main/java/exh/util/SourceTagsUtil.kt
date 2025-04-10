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
        Locale("en"),
        Locale("ja"),
        Locale("zh"),
        Locale("es"),
        Locale("ko"),
        Locale("ru"),
        Locale("fr"),
        Locale("pt"),
        Locale("th"),
        Locale("de"),
        Locale("it"),
        Locale("vi"),
        Locale("pl"),
        Locale("hu"),
        Locale("nl"),
    )
    Column {
        FlowRow {
            locales.forEach {
                Text(text = getEmojiLangFlag(it.toLanguageTag()))
            }
        }
    }
}

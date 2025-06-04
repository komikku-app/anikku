package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppIcon
import eu.kanade.tachiyomi.R
import uy.kohesive.injekt.Injekt

@Composable
fun LogoHeader() {
    // KMK -->
    val uiPreferences by Injekt.injectLazy<UiPreferences>()
    val appIcon = uiPreferences.appIcon().get()
    val painter = when (appIcon) {
        AppIcon.DEFAULT -> painterResource(R.drawable.ic_launcher_monochrome)
        AppIcon.ANIKUN1 -> painterResource(R.drawable.ic_launcher_monochrome_anikun_1)
        AppIcon.ANIKUN2 -> painterResource(R.drawable.ic_launcher_monochrome_anikun_2)
        AppIcon.ANIKUN3 -> painterResource(R.drawable.ic_launcher_monochrome_anikun_3)
        AppIcon.ONIGIRI1 -> painterResource(R.drawable.ic_launcher_monochrome_onigiri)
        AppIcon.ONIGIRI2 -> painterResource(R.drawable.ic_launcher_monochrome_onigiri)
    }
    // KMK <--
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(vertical = 56.dp)
                .size(64.dp),
        )

        HorizontalDivider()
    }
}

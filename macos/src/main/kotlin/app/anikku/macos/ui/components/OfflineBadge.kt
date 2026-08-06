package app.anikku.macos.ui.components
import app.anikku.macos.ui.theme.AnikkuStatusColors

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small green chip/badge indicating the episode is available offline.
 *
 * Displays a [DownloadDone] icon and \"Offline\" label inside a rounded
 * green [Surface], styled consistently across PlayerScreen and download lists.
 */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = AnikkuStatusColors.success(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DownloadDone,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

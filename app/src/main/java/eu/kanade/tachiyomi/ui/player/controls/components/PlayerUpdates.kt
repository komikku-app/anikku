/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.material.padding

@Composable
fun PlayerUpdate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(0.4f))
            .padding(vertical = MaterialTheme.padding.small, horizontal = MaterialTheme.padding.medium)
            .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun TextPlayerUpdate(
    text: String,
    modifier: Modifier = Modifier,
) {
    PlayerUpdate(modifier) {
        Text(text)
    }
}

@Composable
fun DoubleSpeedIndicator(
    modifier: Modifier = Modifier,
) {
    var targetAlpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        delay(1500)
        targetAlpha = 0.35f
        delay(1500)
        targetAlpha = 0f
    }

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500),
        label = "doubleSpeedAlpha",
    )

    if (alpha > 0f) {
        PlayerUpdate(modifier.alpha(alpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "2x",
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

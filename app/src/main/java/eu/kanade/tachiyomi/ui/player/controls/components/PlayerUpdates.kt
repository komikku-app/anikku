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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
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
    speed: Float,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    var showFullBar by remember { mutableStateOf(true) }

    LaunchedEffect(speed) {
        showFullBar = true
        delay(2000)
        showFullBar = false
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(Color.Black.copy(0.4f))
            .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (showFullBar) {
            val trackWidth = 240.dp
            val speedStops = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                // Top labels — positioned using same progress formula as dots
                Box(
                    modifier = Modifier.width(trackWidth).padding(bottom = 6.dp),
                ) {
                    speedStops.forEach { stopVal ->
                        val labelProgress = ((stopVal - 0.5f) / 3.5f).coerceIn(0f, 1f)
                        val label = String.format("%.1fx", stopVal)
                        Text(
                            text = label,
                            color = if (speed == stopVal) Color(0xFF4A90E2) else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = trackWidth * labelProgress - 12.dp),
                        )
                    }
                }

                // Track with dots
                Box(
                    modifier = Modifier
                        .width(trackWidth)
                        .height(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Track background
                    Box(
                        modifier = Modifier
                            .width(trackWidth)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    )
                    // Progress fill
                    val progress = ((speed - 0.5f) / 3.5f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(trackWidth * progress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF4A90E2)),
                    )
                    // Dot markers at each stop
                    speedStops.forEach { stopVal ->
                        val dotProgress = ((stopVal - 0.5f) / 3.5f).coerceIn(0f, 1f)
                        val dotOffset = (trackWidth * dotProgress - 3.dp).coerceAtLeast(0.dp)
                        Box(
                            modifier = Modifier
                                .offset(x = dotOffset)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (speed >= stopVal) Color(0xFF4A90E2) else Color.White.copy(alpha = 0.7f)),
                        )
                    }
                    // Thumb
                    val thumbOffset = (trackWidth * ((speed - 0.5f) / 3.5f).coerceIn(0f, 1f) - 5.dp).coerceAtLeast(0.dp)
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A90E2)),
                    )
                }

                // Bottom status text
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val speedText = String.format("%.1fx", speed)
                    Text(
                        text = "$speedText Speed Playing",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.size(48.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.height(2.dp))
                val speedText = String.format("%.2fx", speed)
                Text(
                    text = speedText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

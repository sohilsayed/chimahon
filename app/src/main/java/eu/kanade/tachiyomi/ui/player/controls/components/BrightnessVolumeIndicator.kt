package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrightnessVolumeIndicator(
    isBrightness: Boolean,
    value: Float,
    maxValue: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .width(48.dp)
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 16.dp),
            ) {
                Icon(
                    imageVector = if (isBrightness) {
                        Icons.Default.BrightnessHigh
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = null,
                    tint = Color.White,
                )

                val fraction = if (maxValue > 0) (value / maxValue).coerceIn(0f, 1f) else 0f
                val percent = (fraction * 100).toInt()
                Text(
                    text = "$percent%",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                ) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

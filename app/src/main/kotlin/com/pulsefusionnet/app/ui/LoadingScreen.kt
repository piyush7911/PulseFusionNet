package com.pulsefusionnet.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The app's sole splash/loading moment — no separate OS-level splash screen, just this:
 * the brand icon pulsing on a dark background from the instant the process starts, plus
 * one line of text so the user immediately confirms which app just opened.
 * Icon proportions (38dp heart in an 84dp circle) intentionally match the launcher icon.
 */
@Composable
fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "loading")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "loadingScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(PulseColors.BrandGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(PulseIcons.Heart, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.size(18.dp))
            Text("PulseFusionNet", style = MaterialTheme.typography.titleMedium, color = PulseColors.Muted2)
        }
    }
}

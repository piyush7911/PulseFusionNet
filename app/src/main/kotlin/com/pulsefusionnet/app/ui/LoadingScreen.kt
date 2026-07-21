package com.pulsefusionnet.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberInfiniteTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Brief branded loading state shown right after the OS-level splash screen hands off to
 * Compose — bridges the gap while we check camera permission, so there's never a jarring
 * jump straight into a permission dialog on cold start.
 */
@Composable
fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "loading")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "loadingScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
        Text("PulseFusionNet", style = MaterialTheme.typography.headlineMedium, color = PulseColors.White)
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        Text(
            "Loading on-device model…",
            style = MaterialTheme.typography.bodyMedium,
            color = PulseColors.Muted2
        )
    }
}

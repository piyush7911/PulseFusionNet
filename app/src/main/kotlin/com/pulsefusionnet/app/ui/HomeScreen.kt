package com.pulsefusionnet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private data class Step(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val body: String)

private val STEPS = listOf(
    Step(PulseIcons.Camera, "Open the scanner", "Tap Start Measurement to activate the rear camera."),
    Step(PulseIcons.Fingerprint, "Cover the lens", "Rest your fingertip flat over the camera, no flash needed."),
    Step(PulseIcons.Clock, "Hold steady", "Keep still for 60 seconds while the signal stabilizes."),
    Step(PulseIcons.CheckCircle, "Read your result", "The on-device model returns your BPM and confidence.")
)

@Composable
fun HomeScreen(onStart: () -> Unit) {
    var showSteps by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(PulseColors.BrandGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PulseIcons.Heart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text("PulseFusionNet", style = MaterialTheme.typography.headlineMedium, color = PulseColors.White)
            }
            StatusPill("ON-DEVICE AI", PulseColors.Blue)
        }

        HeroHeartCard()

        GradientButton("Start Measurement", icon = PulseIcons.Fingerprint, onClick = onStart)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PulseColors.Card)
                .clickable { showSteps = !showSteps }
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(PulseIcons.Bulb, "How it works")
                Icon(
                    PulseIcons.ChevronRight, contentDescription = null, tint = PulseColors.Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = if (showSteps) 90f else 0f }
                )
            }

            AnimatedVisibility(visible = showSteps, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    STEPS.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(PulseColors.BrandGradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            Column {
                                Text(step.title, style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
                                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted2)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PulseColors.Blue.copy(alpha = 0.05f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(PulseIcons.Shield, contentDescription = null, tint = PulseColors.Muted, modifier = Modifier.size(15.dp))
            Text(
                "Runs fully offline on this device. General wellness reference only — not a medical device.",
                style = MaterialTheme.typography.bodyMedium,
                color = PulseColors.Muted
            )
        }
    }
}

@Composable
private fun HeroHeartCard() {
    val transition = rememberInfiniteTransition(label = "heartbeat")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "heartScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(PulseColors.Blue.copy(alpha = 0.16f), PulseColors.Cyan.copy(alpha = 0.08f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                PulseIcons.Heart,
                contentDescription = null,
                tint = PulseColors.Blue,
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            )
            Text(
                "Ready when you are",
                style = MaterialTheme.typography.titleMedium,
                color = PulseColors.White,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                "60-second on-device pulse scan",
                style = MaterialTheme.typography.bodyMedium,
                color = PulseColors.Muted2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

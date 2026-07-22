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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

import com.pulsefusionnet.app.camera.CameraController

@Composable
fun DetectingScreen(
    fingerOnLens: Boolean,
    stabilizationPct: Int,
    waveformSamples: List<Float>,
    cameraController: CameraController? = null,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onCancel) {
                Icon(
                    PulseIcons.ChevronRight, contentDescription = "Cancel",
                    tint = PulseColors.White,
                    modifier = Modifier.graphicsLayer { rotationZ = 180f }.size(20.dp)
                )
            }
            Text("Finger Detection", style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            ScanRing(active = fingerOnLens, progress = stabilizationPct / 100f, cameraController = cameraController)
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PulsingDot(if (fingerOnLens) PulseColors.Green else PulseColors.Muted)
                    Column {
                        Text(
                            if (fingerOnLens) "Finger detected!" else "Waiting for finger…",
                            style = MaterialTheme.typography.titleMedium, color = PulseColors.White
                        )
                        Text(
                            if (fingerOnLens) "Hold still — calibrating signal quality" else "Cover the camera lens completely with your fingertip",
                            style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted2
                        )
                    }
                }
                if (fingerOnLens) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { stabilizationPct / 100f },
                        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                        color = PulseColors.Cyan,
                        trackColor = Color.White.copy(alpha = 0.06f)
                    )
                }
            }
        }

        if (waveformSamples.size > 5) {
            GlassCard {
                Column {
                    Text("LIVE SIGNAL", style = MaterialTheme.typography.labelSmall, color = PulseColors.Muted)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Waveform(waveformSamples, PulseColors.Red)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(PulseColors.Blue.copy(alpha = 0.06f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Icon(PulseIcons.Bulb, contentDescription = null, tint = PulseColors.Blue, modifier = Modifier.size(16.dp))
            Text(
                "Apply gentle, flat pressure — keep your fingertip still over the lens.",
                style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted2
            )
        }
    }
}

@Composable
private fun ScanRing(active: Boolean, progress: Float, cameraController: CameraController? = null) {
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanPulse"
    )
    val color = if (active) PulseColors.Green else PulseColors.Blue

    Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        ProgressRing(progress = if (active) progress else 0f, modifier = Modifier.fillMaxSize().aspectRatio(1f))
        Box(
            modifier = Modifier
                .size(105.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (cameraController != null) {
                CameraLivePreview(cameraController = cameraController, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    PulseIcons.Fingerprint, contentDescription = null, tint = color,
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer { if (!active) { scaleX = pulse; scaleY = pulse } }
                )
            }
        }
    }
}

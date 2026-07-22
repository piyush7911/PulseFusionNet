package com.pulsefusionnet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.pulsefusionnet.app.camera.CameraController
import androidx.compose.foundation.shape.CircleShape

@Composable
fun MeasuringScreen(
    secondsRemaining: Int,
    liveBpmText: String,
    signalQualityPct: Int,
    fingerPresent: Boolean,
    movementWarning: Boolean,
    isPaused: Boolean,
    waveformSamples: List<Float>,
    cameraController: CameraController? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Measuring", style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PulsingDot(PulseColors.Green, size = 7)
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = PulseColors.Green)
            }
        }

        FingerStatusPill(fingerPresent, movementWarning)

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                ProgressRing(progress = 1f - (secondsRemaining / 60f), modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(PulseColors.Card),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraController != null) {
                        CameraLivePreview(cameraController = cameraController, modifier = Modifier.fillMaxSize())
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$secondsRemaining",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp),
                                color = PulseColors.White
                            )
                            Text(
                                if (isPaused) "PAUSED" else "sec left",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isPaused) PulseColors.Orange else PulseColors.Muted
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(PulseColors.Blue.copy(alpha = 0.12f), PulseColors.Cyan.copy(alpha = 0.05f))
                    )
                )
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIVE READING", style = MaterialTheme.typography.labelSmall, color = PulseColors.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        liveBpmText,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                        color = PulseColors.Blue
                    )
                    Text(" BPM", style = MaterialTheme.typography.titleMedium, color = PulseColors.Blue)
                }
                Text("Updates every 3 seconds", style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted)
            }
        }

        if (waveformSamples.size > 5) {
            GlassCard {
                Column {
                    Text("PPG SIGNAL", style = MaterialTheme.typography.labelSmall, color = PulseColors.Muted)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Waveform(waveformSamples, PulseColors.Red)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Signal Quality", style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted)
                Text("$signalQualityPct%", style = MaterialTheme.typography.bodyMedium, color = PulseColors.Cyan)
            }
            LinearProgressIndicator(
                progress = { signalQualityPct / 100f },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)),
                color = PulseColors.Cyan,
                trackColor = Color.White.copy(alpha = 0.06f)
            )
        }
    }
}

@Composable
private fun FingerStatusPill(present: Boolean, movement: Boolean) {
    val (color, text) = when {
        !present -> PulseColors.Red to "Finger missing — replace to resume"
        movement -> PulseColors.Orange to "Movement detected — hold still!"
        else -> PulseColors.Green to "Finger detected — measuring"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PulsingDot(color, size = 8)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PulseColors.White)
    }
}

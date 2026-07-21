package com.pulsefusionnet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val TIPS = listOf(
    "Keep your finger flat and still on the lens",
    "Apply gentle, steady pressure — not too hard",
    "Rest your arm on a surface to reduce motion",
    "Avoid scrolling or tapping during measurement",
    "If outdoors, shade the camera from direct sunlight"
)

@Composable
fun FailedScreen(reason: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(PulseColors.Red.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(PulseIcons.Warning, contentDescription = null, tint = PulseColors.Red, modifier = Modifier.size(40.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Measurement Failed", style = MaterialTheme.typography.headlineLarge, color = PulseColors.Red)
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            Text(reason, style = MaterialTheme.typography.bodyLarge, color = PulseColors.Muted2, textAlign = TextAlign.Center)
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(PulseIcons.Pin, "Tips for accurate reading")
                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TIPS.forEach { tip ->
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                PulseIcons.ChevronRight, contentDescription = null,
                                tint = PulseColors.Blue, modifier = Modifier.size(14.dp)
                            )
                            Text(tip, style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted2)
                        }
                    }
                }
            }
        }

        GradientButton("Try Again", icon = PulseIcons.Refresh, onClick = onRetry, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onCancel) {
            Text("Back to home", color = PulseColors.Muted)
        }
    }
}

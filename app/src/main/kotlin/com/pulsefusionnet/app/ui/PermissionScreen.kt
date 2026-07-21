package com.pulsefusionnet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(
    wasDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(PulseColors.BrandGradient)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                PulseIcons.Camera, contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.size(28.dp))
        Text(
            "Camera Access Needed",
            style = MaterialTheme.typography.headlineLarge,
            color = PulseColors.White,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        Text(
            "PulseFusionNet reads your pulse through the rear camera lens — everything is " +
                "processed on this device, nothing is uploaded anywhere.",
            style = MaterialTheme.typography.bodyLarge,
            color = PulseColors.Muted2,
            textAlign = TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(Modifier.size(36.dp))

        if (wasDenied) {
            GradientButton("Open App Settings", icon = PulseIcons.Shield, onClick = onOpenSettings)
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(
                "Camera permission was denied. Enable it from Settings to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = PulseColors.Muted,
                textAlign = TextAlign.Center
            )
        } else {
            GradientButton("Grant Camera Access", icon = PulseIcons.Camera, onClick = onRequestPermission)
        }
    }
}

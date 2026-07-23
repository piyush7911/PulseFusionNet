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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
    Step(PulseIcons.Fingerprint, "Cover the lens", "Rest your fingertip flat over the camera, flash needed only if ambient light is less."),
    Step(PulseIcons.Clock, "Hold steady", "Keep still for 60 seconds while the signal stabilizes."),
    Step(PulseIcons.CheckCircle, "Read your result", "The on-device model returns your BPM and confidence.")
)

@Composable
fun HomeScreen(onStart: () -> Unit) {
    var showSteps by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

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
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Top Bar Update Icon Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PulseColors.Card)
                        .clickable { showUpdateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        PulseIcons.Refresh,
                        contentDescription = "Check for Updates",
                        tint = PulseColors.Cyan,
                        modifier = Modifier.size(17.dp)
                    )
                }
                StatusPill("ON-DEVICE AI", PulseColors.Blue)
            }
        }

        if (showUpdateDialog) {
            UpdateDialog(onDismiss = { showUpdateDialog = false })
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
                    PulseIcons.ChevronRight,
                    contentDescription = null,
                    tint = PulseColors.Muted2,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = if (showSteps) 90f else 0f }
                )
            }

            AnimatedVisibility(
                visible = showSteps,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    STEPS.forEach { step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(PulseColors.Blue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(step.icon, contentDescription = null, tint = PulseColors.Blue, modifier = Modifier.size(14.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(step.title, style = MaterialTheme.typography.titleSmall, color = PulseColors.White)
                                Text(step.body, style = MaterialTheme.typography.bodySmall, color = PulseColors.Muted2)
                            }
                        }
                    }
                }
            }
        }

        GuidanceCard()

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
                "General wellness reference only — not a medical device.",
                style = MaterialTheme.typography.bodyMedium,
                color = PulseColors.Muted
            )
        }
    }
}

@Composable
private fun GuidanceCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PulseColors.Card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(PulseIcons.Pin, contentDescription = null, tint = PulseColors.Cyan, modifier = Modifier.size(18.dp))
            Text("Scanning Guidance", style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PulseColors.Cyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(PulseIcons.Target, contentDescription = null, tint = PulseColors.Cyan, modifier = Modifier.size(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Reliable Range: 54 – 135 BPM", style = MaterialTheme.typography.titleSmall, color = PulseColors.White)
                Text("Optimized for still resting measurement on smartphone camera optical sensors.", style = MaterialTheme.typography.bodySmall, color = PulseColors.Muted2)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PulseColors.Cyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(PulseIcons.Clock, contentDescription = null, tint = PulseColors.Cyan, modifier = Modifier.size(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Post-Exercise Tip (Wait 3 Mins)", style = MaterialTheme.typography.titleSmall, color = PulseColors.White)
                Text("After heavy exercise, rest for 3 minutes before measuring for your pulse to stabilize.", style = MaterialTheme.typography.bodySmall, color = PulseColors.Muted2)
            }
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

@Composable
private fun UpdateDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(com.pulsefusionnet.app.update.UpdateStatus.IDLE) }
    var updateInfo by remember { mutableStateOf<com.pulsefusionnet.app.update.UpdateInfo?>(null) }
    var progressPct by remember { mutableStateOf(0) }

    // Automatically check remote update server when dialog is opened
    androidx.compose.runtime.LaunchedEffect(Unit) {
        status = com.pulsefusionnet.app.update.UpdateStatus.CHECKING
        val (newStatus, info) = com.pulsefusionnet.app.update.InAppUpdateManager.checkForUpdate()
        status = newStatus
        updateInfo = info
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PulseColors.Surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(PulseIcons.Refresh, contentDescription = null, tint = PulseColors.Cyan, modifier = Modifier.size(20.dp))
                    Text("App Update Check", style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
                }
                StatusPill(com.pulsefusionnet.app.update.InAppUpdateManager.CURRENT_VERSION_NAME, PulseColors.Blue)
            }

            Text(
                text = when (status) {
                    com.pulsefusionnet.app.update.UpdateStatus.IDLE -> "Checking for updates..."
                    com.pulsefusionnet.app.update.UpdateStatus.CHECKING -> "Checking remote update server..."
                    com.pulsefusionnet.app.update.UpdateStatus.UPDATE_AVAILABLE -> "New update available: ${updateInfo?.versionName ?: "v1.2.0"}\n\n${updateInfo?.changelog ?: "Latest engine & UI improvements."}"
                    com.pulsefusionnet.app.update.UpdateStatus.DOWNLOADING -> "Downloading update over network... $progressPct%"
                    com.pulsefusionnet.app.update.UpdateStatus.INSTALLING -> "Launching automatic APK installation..."
                    com.pulsefusionnet.app.update.UpdateStatus.UP_TO_DATE -> "Your app is running the latest verified algorithm engine (${com.pulsefusionnet.app.update.InAppUpdateManager.CURRENT_VERSION_NAME})."
                    com.pulsefusionnet.app.update.UpdateStatus.ERROR -> "Network error checking update. Please try again."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PulseColors.Muted2
            )

            if (status == com.pulsefusionnet.app.update.UpdateStatus.DOWNLOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PulseColors.Muted2.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPct / 100f)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(PulseColors.BrandGradient))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PulseColors.SurfaceAlt)
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", style = MaterialTheme.typography.titleSmall, color = PulseColors.White)
                }

                if (status == com.pulsefusionnet.app.update.UpdateStatus.UPDATE_AVAILABLE) {
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(PulseColors.BrandGradient))
                            .clickable {
                                val url = updateInfo?.downloadUrl ?: return@clickable
                                status = com.pulsefusionnet.app.update.UpdateStatus.DOWNLOADING
                                scope.launch {
                                    val success = com.pulsefusionnet.app.update.InAppUpdateManager.downloadAndInstall(
                                        context,
                                        url,
                                        onProgress = { p -> progressPct = p }
                                    )
                                    status = if (success) com.pulsefusionnet.app.update.UpdateStatus.INSTALLING else com.pulsefusionnet.app.update.UpdateStatus.ERROR
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Update Now", style = MaterialTheme.typography.titleSmall, color = PulseColors.White)
                    }
                }
            }
        }
    }
}

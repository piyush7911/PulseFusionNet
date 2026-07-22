package com.pulsefusionnet.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small tinted circular chip used to prefix a section header — the app-wide icon container. */
@Composable
fun IconChip(icon: ImageVector, tint: Color = PulseColors.Blue, size: Int = 26) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.6).dp))
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconChip(icon)
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = PulseColors.Muted2
        )
    }
}

/** Frosted card matching the brand's dark-glass surfaces. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PulseColors.Card)
            .border(1.dp, PulseColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Primary CTA button with the brand gradient and a subtle shimmer sweep. */
@Composable
fun GradientButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (outline) Modifier
                .background(PulseColors.Blue.copy(alpha = 0.08f))
                .border(1.dp, PulseColors.Blue.copy(alpha = 0.35f), shape)
            else Modifier.background(
                Brush.linearGradient(PulseColors.BrandGradient)
            )
        )
        .then(modifier)

    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = base,
        color = Color.Transparent,
        contentColor = if (outline) PulseColors.Blue else Color.White
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun RowScope.StatTile(icon: ImageVector, value: String, label: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(PulseColors.Card)
            .border(1.dp, PulseColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = PulseColors.Blue, modifier = Modifier.size(17.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = PulseColors.Muted)
        }
    }
}

/** Pulsing dot used for "live" / status indicators. */
@Composable
fun PulsingDot(color: Color, size: Int = 9) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Live PPG sparkline — plots the REAL captured camera readings, no synthetic motion.
 * Raw brightness samples are dominated by slow baseline drift, which would otherwise
 * bury the actual pulsatile ripple; a local moving-average detrend (same window and
 * formula as the web app's drawWaveform) subtracts that drift so the genuine
 * heartbeat-shaped fluctuation in the real signal is what's visible.
 */
@Composable
fun Waveform(samples: List<Float>, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxWidth().aspectRatio(5f)) {
        if (samples.size < 5) return@Canvas
        val win = 15
        val detrended = FloatArray(samples.size) { i ->
            val lo = maxOf(0, i - win)
            val hi = minOf(samples.size - 1, i + win)
            var sum = 0f
            var count = 0
            for (j in lo..hi) { sum += samples[j]; count++ }
            samples[i] - sum / count
        }
        val w = size.width
        val h = size.height
        val minV = detrended.min()
        val maxV = detrended.max()
        val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
        val step = w / (detrended.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        detrended.forEachIndexed { i, v ->
            val x = i * step
            val y = h - ((v - minV) / range) * (h - 8) - 4
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Circular countdown / progress ring drawn with Canvas, brand-gradient stroke. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, strokeWidth: Float = 14f, trackColor: Color = Color.White.copy(alpha = 0.06f)) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = androidx.compose.ui.geometry.Size(diameter - strokeWidth, diameter - strokeWidth)
        val arcTopLeft = Offset(topLeft.x + strokeWidth / 2f, topLeft.y + strokeWidth / 2f)

        drawArc(
            color = trackColor,
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = arcTopLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawArc(
            brush = Brush.linearGradient(PulseColors.BrandGradient),
            startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = arcTopLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

/** Embeds CameraX PreviewView into Jetpack Compose UI for live camera monitoring. */
@Composable
fun CameraLivePreview(cameraController: com.pulsefusionnet.app.camera.CameraController?, modifier: Modifier = Modifier) {
    if (cameraController == null) return
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.camera.view.PreviewView(ctx).apply {
                scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                cameraController.attachPreview(this)
            }
        },
        modifier = modifier
    )
}

package com.pulsefusionnet.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsefusionnet.app.ResultData
import com.pulsefusionnet.app.ZoneLabel

private data class ZoneInfo(val label: String, val range: String, val color: Color)

private val ZONES = listOf(
    ZoneInfo("Bradycardia", "< 60 BPM", Color(0xFF60A5FA)),
    ZoneInfo("Normal Resting", "60-100 BPM", PulseColors.Green),
    ZoneInfo("Elevated", "100-120 BPM", PulseColors.Orange),
    ZoneInfo("High", "> 120 BPM", PulseColors.Red)
)

private fun zoneName(zone: ZoneLabel) = when (zone) {
    ZoneLabel.BRADYCARDIA -> "Bradycardia"
    ZoneLabel.NORMAL -> "Normal Resting"
    ZoneLabel.ELEVATED -> "Elevated"
    ZoneLabel.HIGH -> "High — Rest & Recheck"
}

private fun zoneColor(zone: ZoneLabel) = when (zone) {
    ZoneLabel.BRADYCARDIA -> Color(0xFF60A5FA)
    ZoneLabel.NORMAL -> PulseColors.Green
    ZoneLabel.ELEVATED -> PulseColors.Orange
    ZoneLabel.HIGH -> PulseColors.Red
}

@Composable
fun ResultScreen(result: ResultData, onMeasureAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Result", style = MaterialTheme.typography.titleMedium, color = PulseColors.White)
            StatusPill("COMPLETE", PulseColors.Green)
        }

        val zColor = zoneColor(result.zone)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(listOf(PulseColors.Blue.copy(alpha = 0.16f), PulseColors.Cyan.copy(alpha = 0.08f)))
                )
                .padding(vertical = 34.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HEART RATE", style = MaterialTheme.typography.labelSmall, color = PulseColors.Muted)
                Text(
                    "%.0f".format(result.bpm),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    color = PulseColors.White
                )
                Text("BPM", style = MaterialTheme.typography.titleMedium, color = PulseColors.Blue)
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                StatusPill(zoneName(result.zone).uppercase(), zColor)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(PulseIcons.Target, "%.0f%%".format(result.confidence), "Confidence")
            StatTile(PulseIcons.BarChart, "${result.samples}", "Readings")
            StatTile(PulseIcons.Award, "0.60", "MAE (BPM)")
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(PulseIcons.BarChart, "Heart rate zones")
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZONES.forEach { z ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(z.color.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(z.color))
                            Text(z.range, style = MaterialTheme.typography.bodyMedium, color = PulseColors.White, modifier = Modifier.width(92.dp))
                            Text(z.label, style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted2)
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
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(PulseIcons.Shield, contentDescription = null, tint = PulseColors.Muted, modifier = Modifier.size(15.dp))
            Text(
                "For general wellness & research reference only. Not a medical device.",
                style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted
            )
        }

        GradientButton("Done", icon = PulseIcons.CheckCircle, onClick = onDone)
        GradientButton("Measure Again", icon = PulseIcons.Refresh, outline = true, onClick = onMeasureAgain)
    }
}

package com.pulsefusionnet.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsefusionnet.app.ResultData
import com.pulsefusionnet.app.ZoneLabel

private data class ZoneInfo(val label: String, val range: String, val color: Color)

private val ZONES = listOf(
    ZoneInfo("Bradycardia", "< 60 BPM", Color(0xFF60A5FA)),
    ZoneInfo("Normal Resting", "60–100 BPM", PulseColors.Green),
    ZoneInfo("Elevated", "100–120 BPM", PulseColors.Orange),
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Result", style = MaterialTheme.typography.headlineMedium, color = PulseColors.White)
            StatusPill("COMPLETE", PulseColors.Green)
        }

        val zColor = zoneColor(result.zone)

        OutlinedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = PulseColors.Surface),
            border = BorderStroke(1.dp, PulseColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(zColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PulseIcons.Heart, contentDescription = null, tint = zColor, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.size(14.dp))
                Text("HEART RATE", style = MaterialTheme.typography.labelSmall, color = PulseColors.Muted)
                Text(
                    "%.0f".format(result.bpm),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                    color = PulseColors.White
                )
                Text("BPM", style = MaterialTheme.typography.titleMedium, color = PulseColors.Muted2)
                Spacer(Modifier.size(12.dp))
                StatusPill(zoneName(result.zone).uppercase(), zColor)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(PulseIcons.Target, "%.0f%%".format(result.confidence), "Confidence")
            StatTile(PulseIcons.BarChart, "${result.samples}", "Readings")
            StatTile(PulseIcons.Award, "0.60", "MAE (BPM)")
        }

        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = PulseColors.Surface),
            border = BorderStroke(1.dp, PulseColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeader(PulseIcons.BarChart, "Heart rate zones")
                Spacer(Modifier.size(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ZONES.forEach { z ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(z.color)
                            )
                            Text(
                                z.range,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PulseColors.White,
                                modifier = Modifier.width(92.dp)
                            )
                            Text(z.label, style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted2)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                PulseIcons.Shield, contentDescription = null, tint = PulseColors.Muted,
                modifier = Modifier.size(15.dp).padding(top = 2.dp)
            )
            Text(
                "For general wellness & research reference only. Not a medical device.",
                style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted
            )
        }

        Spacer(Modifier.size(4.dp))

        GradientButton("Done", icon = PulseIcons.CheckCircle, onClick = onDone)

        OutlinedButton(
            onClick = onMeasureAgain,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PulseColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(PulseIcons.Refresh, contentDescription = null, tint = PulseColors.Muted2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text("Measure Again", color = PulseColors.Muted2)
        }
    }
}

package com.pulsefusionnet.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PulseColors {
    val Background = Color(0xFF03070F)
    val Surface = Color(0xFF0A1120)
    val SurfaceAlt = Color(0xFF0F1A2E)
    val Card = Color(0x0AFFFFFF)
    val CardBorder = Color(0x2E3B82F6)
    val Blue = Color(0xFF3B82F6)
    val BlueMid = Color(0xFF2563EB)
    val Cyan = Color(0xFF06B6D4)
    val White = Color(0xFFF8FAFC)
    val Muted = Color(0xFF64748B)
    val Muted2 = Color(0xFF94A3B8)
    val Green = Color(0xFF10B981)
    val Orange = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)

    val BrandGradient = listOf(BlueMid, Blue, Cyan)
}

private val PulseDarkScheme = darkColorScheme(
    primary = PulseColors.Blue,
    onPrimary = PulseColors.White,
    secondary = PulseColors.Cyan,
    background = PulseColors.Background,
    onBackground = PulseColors.White,
    surface = PulseColors.Surface,
    onSurface = PulseColors.White,
    surfaceVariant = PulseColors.SurfaceAlt,
    error = PulseColors.Red
)

private val PulseTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 56.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
)

@Composable
fun PulseFusionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulseDarkScheme,
        typography = PulseTypography,
        content = content
    )
}

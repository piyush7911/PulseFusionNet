package com.pulsefusionnet.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small stroke-style icon set (feather/lucide-inspired) so the app doesn't rely on emoji
 * glyphs anywhere. Each is a tiny outlined ImageVector built at 24x24 viewport.
 */
object PulseIcons {

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(block)
            .build()

    private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.stroke(
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
            pathBuilder = pathData
        )
    }

    val Heart: ImageVector by lazy {
        icon("Heart") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 21f)
                curveTo(12f, 21f, 3f, 15.5f, 3f, 9.5f)
                curveTo(3f, 6.5f, 5.5f, 4f, 8.5f, 4f)
                curveTo(10f, 4f, 11.2f, 4.7f, 12f, 5.8f)
                curveTo(12.8f, 4.7f, 14f, 4f, 15.5f, 4f)
                curveTo(18.5f, 4f, 21f, 6.5f, 21f, 9.5f)
                curveTo(21f, 15.5f, 12f, 21f, 12f, 21f)
                close()
            }
        }
    }

    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            stroke {
                moveTo(12f, 10f); curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
                curveTo(10f, 13.6f, 9.4f, 15f, 8.4f, 16.1f)
            }
            stroke {
                moveTo(18f, 8.2f); curveTo(16.6f, 5.6f, 13.9f, 3.9f, 10.8f, 4.2f)
                curveTo(7.9f, 4.5f, 5.4f, 6.5f, 4.2f, 9.2f)
            }
            stroke {
                moveTo(2.2f, 12.3f); curveTo(2.2f, 7.2f, 6.5f, 3f, 11.9f, 3f)
                curveTo(15.2f, 3f, 18.1f, 4.6f, 19.9f, 7.2f)
            }
            stroke {
                moveTo(21.5f, 15.8f); curveTo(21.7f, 13.9f, 21.7f, 12.2f, 21.1f, 10.4f)
            }
            stroke {
                moveTo(5.3f, 19.1f); curveTo(7.6f, 16.7f, 8.7f, 14.4f, 8.7f, 12f)
            }
            stroke {
                moveTo(18.1f, 21f); curveTo(15.6f, 18.7f, 14f, 15.8f, 14f, 12f)
                curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke { moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f); close() }
            stroke { moveTo(12f, 7.5f); lineTo(12f, 12.5f); lineTo(15.2f, 14.3f) }
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            stroke { moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f); close() }
            stroke { moveTo(8.3f, 12.4f); lineTo(10.8f, 14.9f); lineTo(15.5f, 9.7f) }
        }
    }

    val Bulb: ImageVector by lazy {
        icon("Bulb") {
            stroke { moveTo(9f, 18f); lineTo(15f, 18f) }
            stroke { moveTo(10f, 21f); lineTo(14f, 21f) }
            stroke {
                moveTo(12f, 3f); curveTo(8.7f, 3f, 6f, 5.7f, 6f, 9f)
                curveTo(6f, 11.6f, 7.4f, 12.9f, 8.1f, 13.9f); curveTo(8.5f, 14.4f, 8.8f, 14.9f, 8.8f, 15.5f)
                lineTo(8.8f, 16f); lineTo(15.2f, 16f); lineTo(15.2f, 15.5f)
                curveTo(15.2f, 14.9f, 15.5f, 14.4f, 15.9f, 13.9f); curveTo(16.6f, 12.9f, 18f, 11.6f, 18f, 9f)
                curveTo(18f, 5.7f, 15.3f, 3f, 12f, 3f); close()
            }
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            stroke { moveTo(12f, 3.2f); lineTo(2.4f, 20f); lineTo(21.6f, 20f); close() }
            stroke { moveTo(12f, 9.8f); lineTo(12f, 14.4f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12.9f, 17.4f); arcTo(0.9f, 0.9f, 0f, true, true, 11.1f, 17.4f); arcTo(0.9f, 0.9f, 0f, true, true, 12.9f, 17.4f); close() }
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            stroke {
                moveTo(12f, 2f); curveTo(9.2f, 2f, 7f, 4.2f, 7f, 7f); curveTo(7f, 10.5f, 12f, 17f, 12f, 17f)
                curveTo(12f, 17f, 17f, 10.5f, 17f, 7f); curveTo(17f, 4.2f, 14.8f, 2f, 12f, 2f); close()
            }
            stroke { moveTo(14f, 7f); arcTo(2f, 2f, 0f, true, true, 10f, 7f); arcTo(2f, 2f, 0f, true, true, 14f, 7f); close() }
        }
    }

    val Target: ImageVector by lazy {
        icon("Target") {
            stroke { moveTo(20f, 12f); arcTo(8f, 8f, 0f, true, true, 4f, 12f); arcTo(8f, 8f, 0f, true, true, 20f, 12f); close() }
            stroke { moveTo(16.5f, 12f); arcTo(4.5f, 4.5f, 0f, true, true, 7.5f, 12f); arcTo(4.5f, 4.5f, 0f, true, true, 16.5f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(13f, 12f); arcTo(1f, 1f, 0f, true, true, 11f, 12f); arcTo(1f, 1f, 0f, true, true, 13f, 12f); close() }
        }
    }

    val BarChart: ImageVector by lazy {
        icon("BarChart") {
            stroke { moveTo(5f, 20f); lineTo(5f, 10f) }
            stroke { moveTo(12f, 20f); lineTo(12f, 4f) }
            stroke { moveTo(19f, 20f); lineTo(19f, 13f) }
        }
    }

    val Award: ImageVector by lazy {
        icon("Award") {
            stroke { moveTo(17f, 8.5f); arcTo(5f, 5f, 0f, true, true, 7f, 8.5f); arcTo(5f, 5f, 0f, true, true, 17f, 8.5f); close() }
            stroke { moveTo(8.3f, 13f); lineTo(6.5f, 22f); lineTo(12f, 19f); lineTo(17.5f, 22f); lineTo(15.7f, 13f) }
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            stroke { moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f); close() }
            stroke { moveTo(12f, 8f); lineTo(12f, 8.01f) }
            stroke { moveTo(11.2f, 12f); lineTo(12.1f, 12f); lineTo(12.1f, 17f) }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathBuilder = {
                    moveTo(3.5f, 12f); curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f); curveTo(14.4f, 3.5f, 16.6f, 4.4f, 18.2f, 5.9f)
                    moveTo(20.5f, 3.5f); lineTo(20.5f, 8.5f); lineTo(15.5f, 8.5f)
                    moveTo(20.5f, 12f); curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f); curveTo(9.6f, 20.5f, 7.4f, 19.6f, 5.8f, 18.1f)
                    moveTo(3.5f, 20.5f); lineTo(3.5f, 15.5f); lineTo(8.5f, 15.5f)
                }
            )
        }
    }

    val Camera: ImageVector by lazy {
        icon("Camera") {
            stroke {
                moveTo(4f, 8f); lineTo(6.5f, 8f); lineTo(7.8f, 5.5f); lineTo(16.2f, 5.5f); lineTo(17.5f, 8f); lineTo(20f, 8f)
                curveTo(20.6f, 8f, 21f, 8.4f, 21f, 9f); lineTo(21f, 18f); curveTo(21f, 18.6f, 20.6f, 19f, 20f, 19f)
                lineTo(4f, 19f); curveTo(3.4f, 19f, 3f, 18.6f, 3f, 18f); lineTo(3f, 9f); curveTo(3f, 8.4f, 3.4f, 8f, 4f, 8f); close()
            }
            stroke { moveTo(16.5f, 13.5f); arcTo(4.5f, 4.5f, 0f, true, true, 7.5f, 13.5f); arcTo(4.5f, 4.5f, 0f, true, true, 16.5f, 13.5f); close() }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroke { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
        }
    }

    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f); lineTo(13.8f, 7.2f); lineTo(19f, 9f); lineTo(13.8f, 10.8f)
                lineTo(12f, 16f); lineTo(10.2f, 10.8f); lineTo(5f, 9f); lineTo(10.2f, 7.2f); close()
            }
        }
    }

    val Flash: ImageVector by lazy {
        icon("Flash") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 2f)
                lineTo(4f, 13f)
                lineTo(11.5f, 13f)
                lineTo(10f, 22f)
                lineTo(20f, 9f)
                lineTo(12.5f, 9f)
                close()
            }
        }
    }
}

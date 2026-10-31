package com.flow.todo.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassLevel {
    THIN,
    REGULAR,
    THICK
}

data class GlassSpec(
    val blurRadius: Dp,
    val fillColor: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val saturation: Float
)

data class FluidColors(
    val bgBase: Color,
    val surfaceSolid: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val accentPressed: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightFluidColors = FluidColors(
    bgBase = Color(0xFFF2F3F5),
    surfaceSolid = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF7F7F9),
    textPrimary = Color(0xE6000000),
    textSecondary = Color(0x8C000000),
    textTertiary = Color(0x59000000),
    divider = Color(0x14000000),
    accent = Color(0xFF2F6BFF),
    accentPressed = Color(0xFF295EE0),
    success = Color(0xFF22B35E),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFF2453D),
    scrim = Color(0x2E000000),
    isDark = false
)

val DarkFluidColors = FluidColors(
    bgBase = Color(0xFF000000),
    surfaceSolid = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2A2A2D),
    textPrimary = Color(0xEBFFFFFF),
    textSecondary = Color(0x99FFFFFF),
    textTertiary = Color(0x61FFFFFF),
    divider = Color(0x1AFFFFFF),
    accent = Color(0xFF2F6BFF),
    accentPressed = Color(0xFF477BFF),
    success = Color(0xFF22B35E),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFF2453D),
    scrim = Color(0x66000000),
    isDark = true
)

object FluidShapes {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(32.dp)
    val full = RoundedCornerShape(percent = 50)
}

object MomentumSpectrum {
    val Red = Color(0xFFFF3B5C)
    val Purple = Color(0xFFAF52DE)
    val Blue = Color(0xFF2F6BFF)
    val Yellow = Color(0xFFFFB800)
    val Green = Color(0xFF22B35E)

    val stops: List<Color> = listOf(Red, Purple, Blue, Yellow, Green)

    fun colorForProgress(progress: Float): Color {
        val clamped = progress.coerceIn(0f, 1f)
        val scaled = clamped * (stops.size - 1)
        val index = scaled.toInt().coerceIn(0, stops.size - 2)
        val fraction = scaled - index
        return androidx.compose.ui.graphics.lerp(stops[index], stops[index + 1], fraction)
    }

    fun stageLabel(progress: Float): String {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p < 0.20f -> "Warming Up • Crimson"
            p < 0.45f -> "Building Spark • Purple"
            p < 0.70f -> "In The Flow • Cobalt Blue"
            p < 0.95f -> "Final Surge • Solar Gold"
            else -> "Flow Complete • Emerald"
        }
    }

    fun seedHexForProgress(progress: Float): String {
        val c = colorForProgress(progress)
        val r = (c.red * 255f).toInt().coerceIn(0, 255)
        val g = (c.green * 255f).toInt().coerceIn(0, 255)
        val b = (c.blue * 255f).toInt().coerceIn(0, 255)
        return String.format(java.util.Locale.US, "%02X%02X%02X", r, g, b)
    }
}

object FluidSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object FluidSprings {
    fun <T> press(reduceMotion: Boolean = false): FiniteAnimationSpec<T> {
        return if (reduceMotion) {
            tween(durationMillis = 150)
        } else {
            spring(dampingRatio = 0.52f, stiffness = 650f)
        }
    }

    fun <T> release(reduceMotion: Boolean = false): FiniteAnimationSpec<T> {
        return if (reduceMotion) {
            tween(durationMillis = 150)
        } else {
            spring(dampingRatio = 0.38f, stiffness = 340f)
        }
    }

    fun <T> morph(reduceMotion: Boolean = false): FiniteAnimationSpec<T> {
        return if (reduceMotion) {
            tween(durationMillis = 150)
        } else {
            spring(dampingRatio = 0.54f, stiffness = 300f)
        }
    }

    fun <T> gentle(reduceMotion: Boolean = false): FiniteAnimationSpec<T> {
        return if (reduceMotion) {
            tween(durationMillis = 150)
        } else {
            spring(dampingRatio = 0.72f, stiffness = 240f)
        }
    }

    fun <T> bubbly(reduceMotion: Boolean = false): FiniteAnimationSpec<T> {
        return if (reduceMotion) {
            tween(durationMillis = 150)
        } else {
            spring(dampingRatio = 0.35f, stiffness = 400f)
        }
    }
}

package com.flow.todo.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flow.todo.domain.model.AccessibilityPreferences

val LocalFluidColors = staticCompositionLocalOf<FluidColors> { LightFluidColors }
val LocalAccessibilityPrefs = staticCompositionLocalOf<AccessibilityPreferences> { AccessibilityPreferences() }
val LocalSystemReducedMotion = staticCompositionLocalOf<Boolean> { false }

object FluidTheme {
    val colors: FluidColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFluidColors.current

    val prefs: AccessibilityPreferences
        @Composable
        @ReadOnlyComposable
        get() = LocalAccessibilityPrefs.current

    val isReducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = prefs.reduceMotion || LocalSystemReducedMotion.current

    @Composable
    fun glassSpec(level: GlassLevel): GlassSpec {
        val currentColors = colors
        val currentPrefs = prefs
        val isDark = currentColors.isDark

        return when {
            currentPrefs.reduceTransparency -> {
                when (level) {
                    GlassLevel.THIN -> GlassSpec(
                        blurRadius = 0.dp,
                        fillColor = currentColors.surfaceSolid,
                        strokeTop = currentColors.divider,
                        strokeBottom = Color.Transparent,
                        saturation = 1.0f
                    )
                    GlassLevel.REGULAR -> GlassSpec(
                        blurRadius = 0.dp,
                        fillColor = currentColors.surfaceRaised,
                        strokeTop = currentColors.divider,
                        strokeBottom = Color.Transparent,
                        saturation = 1.0f
                    )
                    GlassLevel.THICK -> GlassSpec(
                        blurRadius = 0.dp,
                        fillColor = currentColors.surfaceRaised,
                        strokeTop = currentColors.divider,
                        strokeBottom = currentColors.divider,
                        saturation = 1.0f
                    )
                }
            }

            currentPrefs.increasedContrast -> {
                if (isDark) {
                    when (level) {
                        GlassLevel.THIN -> GlassSpec(
                            blurRadius = 24.dp,
                            fillColor = Color(0xFF1E1E22).copy(alpha = 0.92f),
                            strokeTop = Color.White.copy(alpha = 0.50f),
                            strokeBottom = Color.White.copy(alpha = 0.20f),
                            saturation = 1.25f
                        )
                        GlassLevel.REGULAR -> GlassSpec(
                            blurRadius = 32.dp,
                            fillColor = Color(0xFF1C1C1E).copy(alpha = 0.94f),
                            strokeTop = Color.White.copy(alpha = 0.60f),
                            strokeBottom = Color.White.copy(alpha = 0.25f),
                            saturation = 1.30f
                        )
                        GlassLevel.THICK -> GlassSpec(
                            blurRadius = 44.dp,
                            fillColor = Color(0xFF18181A).copy(alpha = 0.96f),
                            strokeTop = Color.White.copy(alpha = 0.70f),
                            strokeBottom = Color.White.copy(alpha = 0.30f),
                            saturation = 1.35f
                        )
                    }
                } else {
                    when (level) {
                        GlassLevel.THIN -> GlassSpec(
                            blurRadius = 24.dp,
                            fillColor = Color.White.copy(alpha = 0.92f),
                            strokeTop = Color(0x66000000),
                            strokeBottom = Color(0x33000000),
                            saturation = 1.25f
                        )
                        GlassLevel.REGULAR -> GlassSpec(
                            blurRadius = 32.dp,
                            fillColor = Color.White.copy(alpha = 0.94f),
                            strokeTop = Color(0x80000000),
                            strokeBottom = Color(0x40000000),
                            saturation = 1.30f
                        )
                        GlassLevel.THICK -> GlassSpec(
                            blurRadius = 44.dp,
                            fillColor = Color.White.copy(alpha = 0.96f),
                            strokeTop = Color(0x99000000),
                            strokeBottom = Color(0x4D000000),
                            saturation = 1.35f
                        )
                    }
                }
            }

            else -> {
                if (isDark) {
                    when (level) {
                        // glass.thin 50% @ 24.dp
                        GlassLevel.THIN -> GlassSpec(
                            blurRadius = 24.dp,
                            fillColor = Color(0xFF1E1E22).copy(alpha = 0.50f),
                            strokeTop = Color.White.copy(alpha = 0.22f),
                            strokeBottom = Color.White.copy(alpha = 0.05f),
                            saturation = 1.25f
                        )
                        // glass.regular 62% @ 32.dp
                        GlassLevel.REGULAR -> GlassSpec(
                            blurRadius = 32.dp,
                            fillColor = Color(0xFF1C1C1E).copy(alpha = 0.62f),
                            strokeTop = Color.White.copy(alpha = 0.28f),
                            strokeBottom = Color.White.copy(alpha = 0.08f),
                            saturation = 1.30f
                        )
                        // glass.thick 76% @ 44.dp
                        GlassLevel.THICK -> GlassSpec(
                            blurRadius = 44.dp,
                            fillColor = Color(0xFF18181A).copy(alpha = 0.76f),
                            strokeTop = Color.White.copy(alpha = 0.35f),
                            strokeBottom = Color.White.copy(alpha = 0.12f),
                            saturation = 1.35f
                        )
                    }
                } else {
                    when (level) {
                        // glass.thin 55% @ 24.dp
                        GlassLevel.THIN -> GlassSpec(
                            blurRadius = 24.dp,
                            fillColor = Color.White.copy(alpha = 0.55f),
                            strokeTop = Color.White.copy(alpha = 0.70f),
                            strokeBottom = Color(0x14000000),
                            saturation = 1.25f
                        )
                        // glass.regular 68% @ 32.dp
                        GlassLevel.REGULAR -> GlassSpec(
                            blurRadius = 32.dp,
                            fillColor = Color.White.copy(alpha = 0.68f),
                            strokeTop = Color.White.copy(alpha = 0.85f),
                            strokeBottom = Color(0x1F000000),
                            saturation = 1.30f
                        )
                        // glass.thick 80% @ 44.dp
                        GlassLevel.THICK -> GlassSpec(
                            blurRadius = 44.dp,
                            fillColor = Color.White.copy(alpha = 0.80f),
                            strokeTop = Color.White.copy(alpha = 0.95f),
                            strokeBottom = Color(0x29000000),
                            saturation = 1.35f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FluidTheme(
    preferences: AccessibilityPreferences = AccessibilityPreferences(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val isDark = when (preferences.darkThemeOverride) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }

    val fluidColors = if (isDark) DarkFluidColors else LightFluidColors

    val materialColors = if (isDark) {
        darkColorScheme(
            primary = fluidColors.accent,
            onPrimary = Color.White,
            background = fluidColors.bgBase,
            onBackground = fluidColors.textPrimary,
            surface = fluidColors.surfaceSolid,
            onSurface = fluidColors.textPrimary,
            outline = fluidColors.divider,
            error = fluidColors.danger
        )
    } else {
        lightColorScheme(
            primary = fluidColors.accent,
            onPrimary = Color.White,
            background = fluidColors.bgBase,
            onBackground = fluidColors.textPrimary,
            surface = fluidColors.surfaceSolid,
            onSurface = fluidColors.textPrimary,
            outline = fluidColors.divider,
            error = fluidColors.danger
        )
    }

    CompositionLocalProvider(
        LocalFluidColors provides fluidColors,
        LocalAccessibilityPrefs provides preferences,
        LocalSystemReducedMotion provides systemReducedMotion
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}

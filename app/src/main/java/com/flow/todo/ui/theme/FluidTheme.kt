package com.flow.todo.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
        val tintFraction = if (isDark) 0.08f else 0.05f

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
                        GlassLevel.THIN -> {
                            val baseFill = Color(0xFF1E1E22).copy(alpha = 0.92f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.50f)
                            GlassSpec(
                                blurRadius = 24.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color.White.copy(alpha = 0.20f),
                                saturation = 1.25f
                            )
                        }
                        GlassLevel.REGULAR -> {
                            val baseFill = Color(0xFF1C1C1E).copy(alpha = 0.94f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.60f)
                            GlassSpec(
                                blurRadius = 32.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color.White.copy(alpha = 0.25f),
                                saturation = 1.30f
                            )
                        }
                        GlassLevel.THICK -> {
                            val baseFill = Color(0xFF18181A).copy(alpha = 0.96f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.70f)
                            GlassSpec(
                                blurRadius = 44.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color.White.copy(alpha = 0.30f),
                                saturation = 1.35f
                            )
                        }
                    }
                } else {
                    when (level) {
                        GlassLevel.THIN -> {
                            val baseFill = Color.White.copy(alpha = 0.92f)
                            val baseStrokeTop = Color(0x66000000)
                            GlassSpec(
                                blurRadius = 24.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color(0x33000000),
                                saturation = 1.25f
                            )
                        }
                        GlassLevel.REGULAR -> {
                            val baseFill = Color.White.copy(alpha = 0.94f)
                            val baseStrokeTop = Color(0x80000000)
                            GlassSpec(
                                blurRadius = 32.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color(0x40000000),
                                saturation = 1.30f
                            )
                        }
                        GlassLevel.THICK -> {
                            val baseFill = Color.White.copy(alpha = 0.96f)
                            val baseStrokeTop = Color(0x99000000)
                            GlassSpec(
                                blurRadius = 44.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color(0x4D000000),
                                saturation = 1.35f
                            )
                        }
                    }
                }
            }

            else -> {
                if (isDark) {
                    when (level) {
                        // glass.thin 50% @ 24.dp tinted with accent
                        GlassLevel.THIN -> {
                            val baseFill = Color(0xFF1E1E22).copy(alpha = 0.50f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.22f)
                            GlassSpec(
                                blurRadius = 24.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color.White.copy(alpha = 0.05f),
                                saturation = 1.25f
                            )
                        }
                        // glass.regular 62% @ 32.dp tinted with accent
                        GlassLevel.REGULAR -> {
                            val baseFill = Color(0xFF1C1C1E).copy(alpha = 0.62f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.28f)
                            GlassSpec(
                                blurRadius = 32.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color.White.copy(alpha = 0.08f),
                                saturation = 1.30f
                            )
                        }
                        // glass.thick 76% @ 44.dp tinted with accent
                        GlassLevel.THICK -> {
                            val baseFill = Color(0xFF18181A).copy(alpha = 0.76f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.35f)
                            GlassSpec(
                                blurRadius = 44.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color.White.copy(alpha = 0.12f),
                                saturation = 1.35f
                            )
                        }
                    }
                } else {
                    when (level) {
                        // glass.thin 55% @ 24.dp tinted with accent
                        GlassLevel.THIN -> {
                            val baseFill = Color.White.copy(alpha = 0.55f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.70f)
                            GlassSpec(
                                blurRadius = 24.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color(0x14000000),
                                saturation = 1.25f
                            )
                        }
                        // glass.regular 68% @ 32.dp tinted with accent
                        GlassLevel.REGULAR -> {
                            val baseFill = Color.White.copy(alpha = 0.68f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.85f)
                            GlassSpec(
                                blurRadius = 32.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color(0x1F000000),
                                saturation = 1.30f
                            )
                        }
                        // glass.thick 80% @ 44.dp tinted with accent
                        GlassLevel.THICK -> {
                            val baseFill = Color.White.copy(alpha = 0.80f)
                            val baseStrokeTop = Color.White.copy(alpha = 0.95f)
                            GlassSpec(
                                blurRadius = 44.dp,
                                fillColor = lerp(baseFill, currentColors.accent, tintFraction),
                                strokeTop = lerp(baseStrokeTop, currentColors.accent, tintFraction),
                                strokeBottom = Color(0x29000000),
                                saturation = 1.35f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FluidTheme(
    preferences: AccessibilityPreferences = AccessibilityPreferences(),
    dailyProgress: Float = 0.5f,
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

    val canUseDynamic = preferences.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val targetFluidColors = remember(isDark, canUseDynamic, preferences.useMomentumColorSpectrum, dailyProgress, context) {
        val baseColors = if (canUseDynamic) {
            if (isDark) {
                val dynamicScheme = dynamicDarkColorScheme(context)
                FluidColors(
                    bgBase = lerp(Color(0xFF000000), dynamicScheme.surfaceDim, 0.22f),
                    surfaceSolid = lerp(Color(0xFF1C1C1E), dynamicScheme.surfaceContainerLow, 0.65f),
                    surfaceRaised = lerp(Color(0xFF2A2A2D), dynamicScheme.surfaceContainerHigh, 0.70f),
                    textPrimary = DarkFluidColors.textPrimary,
                    textSecondary = DarkFluidColors.textSecondary,
                    textTertiary = DarkFluidColors.textTertiary,
                    divider = dynamicScheme.outlineVariant.copy(alpha = 0.22f),
                    accent = dynamicScheme.primary,
                    accentPressed = dynamicScheme.primaryContainer,
                    success = DarkFluidColors.success,
                    warning = DarkFluidColors.warning,
                    danger = DarkFluidColors.danger,
                    scrim = DarkFluidColors.scrim,
                    isDark = true
                )
            } else {
                val dynamicScheme = dynamicLightColorScheme(context)
                FluidColors(
                    bgBase = lerp(Color(0xFFF2F3F5), dynamicScheme.surfaceContainerLowest, 0.70f),
                    surfaceSolid = lerp(Color(0xFFFFFFFF), dynamicScheme.surfaceContainerLow, 0.45f),
                    surfaceRaised = lerp(Color(0xFFF7F7F9), dynamicScheme.surfaceContainer, 0.65f),
                    textPrimary = LightFluidColors.textPrimary,
                    textSecondary = LightFluidColors.textSecondary,
                    textTertiary = LightFluidColors.textTertiary,
                    divider = dynamicScheme.outlineVariant.copy(alpha = 0.28f),
                    accent = dynamicScheme.primary,
                    accentPressed = dynamicScheme.primaryContainer,
                    success = LightFluidColors.success,
                    warning = LightFluidColors.warning,
                    danger = LightFluidColors.danger,
                    scrim = LightFluidColors.scrim,
                    isDark = false
                )
            }
        } else {
            if (isDark) DarkFluidColors else LightFluidColors
        }

        if (preferences.useMomentumColorSpectrum) {
            val momentumAccent = MomentumSpectrum.colorForProgress(dailyProgress)
            val momentumPressed = lerp(momentumAccent, if (isDark) Color.White else Color.Black, 0.18f)
            val tintFraction = if (isDark) 0.08f else 0.05f

            baseColors.copy(
                accent = momentumAccent,
                accentPressed = momentumPressed,
                bgBase = lerp(baseColors.bgBase, momentumAccent, tintFraction),
                surfaceSolid = lerp(baseColors.surfaceSolid, momentumAccent, tintFraction),
                surfaceRaised = lerp(baseColors.surfaceRaised, momentumAccent, tintFraction)
            )
        } else {
            baseColors
        }
    }

    val isReducedMotion = preferences.reduceMotion || systemReducedMotion

    val animatedBgBase by animateColorAsState(targetFluidColors.bgBase, FluidSprings.gentle(isReducedMotion), label = "bgBase")
    val animatedSurfaceSolid by animateColorAsState(targetFluidColors.surfaceSolid, FluidSprings.gentle(isReducedMotion), label = "surfaceSolid")
    val animatedSurfaceRaised by animateColorAsState(targetFluidColors.surfaceRaised, FluidSprings.gentle(isReducedMotion), label = "surfaceRaised")
    val animatedTextPrimary by animateColorAsState(targetFluidColors.textPrimary, FluidSprings.gentle(isReducedMotion), label = "textPrimary")
    val animatedTextSecondary by animateColorAsState(targetFluidColors.textSecondary, FluidSprings.gentle(isReducedMotion), label = "textSecondary")
    val animatedTextTertiary by animateColorAsState(targetFluidColors.textTertiary, FluidSprings.gentle(isReducedMotion), label = "textTertiary")
    val animatedDivider by animateColorAsState(targetFluidColors.divider, FluidSprings.gentle(isReducedMotion), label = "divider")
    val animatedAccent by animateColorAsState(targetFluidColors.accent, FluidSprings.gentle(isReducedMotion), label = "accent")
    val animatedAccentPressed by animateColorAsState(targetFluidColors.accentPressed, FluidSprings.gentle(isReducedMotion), label = "accentPressed")
    val animatedSuccess by animateColorAsState(targetFluidColors.success, FluidSprings.gentle(isReducedMotion), label = "success")
    val animatedWarning by animateColorAsState(targetFluidColors.warning, FluidSprings.gentle(isReducedMotion), label = "warning")
    val animatedDanger by animateColorAsState(targetFluidColors.danger, FluidSprings.gentle(isReducedMotion), label = "danger")
    val animatedScrim by animateColorAsState(targetFluidColors.scrim, FluidSprings.gentle(isReducedMotion), label = "scrim")

    val fluidColors = FluidColors(
        bgBase = animatedBgBase,
        surfaceSolid = animatedSurfaceSolid,
        surfaceRaised = animatedSurfaceRaised,
        textPrimary = animatedTextPrimary,
        textSecondary = animatedTextSecondary,
        textTertiary = animatedTextTertiary,
        divider = animatedDivider,
        accent = animatedAccent,
        accentPressed = animatedAccentPressed,
        success = animatedSuccess,
        warning = animatedWarning,
        danger = animatedDanger,
        scrim = animatedScrim,
        isDark = isDark
    )

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

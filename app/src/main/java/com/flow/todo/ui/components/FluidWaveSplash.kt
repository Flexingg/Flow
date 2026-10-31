package com.flow.todo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State holding all dynamic properties for the 2-stage Luminous Wave Splash-to-Home transition.
 */
class SplashTransitionState(
    val isFinished: Boolean,
    val iconScale: Float,
    val iconOffsetY: Dp,
    val iconAlpha: Float,
    val drawProgress: Float,
    val glowIntensity: Float,
    val haloScale: Float,
    val haloAlpha: Float,
    val textAlpha: Float,
    val textOffsetY: Dp,
    val overlayBgAlpha: Float,
    val homeContentScale: Float,
    val homeContentOffsetY: Dp,
    val homeContentAlpha: Float
)

/**
 * Orchestrates the ColorOS 17 Living Flow 2-stage launch animation:
 * - Stage 1 (0ms - 520ms): Squircle pops in with bubbly spring, wave check strokes with glowing comet tip,
 *   cyan-cobalt bubble halo blooms, and title/subtitle fade & slide up.
 * - Stage 2 (520ms - 980ms): Squircle glides to -280.dp and dissolves into FlowAppShell's top ambient glow,
 *   while home screen content simultaneously springs into place!
 * - Respects reduced motion preferences by running a quick 150ms gentle fade.
 */
@Composable
fun rememberSplashTransitionState(
    isAlreadyFinished: Boolean = false,
    onSplashFinished: () -> Unit = {}
): SplashTransitionState {
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    var isFinished by remember { mutableStateOf(isAlreadyFinished) }

    val iconScale = remember { Animatable(if (isAlreadyFinished || isReducedMotion) 1f else 0.72f) }
    val iconOffsetY = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(if (isAlreadyFinished) 0f else 1f) }
    val drawProgress = remember { Animatable(if (isAlreadyFinished || isReducedMotion) 1f else 0.15f) }
    val glowIntensity = remember { Animatable(if (isAlreadyFinished || isReducedMotion) 1f else 0.4f) }
    val haloScale = remember { Animatable(0.85f) }
    val haloAlpha = remember { Animatable(if (isAlreadyFinished || isReducedMotion) 0f else 0.7f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(if (isAlreadyFinished || isReducedMotion) 0f else 18f) }
    val overlayBgAlpha = remember { Animatable(if (isAlreadyFinished) 0f else 1f) }

    val homeContentScale = remember { Animatable(if (isAlreadyFinished || isReducedMotion) 1f else 0.93f) }
    val homeContentOffsetY = remember { Animatable(0f) }
    val homeContentAlpha = remember { Animatable(if (isAlreadyFinished) 1f else 0f) }

    LaunchedEffect(isAlreadyFinished) {
        if (isAlreadyFinished) {
            isFinished = true
            return@LaunchedEffect
        }

        if (isReducedMotion) {
            // Quick 150ms cross-fade for accessibility / reduced motion
            launch { homeContentAlpha.animateTo(1f, tween(durationMillis = 150)) }
            launch { overlayBgAlpha.animateTo(0f, tween(durationMillis = 150)) }
            iconAlpha.animateTo(0f, tween(durationMillis = 150))
            isFinished = true
            onSplashFinished()
            return@LaunchedEffect
        }

        // Initialize home content offset for intro spring
        homeContentOffsetY.snapTo(36f)

        // ========================================================
        // STAGE 1: (0ms – 520ms) - Bubbly Pop-in, Stroke & Halo Bloom
        // ========================================================
        // 1. Squircle pops in with ColorOS 17 bubbly spring (scale 0.72 -> 1.08 -> 1.0)
        launch {
            iconScale.animateTo(
                targetValue = 1.0f,
                animationSpec = FluidSprings.bubbly(reduceMotion = false)
            )
        }

        // 2. Liquid wave check draws from 0.15f -> 1.0f with glowing comet tip
        launch {
            drawProgress.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
            // Subtle confirm haptic tick when wave check completes stroke
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }

        // 3. Cyan-cobalt Bubble Ring Halo blooms outward around squircle
        launch {
            glowIntensity.animateTo(1.25f, tween(durationMillis = 280))
            glowIntensity.animateTo(1.0f, tween(durationMillis = 240))
        }
        launch {
            haloScale.animateTo(
                targetValue = 1.75f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
        launch {
            haloAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }

        // 4. "Flow" title and subtitle softly fade & slide up
        launch {
            delay(120)
            launch {
                textAlpha.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = 320)
                )
            }
            launch {
                textOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = FluidSprings.gentle(reduceMotion = false)
                )
            }
        }

        // Hold until Stage 1 finishes
        delay(520)

        // ========================================================
        // STAGE 2: (520ms – 980ms) - Morph into Home Screen
        // ========================================================
        // 1. Icon glides to top ambient glow position (-280dp), scales up & dissolves
        launch {
            iconOffsetY.animateTo(
                targetValue = -280f,
                animationSpec = FluidSprings.morph(reduceMotion = false)
            )
        }
        launch {
            iconScale.animateTo(
                targetValue = 1.65f,
                animationSpec = FluidSprings.morph(reduceMotion = false)
            )
        }
        launch {
            iconAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }
        launch {
            overlayBgAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }

        // 2. Title & subtitle dissolve
        launch {
            textAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200)
            )
        }

        // 3. Home screen content, TopBar, and IslandNavBar spring simultaneously into place
        launch {
            homeContentScale.animateTo(
                targetValue = 1.0f,
                animationSpec = FluidSprings.morph(reduceMotion = false)
            )
        }
        launch {
            homeContentOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = FluidSprings.morph(reduceMotion = false)
            )
        }
        launch {
            homeContentAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }

        // Stage 2 duration: 460ms (520ms + 460ms = 980ms total)
        delay(460)

        isFinished = true
        onSplashFinished()
    }

    return remember(
        isFinished,
        iconScale.value,
        iconOffsetY.value,
        iconAlpha.value,
        drawProgress.value,
        glowIntensity.value,
        haloScale.value,
        haloAlpha.value,
        textAlpha.value,
        textOffsetY.value,
        overlayBgAlpha.value,
        homeContentScale.value,
        homeContentOffsetY.value,
        homeContentAlpha.value
    ) {
        SplashTransitionState(
            isFinished = isFinished,
            iconScale = iconScale.value,
            iconOffsetY = iconOffsetY.value.dp,
            iconAlpha = iconAlpha.value,
            drawProgress = drawProgress.value,
            glowIntensity = glowIntensity.value,
            haloScale = haloScale.value,
            haloAlpha = haloAlpha.value,
            textAlpha = textAlpha.value,
            textOffsetY = textOffsetY.value.dp,
            overlayBgAlpha = overlayBgAlpha.value,
            homeContentScale = homeContentScale.value,
            homeContentOffsetY = homeContentOffsetY.value.dp,
            homeContentAlpha = homeContentAlpha.value
        )
    }
}

/**
 * Luminous Wave Check Squircle Icon:
 * - Squircle base filled with cobalt-to-cyan gradient and bottom cyan radial diffusion glow.
 * - 1.5.dp glass squircle contour border.
 * - Sculpted 3D liquid-glass Wave Checkmark rendered using Path & PathMeasure.
 * - Translucent frosted checkmark wave ribbons across the squircle with a glowing white-cyan Comet Tip.
 *
 * NOTE: Reads FluidTheme.colors in @Composable scope prior to entering Canvas.
 */
@Composable
fun LuminousWaveCheckIcon(
    drawProgress: Float,
    glowIntensity: Float,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val accentColor = colors.accent
    val baseCobalt = Color(0xFF1B3FFF)
    val blendedCobalt = remember(accentColor) { lerp(baseCobalt, accentColor, 0.35f) }
    val azureCyan = Color(0xFF00D2FF)
    val glowCyan = Color(0x9900F0FF)

    val shadowColor = Color(0x3300184D)
    val checkFrostStart = Color(0xE6FFFFFF)
    val checkFrostEnd = Color(0x99B8F5FF)
    val contourStart = Color(0xFFFFFFFF)
    val contourEnd = Color(0x6600F0FF)
    val highlightStart = Color(0x80FFFFFF)
    val highlightEnd = Color(0x00FFFFFF)
    val cometCore = Color(0xFFFFFFFF)
    val cometHalo = Color(0xCC00F0FF)

    val fullWavePath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cornerRadius = CornerRadius(width * (28f / 108f), height * (28f / 108f))
        val scaleX = width / 108f
        val scaleY = height / 108f

        // 1. Cobalt to Cyan Linear Gradient Background
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(blendedCobalt, azureCyan),
                start = Offset(0f, 0f),
                end = Offset(width, height)
            ),
            cornerRadius = cornerRadius
        )

        // Overlay: Bottom Cyan Radial Diffusion Glow
        val glowRadius = width * 0.52f
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowCyan.copy(alpha = 0.6f * glowIntensity),
                    Color.Transparent
                ),
                center = Offset(width * 0.5f, height * 0.85f),
                radius = glowRadius
            ),
            cornerRadius = cornerRadius
        )

        // 2. Top-to-bottom 1.5.dp Glass Squircle Contour Border
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.15f)
                )
            ),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 3. Sculpted 3D Liquid-Glass Wave Checkmark Path using Path & PathMeasure
        fullWavePath.reset()
        fullWavePath.moveTo(31f * scaleX, 56f * scaleY)
        fullWavePath.cubicTo(
            34f * scaleX, 57f * scaleY,
            41f * scaleX, 69f * scaleY,
            47f * scaleX, 71f * scaleY
        )
        fullWavePath.cubicTo(
            52f * scaleX, 73f * scaleY,
            64f * scaleX, 54f * scaleY,
            78f * scaleX, 37f * scaleY
        )

        pathMeasure.setPath(fullWavePath, false)
        val totalLength = pathMeasure.length
        val clampedProgress = drawProgress.coerceIn(0f, 1f)
        val currentLength = totalLength * clampedProgress

        if (currentLength > 0.5f) {
            val segmentPath = Path()
            pathMeasure.getSegment(0f, currentLength, segmentPath, true)

            val strokeWidth = 8.5f * scaleX
            val contourStrokeWidth = 9.8f * scaleX
            val highlightStrokeWidth = 2.4f * scaleX

            // Soft drop shadow offset +1.5 Y
            val shadowPath = Path()
            pathMeasure.getSegment(0f, currentLength, shadowPath, true)
            shadowPath.translate(Offset(0f, 1.8f * scaleY))
            drawPath(
                path = shadowPath,
                color = shadowColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Contour Glow along outer silhouette
            drawPath(
                path = segmentPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        contourStart,
                        contourEnd.copy(alpha = 0.8f * glowIntensity)
                    ),
                    start = Offset(31f * scaleX, 37f * scaleY),
                    end = Offset(78f * scaleX, 71f * scaleY)
                ),
                style = Stroke(
                    width = contourStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Main Frosted Liquid-Glass Ribbon Body
            drawPath(
                path = segmentPath,
                brush = Brush.linearGradient(
                    colors = listOf(checkFrostStart, checkFrostEnd),
                    start = Offset(31f * scaleX, 37f * scaleY),
                    end = Offset(78f * scaleX, 71f * scaleY)
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Inner Liquid Twist Highlight along upper ridge
            val highlightPath = Path()
            pathMeasure.getSegment(0f, currentLength, highlightPath, true)
            highlightPath.translate(Offset(0f, -0.9f * scaleY))
            drawPath(
                path = highlightPath,
                brush = Brush.linearGradient(
                    colors = listOf(highlightStart, highlightEnd),
                    start = Offset(31f * scaleX, 56f * scaleY),
                    end = Offset(78f * scaleX, 37f * scaleY)
                ),
                style = Stroke(
                    width = highlightStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Glowing White-Cyan Comet Tip at leading edge
            val tipPos = pathMeasure.getPosition(currentLength)
            val cometGlowRadius = 14f * scaleX * glowIntensity
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cometHalo.copy(alpha = 0.9f * glowIntensity),
                        Color.Transparent
                    ),
                    center = tipPos,
                    radius = cometGlowRadius.coerceAtLeast(1f)
                ),
                radius = cometGlowRadius.coerceAtLeast(1f),
                center = tipPos
            )
            drawCircle(
                color = cometCore,
                radius = 3.8f * scaleX,
                center = tipPos
            )
        }
    }
}

/**
 * Full Luminous Wave Check Splash-to-Home Overlay.
 * Renders the pop-in squircle, expanding bubble ring halo, flowing wave checkmark,
 * and title/subtitle text, orchestrating the morph into FlowAppShell.
 */
@Composable
fun FluidWaveSplashOverlay(
    transitionState: SplashTransitionState,
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (transitionState.isFinished) return

    val colors = FluidTheme.colors

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase.copy(alpha = transitionState.overlayBgAlpha))
    ) {
        // Squircle & Halo container, glides upward toward top ambient glow (-280dp) in Stage 2
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset(y = transitionState.iconOffsetY)
        ) {
            // Expanding Cyan-Cobalt Bubble Ring Halo
            if (transitionState.haloAlpha > 0.001f) {
                Canvas(
                    modifier = Modifier
                        .size(116.dp)
                        .graphicsLayer {
                            scaleX = transitionState.haloScale
                            scaleY = transitionState.haloScale
                            alpha = transitionState.haloAlpha
                        }
                ) {
                    val radius = size.width * 0.58f
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00F0FF),
                                Color(0xFF1B3FFF),
                                Color(0xFF00D2FF),
                                Color(0xFF00F0FF)
                            )
                        ),
                        radius = radius,
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                }
            }

            // Luminous Wave Check Squircle Icon
            LuminousWaveCheckIcon(
                drawProgress = transitionState.drawProgress,
                glowIntensity = transitionState.glowIntensity,
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        scaleX = transitionState.iconScale
                        scaleY = transitionState.iconScale
                        alpha = transitionState.iconAlpha
                    }
            )
        }

        // "Flow" title text and "Living Flow GTD" subtitle softly fade and slide up
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = 96.dp + transitionState.textOffsetY)
                .graphicsLayer {
                    alpha = transitionState.textAlpha
                }
        ) {
            Text(
                text = "Flow",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Living Flow GTD",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Convenience overload for standalone use of FluidWaveSplashOverlay.
 */
@Composable
fun FluidWaveSplashOverlay(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transitionState = rememberSplashTransitionState(onSplashFinished = onSplashFinished)
    FluidWaveSplashOverlay(
        transitionState = transitionState,
        onSplashFinished = onSplashFinished,
        modifier = modifier
    )
}

package com.flow.todo.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/**
 * ColorOS 17 Living Flow glass modifier:
 * - Applies soft 8.dp elevation shadow
 * - Clips to [shape]
 * - Renders GlassSpec fill with subtle vertical luminance sheen
 * - Draws the Contour Glow vertical gradient inner border (1.dp, or 1.5.dp in increased contrast mode)
 */
@Composable
fun Modifier.fluidGlass(
    level: GlassLevel = GlassLevel.REGULAR,
    shape: Shape = FluidShapes.lg,
    isSelected: Boolean = false,
    accentColor: Color? = null
): Modifier {
    val colors = FluidTheme.colors
    val prefs = FluidTheme.prefs
    val spec = FluidTheme.glassSpec(level)

    val borderWidth = when {
        prefs.increasedContrast -> 1.75.dp
        isSelected -> 1.5.dp
        else -> 1.dp
    }

    val strokeBrush = remember(spec, isSelected, accentColor, colors) {
        if (isSelected) {
            val active = accentColor ?: colors.accent
            Brush.verticalGradient(
                listOf(
                    lerp(active, Color.White, if (colors.isDark) 0.22f else 0.10f).copy(alpha = 0.95f),
                    active.copy(alpha = 0.75f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    spec.strokeTop,
                    spec.strokeBottom
                )
            )
        }
    }

    val backgroundBrush = remember(spec, isSelected, accentColor, colors, prefs.reduceTransparency) {
        if (prefs.reduceTransparency) {
            Brush.verticalGradient(listOf(spec.fillColor, spec.fillColor))
        } else if (isSelected) {
            val active = accentColor ?: colors.accent
            val topTint = lerp(spec.fillColor, active, if (colors.isDark) 0.30f else 0.20f)
            val bottomTint = lerp(spec.fillColor, active, if (colors.isDark) 0.16f else 0.10f)
            Brush.verticalGradient(listOf(topTint, bottomTint))
        } else {
            val sheen = if (colors.isDark) 0.08f else 0.14f
            Brush.verticalGradient(
                0.0f to spec.fillColor.copy(alpha = (spec.fillColor.alpha + sheen).coerceAtMost(1f)),
                0.40f to spec.fillColor,
                1.0f to spec.fillColor
            )
        }
    }

    val shadowElevation = if (prefs.reduceTransparency) 4.dp else 8.dp
    val spotColor = if (isSelected) {
        (accentColor ?: colors.accent).copy(alpha = 0.35f)
    } else if (colors.isDark) {
        Color(0x80000000)
    } else {
        Color(0x24000000)
    }
    val ambientColor = if (colors.isDark) Color(0x66000000) else Color(0x14000000)

    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = ambientColor,
            spotColor = spotColor
        )
        .background(backgroundBrush, shape)
        .border(borderWidth, strokeBrush, shape)
        .clip(shape)
}

/**
 * ColorOS 17 Living Flow interactive modifier:
 * - Springs scale to [pressScale] with asymmetric squash-and-stretch physics
 * - Bubbly rubber-band stretching (+9% along primary drag axis, -4.5% cross axis) when [enableStretch] is true
 * - Luminous expanding Bubbly Ripple Bloom centered at the pointer position
 * - Light haptic tick on press
 */
@Composable
fun Modifier.fluidInteractive(
    enabled: Boolean = true,
    pressScale: Float = 0.96f,
    enableStretch: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier {
    val haptic = LocalHapticFeedback.current
    val isReducedMotion = FluidTheme.isReducedMotion
    val isDark = FluidTheme.colors.isDark

    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    val currentPressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1f,
        animationSpec = if (isPressed) {
            FluidSprings.press(isReducedMotion)
        } else {
            FluidSprings.release(isReducedMotion)
        },
        label = "fluidPressScale"
    )

    // Asymmetric squash-and-stretch on press: widens slightly along X and compresses along Y
    val squashX by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isReducedMotion) 1.035f else 1.0f,
        animationSpec = if (isPressed) FluidSprings.press(isReducedMotion) else FluidSprings.release(isReducedMotion),
        label = "fluidSquashX"
    )
    val squashY by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isReducedMotion) 0.965f else 1.0f,
        animationSpec = if (isPressed) FluidSprings.press(isReducedMotion) else FluidSprings.release(isReducedMotion),
        label = "fluidSquashY"
    )

    // Expanding Bubbly Ripple Bloom: radius springs from 0.35f to 1.25f on press
    val glowRadiusFraction by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1.25f else 0.35f,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "fluidGlowRadiusFraction"
    )

    // Touch Glow alpha: 22% light / 18% dark on press, fades out over 280ms on release
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) {
            if (isDark) 0.18f else 0.22f
        } else {
            0f
        },
        animationSpec = if (isPressed) {
            tween(durationMillis = 40)
        } else {
            tween(durationMillis = 280)
        },
        label = "fluidTouchGlow"
    )

    // Calculate stretch factors along primary drag axis (+9% primary, -4.5% cross axis)
    val (stretchX, stretchY) = remember(dragDelta, enableStretch, isReducedMotion) {
        if (!enableStretch || isReducedMotion || dragDelta == Offset.Zero) {
            1f to 1f
        } else {
            val absX = abs(dragDelta.x)
            val absY = abs(dragDelta.y)
            val primary = max(absX, absY)
            val factor = (primary / 160f).coerceIn(0f, 1f)
            if (absX >= absY) {
                (1f + 0.09f * factor) to (1f - 0.045f * factor)
            } else {
                (1f - 0.045f * factor) to (1f + 0.09f * factor)
            }
        }
    }

    val animatedStretchX by animateFloatAsState(
        targetValue = stretchX,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "fluidStretchX"
    )
    val animatedStretchY by animateFloatAsState(
        targetValue = stretchY,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "fluidStretchY"
    )

    val pointerModifier = Modifier.pointerInput(enabled, isReducedMotion) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            touchOffset = down.position
            dragDelta = Offset.Zero
            isPressed = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            val pointerId = down.id
            var insideBounds = true

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || change.isConsumed) {
                    break
                }
                if (!change.pressed) {
                    insideBounds = change.position.x in 0f..size.width.toFloat() &&
                            change.position.y in 0f..size.height.toFloat()
                    if (insideBounds) {
                        onClick?.invoke()
                    }
                    break
                } else {
                    touchOffset = change.position
                    if (enableStretch) {
                        dragDelta = change.position - down.position
                    }
                }
            }
            isPressed = false
            dragDelta = Offset.Zero
        }
    }

    val semanticsModifier = if (onClick != null) {
        Modifier.semantics {
            role = Role.Button
        }
    } else {
        Modifier
    }

    val accentColor = FluidTheme.colors.accent
    val bloomTint = remember(accentColor) {
        lerp(Color.White, accentColor.copy(alpha = 0.35f), 0.40f)
    }

    return this
        .then(semanticsModifier)
        .then(pointerModifier)
        .graphicsLayer {
            scaleX = currentPressScale * squashX * animatedStretchX
            scaleY = currentPressScale * squashY * animatedStretchY
        }
        .drawWithContent {
            drawContent()
            if (glowAlpha > 0.001f) {
                val baseGlowRadius = (size.minDimension * 0.95f).coerceAtLeast(100f)
                val glowRadius = baseGlowRadius * glowRadiusFraction
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bloomTint.copy(alpha = glowAlpha),
                            accentColor.copy(alpha = glowAlpha * 0.50f),
                            Color.Transparent
                        ),
                        center = touchOffset,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = touchOffset
                )
            }
        }
}

/**
 * ColorOS 17 progressive blur / scrim ramp:
 * Fades smoothly from [colors.bgBase] (88% alpha) to transparent over [height]
 * so floating chrome never has an abrupt hard edge.
 */
@Composable
fun Modifier.fluidProgressiveBlur(
    fromTop: Boolean = true,
    height: Dp = 56.dp
): Modifier {
    val bgBase = FluidTheme.colors.bgBase
    val heightPx = with(LocalDensity.current) { height.toPx() }

    return this.drawBehind {
        val baseAlpha = bgBase.copy(alpha = 0.88f)
        val brush = if (fromTop) {
            Brush.verticalGradient(
                0.0f to baseAlpha,
                0.35f to baseAlpha.copy(alpha = 0.62f),
                0.65f to baseAlpha.copy(alpha = 0.30f),
                0.90f to baseAlpha.copy(alpha = 0.08f),
                1.0f to Color.Transparent,
                startY = 0f,
                endY = heightPx.coerceAtMost(size.height)
            )
        } else {
            val startY = (size.height - heightPx).coerceAtLeast(0f)
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.10f to baseAlpha.copy(alpha = 0.08f),
                0.35f to baseAlpha.copy(alpha = 0.30f),
                0.65f to baseAlpha.copy(alpha = 0.62f),
                1.0f to baseAlpha,
                startY = startY,
                endY = size.height
            )
        }
        drawRect(brush = brush)
    }
}

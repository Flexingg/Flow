package com.flow.todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive

data class FluidMenuAction(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onSelect: () -> Unit
)

/**
 * ColorOS 17 Inset Grouped Card:
 * - FluidShapes.lg (24.dp) rounded corners
 * - surfaceSolid in light mode, surfaceRaised with subtle contour glow in dark mode
 * - 16.dp inner content padding
 * - Optional accentGlowColor for highlighted/selected states
 * - Elastic animateContentSize with FluidSprings.morph
 */
@Composable
fun FluidInsetCard(
    modifier: Modifier = Modifier,
    accentGlowColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FluidTheme.colors
    val isDark = colors.isDark

    val backgroundModifier = if (accentGlowColor != null) {
        Modifier
            .shadow(
                elevation = 6.dp,
                shape = FluidShapes.lg,
                spotColor = accentGlowColor.copy(alpha = 0.25f)
            )
            .background(if (isDark) colors.surfaceRaised else colors.surfaceSolid, FluidShapes.lg)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        accentGlowColor.copy(alpha = 0.65f),
                        accentGlowColor.copy(alpha = 0.25f)
                    )
                ),
                FluidShapes.lg
            )
            .clip(FluidShapes.lg)
    } else if (isDark) {
        Modifier
            .shadow(
                elevation = 4.dp,
                shape = FluidShapes.lg,
                spotColor = Color(0x66000000)
            )
            .background(colors.surfaceRaised, FluidShapes.lg)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.03f)
                    )
                ),
                FluidShapes.lg
            )
            .clip(FluidShapes.lg)
    } else {
        Modifier
            .shadow(
                elevation = 3.dp,
                shape = FluidShapes.lg,
                ambientColor = Color(0x0A000000),
                spotColor = Color(0x14000000)
            )
            .background(colors.surfaceSolid, FluidShapes.lg)
            .border(1.dp, colors.divider, FluidShapes.lg)
            .clip(FluidShapes.lg)
    }

    Column(
        modifier = modifier
            .then(backgroundModifier)
            .animateContentSize(animationSpec = FluidSprings.morph(FluidTheme.isReducedMotion))
            .padding(16.dp),
        content = content
    )
}

/**
 * ColorOS 17 Pill Button:
 * - Height 48.dp, FluidShapes.full (pill)
 * - Filled accent when isPrimary, glass.thin tonal pill when secondary
 * - Interactive bubbly spring press feedback (pressScale = 0.90f)
 * - Animated background color and Material You accent glow
 */
@Composable
fun FluidPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion

    val targetBgColor = when {
        !enabled -> if (isPrimary) colors.accent.copy(alpha = 0.35f) else Color.Transparent
        isPrimary -> colors.accent
        else -> Color.Transparent
    }
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = FluidSprings.gentle(isReducedMotion),
        label = "pillButtonBgColor"
    )

    val buttonModifier = if (isPrimary) {
        Modifier
            .fluidInteractive(
                enabled = enabled,
                pressScale = 0.92f,
                onClick = onClick
            )
            .height(48.dp)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = FluidShapes.full,
                spotColor = colors.accent.copy(alpha = 0.45f)
            )
            .background(animatedBgColor, FluidShapes.full)
            .border(
                1.25.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (enabled) 0.48f else 0.12f),
                        Color.White.copy(alpha = if (enabled) 0.18f else 0.05f)
                    )
                ),
                FluidShapes.full
            )
            .clip(FluidShapes.full)
    } else {
        Modifier
            .fluidInteractive(
                enabled = enabled,
                pressScale = 0.92f,
                onClick = onClick
            )
            .height(48.dp)
            .fluidGlass(
                level = GlassLevel.THIN,
                shape = FluidShapes.full
            )
    }

    val contentColor = when {
        !enabled -> colors.textTertiary
        isPrimary -> Color.White
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .then(buttonModifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * ColorOS 17 Filter Chip:
 * - FluidShapes.full pill chip
 * - fluidGlass(GlassLevel.THIN) with active accent contour glow when selected
 * - Immediate bubbly pop scaling (1.04f) and elastic animateContentSize
 */
@Composable
fun FluidFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    leadingIcon: ImageVector? = null
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val effectiveAccent = accentColor ?: colors.accent

    val textColor = if (selected) effectiveAccent else colors.textPrimary
    val iconColor = if (selected) effectiveAccent else colors.textSecondary

    val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1.0f,
        animationSpec = FluidSprings.bubbly(isReducedMotion),
        label = "filterChipScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
            }
            .fluidInteractive(
                pressScale = 0.94f,
                onClick = onClick
            )
            .height(36.dp)
            .fluidGlass(
                level = GlassLevel.THIN,
                shape = FluidShapes.full,
                isSelected = selected,
                accentColor = effectiveAccent
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.animateContentSize(animationSpec = FluidSprings.bubbly(isReducedMotion)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * ColorOS 17 Luminous Progress Bar:
 * - 8.dp track, rounded full
 * - Luminous gradient fill animated with FluidSprings.release
 * - Breathing pulse halo on the glowing comet head circle
 */
@Composable
fun FluidGlowingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val effectiveAccent = accentColor ?: colors.accent

    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "fluidProgressBarProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "progressBarPulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cometPulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp), // Extra headroom for the comet glow
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val trackHeightPx = with(LocalDensity.current) { 8.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val centerY = size.height / 2f
                    val topY = centerY - (trackHeightPx / 2f)
                    val bottomY = centerY + (trackHeightPx / 2f)

                    // Track background
                    val trackColor = if (colors.isDark) {
                        Color.White.copy(alpha = 0.12f)
                    } else {
                        Color.Black.copy(alpha = 0.08f)
                    }
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, topY),
                        size = androidx.compose.ui.geometry.Size(size.width, trackHeightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f)
                    )

                    val fillWidth = size.width * animatedProgress
                    if (fillWidth > 1f) {
                        // Luminous gradient fill
                        val gradientBrush = Brush.horizontalGradient(
                            listOf(
                                effectiveAccent.copy(alpha = 0.80f),
                                effectiveAccent
                            ),
                            startX = 0f,
                            endX = fillWidth
                        )
                        drawRoundRect(
                            brush = gradientBrush,
                            topLeft = Offset(0f, topY),
                            size = androidx.compose.ui.geometry.Size(fillWidth, trackHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f)
                        )

                        // Glowing "Comet" highlight circle at the leading edge with breathing pulse
                        val effectivePulse = if (isReducedMotion) 1f else pulseFraction
                        val cometCenter = Offset(fillWidth, centerY)
                        val cometOuterGlowRadius = 8.dp.toPx() * effectivePulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    effectiveAccent.copy(alpha = 0.70f),
                                    effectiveAccent.copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                center = cometCenter,
                                radius = cometOuterGlowRadius
                            ),
                            radius = cometOuterGlowRadius,
                            center = cometCenter
                        )

                        // Comet core
                        val cometCoreRadius = 4.5.dp.toPx() * ((effectivePulse - 1f) * 0.3f + 1f)
                        drawCircle(
                            color = Color.White,
                            radius = cometCoreRadius,
                            center = cometCenter
                        )
                    }
                }
        )
    }
}

/**
 * ColorOS 17 Fluid Stretchy Switch:
 * - 52x32.dp pill switch
 * - Thumb stretches horizontally (1.34f width) and squishes vertically (0.86f height) during press
 * - checkFraction rebounds bouncy with FluidSprings.bubbly
 */
@Composable
fun FluidStretchySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    var isPressed by remember { mutableStateOf(false) }

    val checkFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = FluidSprings.bubbly(isReducedMotion),
        label = "switchCheckFraction"
    )

    // Stretch width to 1.34f and compress height to 0.86f when pressed
    val targetStretchWidth = if (isPressed && !isReducedMotion) 1.34f else 1.0f
    val targetStretchHeight = if (isPressed && !isReducedMotion) 0.86f else 1.0f

    val stretchScaleWidth by animateFloatAsState(
        targetValue = targetStretchWidth,
        animationSpec = FluidSprings.press(isReducedMotion),
        label = "switchThumbStretchWidth"
    )
    val stretchScaleHeight by animateFloatAsState(
        targetValue = targetStretchHeight,
        animationSpec = FluidSprings.press(isReducedMotion),
        label = "switchThumbStretchHeight"
    )

    val trackColor = if (checked) {
        colors.accent
    } else {
        if (colors.isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)
    }

    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbBaseSize = 24.dp
    val padding = 4.dp

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(FluidShapes.full)
            .background(trackColor)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (checked) 0.35f else 0.15f),
                        Color.Transparent
                    )
                ),
                FluidShapes.full
            )
            .semantics { role = Role.Switch }
            .pointerInput(checked, isReducedMotion) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        onCheckedChange(!checked)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val maxOffsetDp = trackWidth - thumbBaseSize - (padding * 2)
        val currentOffsetDp = padding + (maxOffsetDp * checkFraction)
        val thumbWidthDp = thumbBaseSize * stretchScaleWidth
        val thumbHeightDp = thumbBaseSize * stretchScaleHeight

        Box(
            modifier = Modifier
                .offset(x = currentOffsetDp)
                .size(width = thumbWidthDp, height = thumbHeightDp)
                .shadow(
                    elevation = 3.dp,
                    shape = FluidShapes.full,
                    spotColor = Color(0x3D000000)
                )
                .clip(FluidShapes.full)
                .background(Color.White)
        )
    }
}

/**
 * ColorOS 17 Stretchy Context Menu:
 * - Regular glass floating menu with FluidShapes.lg (24.dp)
 * - Scales smoothly from 0.85f -> 1.0f via FluidSprings.morph
 * - Press-and-slide item selection with haptic tick on item hover
 */
@Composable
fun FluidStretchyContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<FluidMenuAction>,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    val menuScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.85f,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "contextMenuScale"
    )

    val menuAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "contextMenuAlpha"
    )

    var hoveredIndex by remember { mutableIntStateOf(-1) }

    Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, 0),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                    alpha = menuAlpha
                }
                .widthIn(min = 200.dp, max = 260.dp)
                .fluidGlass(
                    level = GlassLevel.REGULAR,
                    shape = FluidShapes.lg
                )
                .padding(vertical = 8.dp, horizontal = 6.dp)
                .pointerInput(actions) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val itemHeightPx = 48.dp.toPx()
                            val index = ((offset.y - 8.dp.toPx()) / itemHeightPx)
                                .toInt()
                                .coerceIn(0, actions.size - 1)
                            if (index != hoveredIndex) {
                                hoveredIndex = index
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            if (hoveredIndex in actions.indices) {
                                actions[hoveredIndex].onSelect()
                            }
                            onDismissRequest()
                            hoveredIndex = -1
                        },
                        onDragCancel = {
                            hoveredIndex = -1
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val itemHeightPx = 48.dp.toPx()
                            val index = ((change.position.y - 8.dp.toPx()) / itemHeightPx)
                                .toInt()
                                .coerceIn(0, actions.size - 1)
                            if (index != hoveredIndex) {
                                hoveredIndex = index
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    actions.forEachIndexed { index, action ->
                        val isHovered = hoveredIndex == index
                        val itemColor = if (action.isDestructive) colors.danger else colors.textPrimary

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(FluidShapes.md)
                                .background(
                                    if (isHovered) {
                                        if (colors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    action.onSelect()
                                    onDismissRequest()
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = itemColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = action.label,
                                color = itemColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

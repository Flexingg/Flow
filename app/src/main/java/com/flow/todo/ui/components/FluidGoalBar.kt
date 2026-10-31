package com.flow.todo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.EvaluatedGoal
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive
import kotlin.math.roundToInt

/**
 * Living Flow Goal Bar:
 * Sculpted liquid-glass meter capsule bar with 5-Stop Spectrum fill,
 * comparator badges, and telemetry readouts.
 */

@Composable
fun FluidGoalSpectrumBar(
    evaluatedGoals: List<EvaluatedGoal>,
    goalMomentumProgress: Float,
    shizukuReady: Boolean = false,
    syncSystemColors: Boolean = false,
    onOpenGoalsTab: () -> Unit = {},
    onOpenGoalCreator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isDark = colors.isDark
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    val clampedProgress = goalMomentumProgress.coerceIn(0f, 1f)
    val momentumColor = MomentumSpectrum.colorForProgress(clampedProgress)
    val stageLabel = MomentumSpectrum.stageLabel(clampedProgress)
    val seedHex = MomentumSpectrum.seedHexForProgress(clampedProgress)

    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "goalMomentumBarProgress"
    )

    val pulseTransition = rememberInfiniteTransition(label = "orbPulseTransition")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulseAlpha"
    )

    FluidInsetCard(
        modifier = modifier.fillMaxWidth(),
        accentGlowColor = momentumColor.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Orb + Title + Badge + "+ Goal" chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { onOpenGoalsTab() }
                ) {
                    // Glowing spectrum orb
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = CircleShape,
                                spotColor = momentumColor
                            )
                            .clip(CircleShape)
                            .background(momentumColor.copy(alpha = if (isReducedMotion) 0.85f else pulseAlpha))
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )

                    Text(
                        text = "GOAL MOMENTUM BAR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        color = momentumColor
                    )

                    // Met count badge
                    val metCount = evaluatedGoals.count { it.isCompleted }
                    val totalCount = evaluatedGoals.size
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(momentumColor.copy(alpha = 0.12f))
                            .border(1.dp, momentumColor.copy(alpha = 0.30f), FluidShapes.full)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (totalCount == 0) "0 Goals" else "$metCount/$totalCount Met",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = momentumColor
                        )
                    }
                }

                // "+ Goal" Glass Pill Chip
                Box(
                    modifier = Modifier
                        .fluidInteractive(
                            pressScale = 0.92f,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenGoalCreator()
                            }
                        )
                        .clip(FluidShapes.full)
                        .background(colors.accent.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    colors.accent.copy(alpha = 0.60f),
                                    colors.accent.copy(alpha = 0.25f)
                                )
                            ),
                            FluidShapes.full
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Goal",
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Goal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.sm))

            // Full-width Sculpted 5-Stop Spectrum Gauge Bar
            val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            val spectrumStops = MomentumSpectrum.stops

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clickable { onOpenGoalsTab() },
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val density = LocalDensity.current
                val cometRadiusPx = with(density) { 5.dp.toPx() }
                val cometOuterGlowPx = with(density) { 8.dp.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val trackHeight = size.height * 0.70f
                            val topY = (size.height - trackHeight) / 2f
                            val radius = CornerRadius(trackHeight / 2f)

                            // 1. Inactive background track
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(0f, topY),
                                size = Size(size.width, trackHeight),
                                cornerRadius = radius
                            )

                            // 2. Liquid filled spectrum
                            val fillWidth = (size.width * animatedProgress).coerceIn(0f, size.width)
                            if (fillWidth > 2f) {
                                val fillBrush = Brush.horizontalGradient(
                                    colors = spectrumStops,
                                    startX = 0f,
                                    endX = size.width
                                )
                                drawRoundRect(
                                    brush = fillBrush,
                                    topLeft = Offset(0f, topY),
                                    size = Size(fillWidth, trackHeight),
                                    cornerRadius = radius
                                )

                                // 3. Glowing Comet Head
                                val cometCenter = Offset(fillWidth.coerceAtMost(size.width - cometRadiusPx), size.height / 2f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(
                                            momentumColor.copy(alpha = 0.65f),
                                            momentumColor.copy(alpha = 0.15f),
                                            Color.Transparent
                                        ),
                                        center = cometCenter,
                                        radius = cometOuterGlowPx
                                    ),
                                    radius = cometOuterGlowPx,
                                    center = cometCenter
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = cometRadiusPx,
                                    center = cometCenter
                                )
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Stage Caption & Hex Code Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                Text(
                    text = "#$seedHex",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = momentumColor
                )
            }

            Spacer(modifier = Modifier.height(FluidSpacing.sm))

            // Mini Goal Capsule Meters Strip (or empty state prompt)
            if (evaluatedGoals.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    evaluatedGoals.forEach { evaluatedGoal ->
                        MiniGoalCapsule(
                            evaluatedGoal = evaluatedGoal,
                            onClick = onOpenGoalsTab
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FluidShapes.md)
                        .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f))
                        .border(1.dp, colors.divider, FluidShapes.md)
                        .clickable { onOpenGoalCreator() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Add a Task, Health Connect, or App Usage goal to drive your daily color spectrum",
                            fontSize = 12.sp,
                            color = colors.textTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact horizontal capsule representing a single goal inside FluidGoalSpectrumBar.
 */
@Composable
private fun MiniGoalCapsule(
    evaluatedGoal: EvaluatedGoal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isDark = colors.isDark
    val goal = evaluatedGoal.goal

    val categoryIcon = when (goal.category) {
        GoalCategory.TASK_COMPLETION -> Icons.Rounded.TaskAlt
        GoalCategory.HEALTH_CONNECT -> Icons.Rounded.Favorite
        GoalCategory.APP_USAGE -> Icons.Rounded.Timer
    }

    val barColor = when {
        evaluatedGoal.isOverLimit -> MomentumSpectrum.Red
        evaluatedGoal.isCompleted -> MomentumSpectrum.Green
        else -> MomentumSpectrum.colorForProgress(evaluatedGoal.momentumContribution)
    }

    val fraction = evaluatedGoal.barFillFraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .widthIn(min = 120.dp, max = 160.dp)
            .fluidInteractive(pressScale = 0.95f, onClick = onClick)
            .fluidGlass(GlassLevel.THIN, FluidShapes.md, isSelected = evaluatedGoal.isCompleted, accentColor = barColor)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = goal.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = evaluatedGoal.formattedProgressText,
                fontSize = 10.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Mini 4.dp meter bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(FluidShapes.full)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(FluidShapes.full)
                        .background(barColor)
                )
            }
        }
    }
}

/**
 * Individual full-width Goal Bar card for GoalsScreen.kt.
 * Sculpted liquid-glass telemetry bar with category bubble, comparator pill,
 * live telemetry readout, and 12.dp liquid telemetry bar with tick marks.
 */
@Composable
fun FluidGoalMeterCard(
    evaluatedGoal: EvaluatedGoal,
    projectColorHex: Long? = null,
    onDeleteGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isDark = colors.isDark
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current
    val goal = evaluatedGoal.goal

    val categoryIcon = when (goal.category) {
        GoalCategory.TASK_COMPLETION -> Icons.Rounded.TaskAlt
        GoalCategory.HEALTH_CONNECT -> Icons.Rounded.Favorite
        GoalCategory.APP_USAGE -> Icons.Rounded.Timer
    }

    val barColor = when {
        evaluatedGoal.isOverLimit -> MomentumSpectrum.Red
        evaluatedGoal.isCompleted -> MomentumSpectrum.Green
        else -> MomentumSpectrum.colorForProgress(evaluatedGoal.momentumContribution)
    }

    val fillFraction = evaluatedGoal.barFillFraction.coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(
        targetValue = fillFraction,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "goalBarFill_${goal.id}"
    )

    FluidInsetCard(
        modifier = modifier.fillMaxWidth(),
        accentGlowColor = barColor.copy(alpha = if (evaluatedGoal.isCompleted || evaluatedGoal.isOverLimit) 0.35f else 0.15f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Row: Category Icon Bubble + Title/Subtitle + Comparator + Trailing Status / Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon bubble
                val bubbleColor = projectColorHex?.let { Color(it) } ?: barColor
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 4.dp, shape = CircleShape, spotColor = bubbleColor.copy(alpha = 0.35f))
                        .clip(CircleShape)
                        .background(bubbleColor.copy(alpha = if (isDark) 0.22f else 0.14f))
                        .border(1.dp, bubbleColor.copy(alpha = 0.40f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = goal.category.label,
                        tint = bubbleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Subtitle + Comparator badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = goal.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Comparator pill badge (Goal >= or Limit <=)
                        val comparatorText = if (goal.comparison == GoalComparison.AT_LEAST) "Goal ≥" else "Limit ≤"
                        Box(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .background(barColor.copy(alpha = 0.12f))
                                .border(0.75.dp, barColor.copy(alpha = 0.30f), FluidShapes.full)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = comparatorText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = barColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = evaluatedGoal.subtitleBadgeText,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Pill: Met, Under Limit, Over Limit, or %
                val statusText = when {
                    evaluatedGoal.isOverLimit -> "Over Limit !"
                    evaluatedGoal.isCompleted && goal.comparison == GoalComparison.AT_LEAST -> "Goal Met ✓"
                    evaluatedGoal.isCompleted && goal.comparison == GoalComparison.AT_MOST -> "Under Limit ✓"
                    else -> "${(fillFraction * 100).roundToInt()}%"
                }

                val statusBg = when {
                    evaluatedGoal.isOverLimit -> MomentumSpectrum.Red
                    evaluatedGoal.isCompleted -> MomentumSpectrum.Green
                    else -> colors.accent
                }

                Box(
                    modifier = Modifier
                        .clip(FluidShapes.full)
                        .background(statusBg.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .border(1.dp, statusBg.copy(alpha = 0.35f), FluidShapes.full)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusBg
                    )
                }

                // Delete Icon Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteGoal()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Goal",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry Status Readout Row (e.g. "8,420 ≥ 10,000 steps")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = evaluatedGoal.formattedProgressText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                Text(
                    text = if (goal.comparison == GoalComparison.AT_LEAST) {
                        if (evaluatedGoal.isCompleted) "Completed" else "In Progress"
                    } else {
                        if (evaluatedGoal.isOverLimit) "Exceeded Limit" else "Within Limit"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sculpted 12.dp Liquid Telemetry Bar with tick marks at 25%, 50%, 75%
            val trackBgColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            val tickLineColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val density = LocalDensity.current
                val cometRadiusPx = with(density) { 4.5.dp.toPx() }
                val cometOuterGlowPx = with(density) { 7.5.dp.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val trackHeight = size.height
                            val cornerRadius = CornerRadius(trackHeight / 2f)

                            // 1. Background capsule track
                            drawRoundRect(
                                color = trackBgColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width, trackHeight),
                                cornerRadius = cornerRadius
                            )

                            // 2. Liquid filled gradient bar
                            val fillWidth = (size.width * animatedFill).coerceIn(0f, size.width)
                            if (fillWidth > 1f) {
                                val fillBrush = Brush.horizontalGradient(
                                    colors = listOf(
                                        barColor.copy(alpha = 0.70f),
                                        barColor
                                    ),
                                    startX = 0f,
                                    endX = fillWidth
                                )
                                drawRoundRect(
                                    brush = fillBrush,
                                    topLeft = Offset.Zero,
                                    size = Size(fillWidth, trackHeight),
                                    cornerRadius = cornerRadius
                                )

                                // 3. Glowing Comet Dot at leading edge
                                val cometCenter = Offset(fillWidth.coerceAtMost(size.width - cometRadiusPx), trackHeight / 2f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(
                                            barColor.copy(alpha = 0.65f),
                                            barColor.copy(alpha = 0.20f),
                                            Color.Transparent
                                        ),
                                        center = cometCenter,
                                        radius = cometOuterGlowPx
                                    ),
                                    radius = cometOuterGlowPx,
                                    center = cometCenter
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = cometRadiusPx,
                                    center = cometCenter
                                )
                            }

                            // 4. Tick marks at 25%, 50%, 75%
                            val tickFractions = floatArrayOf(0.25f, 0.50f, 0.75f)
                            for (fraction in tickFractions) {
                                val tickX = size.width * fraction
                                drawLine(
                                    color = tickLineColor,
                                    start = Offset(tickX, 2f),
                                    end = Offset(tickX, trackHeight - 2f),
                                    strokeWidth = 1.2f
                                )
                            }
                        }
                )
            }
        }
    }
}

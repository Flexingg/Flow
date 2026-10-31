package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.AppUsageSnapshot
import com.flow.todo.domain.model.EvaluatedGoal
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.GoalPeriod
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.ShizukuStatus
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidGoalMeterCard
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive
import kotlin.math.roundToInt

private enum class GoalFilterTab(val label: String) {
    ALL("All Goals"),
    TASK("Task Milestones"),
    HEALTH("Health Connect"),
    APP("App Usage Limits")
}

/**
 * Living Flow Goals Screen:
 * Complete goals destination featuring Hero Goal Momentum Spectrum Card,
 * category filters, active sculpted goal meter cards, permissions & live telemetry status,
 * and 1-tap quick presets.
 */
@Composable
fun GoalsScreen(
    evaluatedGoals: List<EvaluatedGoal>,
    projects: List<Project>,
    goalMomentumProgress: Float,
    healthSnapshot: HealthDailySnapshot,
    appUsageSnapshot: AppUsageSnapshot,
    shizukuStatus: ShizukuStatus,
    syncSystemColorsWithShizuku: Boolean,
    onOpenGoalCreator: () -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddPresetGoal: (
        title: String,
        category: GoalCategory,
        comparison: GoalComparison,
        targetValue: Double,
        period: GoalPeriod,
        projectId: String?,
        healthMetric: HealthMetricType?,
        targetPackageName: String?,
        targetAppName: String?,
        appUsageMetric: AppUsageMetricType?
    ) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onRefreshMetrics: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isDark = colors.isDark
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    var selectedFilter by remember { mutableStateOf(GoalFilterTab.ALL) }

    val clampedProgress = goalMomentumProgress.coerceIn(0f, 1f)
    val momentumAccentColor = MomentumSpectrum.colorForProgress(clampedProgress)
    val progressPercent = (clampedProgress * 100).roundToInt()
    val stageLabel = MomentumSpectrum.stageLabel(clampedProgress)
    val seedHex = MomentumSpectrum.seedHexForProgress(clampedProgress)

    val completedGoalsCount = evaluatedGoals.count { it.isCompleted }
    val totalGoalsCount = evaluatedGoals.size

    val filteredGoals = remember(evaluatedGoals, selectedFilter) {
        when (selectedFilter) {
            GoalFilterTab.ALL -> evaluatedGoals
            GoalFilterTab.TASK -> evaluatedGoals.filter { it.goal.category == GoalCategory.TASK_COMPLETION }
            GoalFilterTab.HEALTH -> evaluatedGoals.filter { it.goal.category == GoalCategory.HEALTH_CONNECT }
            GoalFilterTab.APP -> evaluatedGoals.filter { it.goal.category == GoalCategory.APP_USAGE }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = FluidSpacing.lg)
    ) {
        item {
            Spacer(modifier = Modifier.height(FluidSpacing.sm))
        }

        // 1. HERO GOAL MOMENTUM SPECTRUM CARD
        item {
            FluidInsetCard(
                modifier = Modifier.fillMaxWidth(),
                accentGlowColor = momentumAccentColor.copy(alpha = 0.40f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .shadow(elevation = 6.dp, shape = CircleShape, spotColor = momentumAccentColor)
                                    .clip(CircleShape)
                                    .background(momentumAccentColor)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                            )
                            Text(
                                text = "LIVING GOAL SPECTRUM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = momentumAccentColor
                            )
                        }

                        // Percentage Badge with Bouncy Slide
                        Box(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .background(momentumAccentColor.copy(alpha = 0.12f))
                                .border(1.dp, momentumAccentColor.copy(alpha = 0.28f), FluidShapes.full)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            AnimatedContent(
                                targetState = progressPercent,
                                transitionSpec = {
                                    if (isReducedMotion) {
                                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                    } else {
                                        val direction = if (targetState > initialState) 1 else -1
                                        (slideInVertically(
                                            animationSpec = FluidSprings.release(false),
                                            initialOffsetY = { 16 * direction }
                                        ) + scaleIn(
                                            initialScale = 0.80f,
                                            animationSpec = FluidSprings.release(false)
                                        ) + fadeIn(tween(150))).togetherWith(
                                            slideOutVertically(
                                                animationSpec = FluidSprings.release(false),
                                                targetOffsetY = { -16 * direction }
                                            ) + scaleOut(
                                                targetScale = 0.80f,
                                                animationSpec = FluidSprings.release(false)
                                            ) + fadeOut(tween(150))
                                        )
                                    }
                                },
                                label = "goalMomentumPercentage"
                            ) { percent ->
                                Text(
                                    text = "$percent%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = momentumAccentColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Expressive Met / Total Numerals
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        AnimatedContent(
                            targetState = completedGoalsCount,
                            transitionSpec = {
                                if (isReducedMotion) {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                } else {
                                    val direction = if (targetState > initialState) 1 else -1
                                    (slideInVertically(
                                        animationSpec = FluidSprings.release(false),
                                        initialOffsetY = { 28 * direction }
                                    ) + scaleIn(
                                        initialScale = 0.75f,
                                        animationSpec = FluidSprings.release(false)
                                    ) + fadeIn(tween(150))).togetherWith(
                                        slideOutVertically(
                                            animationSpec = FluidSprings.release(false),
                                            targetOffsetY = { -28 * direction }
                                        ) + scaleOut(
                                            targetScale = 0.75f,
                                            animationSpec = FluidSprings.release(false)
                                        ) + fadeOut(tween(150))
                                    )
                                }
                            },
                            label = "completedGoalsNumeral"
                        ) { count ->
                            Text(
                                text = "$count",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp,
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            text = " / $totalGoalsCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(FluidSpacing.sm))
                        Text(
                            text = "goals achieved today",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    Text(
                        text = when {
                            totalGoalsCount == 0 -> "No active goals yet. Add task milestones, biometric targets, or app usage limits below."
                            completedGoalsCount >= totalGoalsCount -> "All goals fulfilled! Living in full flow ✨"
                            else -> "Every step, task, and mindful app limit moves your Living Spectrum closer to pure Emerald."
                        },
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // Luminous Progress Bar with comet
                    FluidGlowingProgressBar(
                        progress = clampedProgress,
                        accentColor = momentumAccentColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // 5-Stop Spectrum Dots + Stage Label & Shizuku Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val spectrumDots = MomentumSpectrum.stops
                            val activeDotIndex = when {
                                clampedProgress >= 0.80f -> 4
                                clampedProgress >= 0.60f -> 3
                                clampedProgress >= 0.40f -> 2
                                clampedProgress >= 0.20f -> 1
                                else -> 0
                            }

                            spectrumDots.forEachIndexed { idx, dotColor ->
                                val isActive = idx == activeDotIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 12.dp else 8.dp)
                                        .then(
                                            if (isActive) Modifier.shadow(elevation = 6.dp, shape = CircleShape, spotColor = dotColor)
                                            else Modifier
                                        )
                                        .clip(CircleShape)
                                        .background(if (isActive) dotColor else dotColor.copy(alpha = 0.35f))
                                        .border(
                                            width = if (isActive) 1.5.dp else 0.5.dp,
                                            color = if (isActive) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = stageLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                        }

                        // Shizuku pill chip
                        val isSyncActive = syncSystemColorsWithShizuku && shizukuStatus.isReady
                        val shizukuText = if (isSyncActive) "OS Sync: Active (#$seedHex)" else "OS UI Palette →"
                        Box(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .background(
                                    if (isSyncActive) colors.success.copy(alpha = 0.12f)
                                    else colors.accent.copy(alpha = 0.10f)
                                )
                                .border(
                                    1.dp,
                                    if (isSyncActive) colors.success.copy(alpha = 0.28f)
                                    else colors.accent.copy(alpha = 0.24f),
                                    FluidShapes.full
                                )
                                .fluidInteractive(pressScale = 0.94f, onClick = onOpenSettings)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSyncActive) colors.success else colors.accent)
                                )
                                Text(
                                    text = shizukuText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSyncActive) colors.success else colors.accent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // "+ Create Goal" Primary Button
                    FluidPillButton(
                        text = "+ Create Goal",
                        onClick = onOpenGoalCreator,
                        isPrimary = true,
                        leadingIcon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. CATEGORY FILTER CHIPS
        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoalFilterTab.entries.forEach { tab ->
                    val count = when (tab) {
                        GoalFilterTab.ALL -> evaluatedGoals.size
                        GoalFilterTab.TASK -> evaluatedGoals.count { it.goal.category == GoalCategory.TASK_COMPLETION }
                        GoalFilterTab.HEALTH -> evaluatedGoals.count { it.goal.category == GoalCategory.HEALTH_CONNECT }
                        GoalFilterTab.APP -> evaluatedGoals.count { it.goal.category == GoalCategory.APP_USAGE }
                    }
                    val label = "${tab.label} ($count)"
                    FluidFilterChip(
                        label = label,
                        selected = selectedFilter == tab,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFilter = tab
                        }
                    )
                }
            }
        }

        // 3. ACTIVE GOAL BARS LIST
        item {
            Spacer(modifier = Modifier.height(FluidSpacing.sm))
        }

        if (filteredGoals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fluidGlass(GlassLevel.THIN, FluidShapes.lg)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TrackChanges,
                            contentDescription = null,
                            tint = colors.accent.copy(alpha = 0.50f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (totalGoalsCount == 0) "No Goals Created Yet" else "No Goals in this Category",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Tap '+ Create Goal' above or pick a 1-tap preset below to start your Living Flow meter.",
                            fontSize = 13.sp,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        } else {
            items(filteredGoals, key = { it.goal.id }) { evaluatedGoal ->
                val projectColorHex = evaluatedGoal.goal.projectId?.let { pid ->
                    projects.firstOrNull { it.id == pid }?.colorHex
                }
                FluidGoalMeterCard(
                    evaluatedGoal = evaluatedGoal,
                    projectColorHex = projectColorHex,
                    onDeleteGoal = { onDeleteGoal(evaluatedGoal.goal.id) },
                    modifier = Modifier.padding(bottom = FluidSpacing.sm)
                )
            }
        }

        // 4. PERMISSIONS & LIVE TELEMETRY STATUS ROW
        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
            FluidInsetCard(
                modifier = Modifier.fillMaxWidth(),
                accentGlowColor = colors.accent.copy(alpha = 0.20f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PERMISSIONS & TELEMETRY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                        }

                        // Refresh icon button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .fluidGlass(GlassLevel.THIN, CircleShape)
                                .fluidInteractive(pressScale = 0.90f, onClick = onRefreshMetrics),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Telemetry",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Health Connect Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Health Connect",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${healthSnapshot.grantedPermissionCount}/${healthSnapshot.totalPermissionCount} biometrics granted",
                                fontSize = 11.sp,
                                color = if (healthSnapshot.hasAllPermissions) colors.success else colors.textSecondary
                            )
                        }

                        if (!healthSnapshot.hasAllPermissions) {
                            FluidFilterChip(
                                label = "Grant Access",
                                selected = false,
                                onClick = onRequestHealthPermissions,
                                leadingIcon = Icons.Rounded.Favorite,
                                accentColor = MomentumSpectrum.Green
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    // Horizontal live health readings strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HealthMetricType.entries.forEach { metric ->
                            val currentVal = healthSnapshot.valueFor(metric)
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 88.dp)
                                    .fluidGlass(GlassLevel.THIN, FluidShapes.md)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = metric.label,
                                        fontSize = 10.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = metric.formatValue(currentVal),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = metric.unit,
                                        fontSize = 9.sp,
                                        color = colors.textTertiary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // App Usage Access Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Usage Access (PACKAGE_USAGE_STATS)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (appUsageSnapshot.hasUsagePermission) "Granted • Live screen time & launch counters active"
                                else "Permission needed to track daily screen-time minutes & app launches",
                                fontSize = 11.sp,
                                color = if (appUsageSnapshot.hasUsagePermission) colors.success else colors.textTertiary
                            )
                        }

                        if (!appUsageSnapshot.hasUsagePermission) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FluidFilterChip(
                                label = "Grant Usage",
                                selected = false,
                                onClick = onRequestUsagePermission,
                                leadingIcon = Icons.Rounded.Timer,
                                accentColor = MomentumSpectrum.Purple
                            )
                        }
                    }
                }
            }
        }

        // 5. 1-TAP QUICK GOAL PRESETS
        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
            FluidInsetCard(
                modifier = Modifier.fillMaxWidth(),
                accentGlowColor = colors.accent.copy(alpha = 0.20f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "1-TAP QUICK GOAL PRESETS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.textTertiary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Task Milestone Presets
                    Text(
                        text = "TASK MILESTONES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FluidFilterChip(
                            label = "+ 3 Tasks Today (General)",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Complete ≥ 3 Tasks (Daily)",
                                    GoalCategory.TASK_COMPLETION,
                                    GoalComparison.AT_LEAST,
                                    3.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            },
                            leadingIcon = Icons.Rounded.TaskAlt
                        )

                        FluidFilterChip(
                            label = "+ 15 Tasks This Week",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Complete ≥ 15 Tasks (Weekly)",
                                    GoalCategory.TASK_COMPLETION,
                                    GoalComparison.AT_LEAST,
                                    15.0,
                                    GoalPeriod.WEEKLY,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            },
                            leadingIcon = Icons.Rounded.TaskAlt
                        )

                        // If user has a project, add preset for the first project
                        projects.firstOrNull()?.let { project ->
                            FluidFilterChip(
                                label = "+ 3 ${project.name} Tasks Today",
                                selected = false,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAddPresetGoal(
                                        "Complete ≥ 3 ${project.name} Tasks (Daily)",
                                        GoalCategory.TASK_COMPLETION,
                                        GoalComparison.AT_LEAST,
                                        3.0,
                                        GoalPeriod.DAILY,
                                        project.id,
                                        null,
                                        null,
                                        null,
                                        null
                                    )
                                },
                                leadingIcon = Icons.Rounded.Folder,
                                accentColor = Color(project.colorHex)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // App Usage Limit Presets
                    Text(
                        text = "APP USAGE LIMITS & TARGETS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FluidFilterChip(
                            label = "Instagram ≤ 30 min/day",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Instagram ≤ 30 min/day",
                                    GoalCategory.APP_USAGE,
                                    GoalComparison.AT_MOST,
                                    30.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    null,
                                    "com.instagram.android",
                                    "Instagram",
                                    AppUsageMetricType.ACTIVE_MINUTES
                                )
                            },
                            leadingIcon = Icons.Rounded.Timer,
                            accentColor = MomentumSpectrum.Purple
                        )

                        FluidFilterChip(
                            label = "TikTok ≤ 5 opens/day",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "TikTok ≤ 5 opens/day",
                                    GoalCategory.APP_USAGE,
                                    GoalComparison.AT_MOST,
                                    5.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    null,
                                    "com.zhiliaoapp.musically",
                                    "TikTok",
                                    AppUsageMetricType.LAUNCH_COUNT
                                )
                            },
                            leadingIcon = Icons.Rounded.Timer,
                            accentColor = MomentumSpectrum.Purple
                        )

                        FluidFilterChip(
                            label = "YouTube ≤ 45 min/day",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "YouTube ≤ 45 min/day",
                                    GoalCategory.APP_USAGE,
                                    GoalComparison.AT_MOST,
                                    45.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    null,
                                    "com.google.android.youtube",
                                    "YouTube",
                                    AppUsageMetricType.ACTIVE_MINUTES
                                )
                            },
                            leadingIcon = Icons.Rounded.Timer,
                            accentColor = MomentumSpectrum.Red
                        )

                        FluidFilterChip(
                            label = "Duolingo ≥ 15 min/day",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Duolingo ≥ 15 min/day",
                                    GoalCategory.APP_USAGE,
                                    GoalComparison.AT_LEAST,
                                    15.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    null,
                                    "com.duolingo",
                                    "Duolingo",
                                    AppUsageMetricType.ACTIVE_MINUTES
                                )
                            },
                            leadingIcon = Icons.Rounded.Timer,
                            accentColor = MomentumSpectrum.Green
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Health Connect Biometric Presets
                    Text(
                        text = "HEALTH CONNECT BIOMETRICS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FluidFilterChip(
                            label = "Steps ≥ 10,000",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Steps ≥ 10,000 steps",
                                    GoalCategory.HEALTH_CONNECT,
                                    GoalComparison.AT_LEAST,
                                    10000.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    HealthMetricType.STEPS,
                                    null,
                                    null,
                                    null
                                )
                            },
                            leadingIcon = Icons.Rounded.DirectionsRun,
                            accentColor = MomentumSpectrum.Green
                        )

                        FluidFilterChip(
                            label = "Exercise ≥ 30 min",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Exercise Time ≥ 30 min",
                                    GoalCategory.HEALTH_CONNECT,
                                    GoalComparison.AT_LEAST,
                                    30.0,
                                    GoalPeriod.DAILY,
                                    null,
                                    HealthMetricType.EXERCISE_MINUTES,
                                    null,
                                    null,
                                    null
                                )
                            },
                            leadingIcon = Icons.Rounded.DirectionsRun,
                            accentColor = MomentumSpectrum.Green
                        )

                        FluidFilterChip(
                            label = "Sleep ≥ 7.5 hrs",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Sleep Duration ≥ 7.5 hrs",
                                    GoalCategory.HEALTH_CONNECT,
                                    GoalComparison.AT_LEAST,
                                    7.5,
                                    GoalPeriod.DAILY,
                                    null,
                                    HealthMetricType.SLEEP_HOURS,
                                    null,
                                    null,
                                    null
                                )
                            },
                            leadingIcon = Icons.Rounded.Favorite,
                            accentColor = MomentumSpectrum.Blue
                        )

                        FluidFilterChip(
                            label = "Water ≥ 2.5 L",
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddPresetGoal(
                                    "Hydration ≥ 2.5 L",
                                    GoalCategory.HEALTH_CONNECT,
                                    GoalComparison.AT_LEAST,
                                    2.5,
                                    GoalPeriod.DAILY,
                                    null,
                                    HealthMetricType.HYDRATION_LITERS,
                                    null,
                                    null,
                                    null
                                )
                            },
                            leadingIcon = Icons.Rounded.Favorite,
                            accentColor = MomentumSpectrum.Blue
                        )
                    }
                }
            }
        }

        // Bottom headroom for Floating Island Bar
        item {
            Spacer(modifier = Modifier.height(115.dp))
        }
    }
}

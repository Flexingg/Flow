package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SelfImprovement
import com.flow.todo.domain.model.ContributionChartSummary
import com.flow.todo.domain.model.EvaluatedGoal
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.GtdContext
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.ShizukuStatus
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.model.UpcomingDayGroup
import com.flow.todo.ui.components.FluidContributionChart
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidGoalSpectrumBar
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

@Composable
fun TodayNextScreen(
    todayTasks: List<TaskItem>,
    nextActionTasks: List<TaskItem>,
    upcomingWeekDays: List<UpcomingDayGroup> = emptyList(),
    laterScheduledTasks: List<TaskItem> = emptyList(),
    upcomingWeekTotalCount: Int = 0,
    projects: List<Project>,
    completedTodayCount: Int,
    totalTodayTargetCount: Int,
    dailyProgress: Float = 0f,
    evaluatedGoals: List<EvaluatedGoal> = emptyList(),
    healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
    shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    syncSystemColorsWithShizuku: Boolean = false,
    contributionChart: ContributionChartSummary = ContributionChartSummary(),
    onRequestHealthPermissions: () -> Unit = {},
    onRefreshHealthStats: () -> Unit = {},
    onAddQuickHealthGoal: (HealthMetricType, Double) -> Unit = { _, _ -> },
    onOpenGoalsTab: () -> Unit = {},
    onOpenGoalCreator: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleComplete: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onMoveBucket: (String, GtdBucket) -> Unit,
    onAssignToday: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onToggleSubtask: (String, String) -> Unit = { _, _ -> },
    onAddSubtask: (String, String) -> Unit = { _, _ -> },
    onDeleteSubtask: (String, String) -> Unit = { _, _ -> },
    selectedContextTag: String? = null,
    onSelectContextFilter: (String?) -> Unit = {},
    onStartFocus: (taskId: String, taskTitle: String) -> Unit = { _, _ -> },
    isFocusActive: Boolean = false,
    focusRemainingFormatted: String = "25:00",
    onOpenFocusSheet: () -> Unit = {},
    onOpenMorningRitual: () -> Unit = {},
    onOpenEveningRitual: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current
    var selectedMode by remember { mutableIntStateOf(0) } // 0: Today & Next, 1: Next Week Queue

    val effectiveProgress = dailyProgress.coerceIn(0f, 1f)
    val progressPercent = (effectiveProgress * 100).toInt()
    val momentumAccentColor = MomentumSpectrum.colorForProgress(effectiveProgress)

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

        // Living Flow Glass Segmented Mode Switcher
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fluidGlass(GlassLevel.THIN, FluidShapes.full)
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mode 0: Today & Next
                    val isMode0 = selectedMode == 0
                    val text0Color by animateColorAsState(
                        targetValue = if (isMode0) FluidTheme.colors.accent else FluidTheme.colors.textSecondary,
                        animationSpec = FluidSprings.morph(isReducedMotion),
                        label = "mode0TextColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fluidInteractive(pressScale = 0.95f, onClick = { selectedMode = 0 })
                            .clip(FluidShapes.full)
                            .background(
                                if (isMode0) FluidTheme.colors.accent.copy(alpha = if (FluidTheme.colors.isDark) 0.24f else 0.16f)
                                else Color.Transparent
                            )
                            .then(
                                if (isMode0) {
                                    Modifier.border(
                                        1.5.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                FluidTheme.colors.accent.copy(alpha = 0.90f),
                                                FluidTheme.colors.accent.copy(alpha = 0.60f)
                                            )
                                        ),
                                        FluidShapes.full
                                    )
                                } else Modifier
                            )
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Today,
                                contentDescription = null,
                                tint = text0Color,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Today & Next",
                                fontSize = 13.sp,
                                fontWeight = if (isMode0) FontWeight.SemiBold else FontWeight.Medium,
                                color = text0Color
                            )
                        }
                    }

                    // Mode 1: Next Week Queue ($upcomingWeekTotalCount)
                    val isMode1 = selectedMode == 1
                    val text1Color by animateColorAsState(
                        targetValue = if (isMode1) FluidTheme.colors.accent else FluidTheme.colors.textSecondary,
                        animationSpec = FluidSprings.morph(isReducedMotion),
                        label = "mode1TextColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fluidInteractive(pressScale = 0.95f, onClick = { selectedMode = 1 })
                            .clip(FluidShapes.full)
                            .background(
                                if (isMode1) FluidTheme.colors.accent.copy(alpha = if (FluidTheme.colors.isDark) 0.24f else 0.16f)
                                else Color.Transparent
                            )
                            .then(
                                if (isMode1) {
                                    Modifier.border(
                                        1.5.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                FluidTheme.colors.accent.copy(alpha = 0.90f),
                                                FluidTheme.colors.accent.copy(alpha = 0.60f)
                                            )
                                        ),
                                        FluidShapes.full
                                    )
                                } else Modifier
                            )
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Upcoming,
                                contentDescription = null,
                                tint = text1Color,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Next Week Queue ($upcomingWeekTotalCount)",
                                fontSize = 13.sp,
                                fontWeight = if (isMode1) FontWeight.SemiBold else FontWeight.Medium,
                                color = text1Color,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(FluidSpacing.md))
        }

        // GTD Context Filter Chips (@work, @home, @errands, @gym, @calls)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluidFilterChip(
                    label = "All Contexts",
                    selected = selectedContextTag == null,
                    onClick = { onSelectContextFilter(null) }
                )
                GtdContext.ALL.forEach { ctx ->
                    val isSelected = selectedContextTag == ctx.tag
                    FluidFilterChip(
                        label = ctx.label,
                        selected = isSelected,
                        accentColor = Color(ctx.colorHex),
                        onClick = {
                            if (isSelected) onSelectContextFilter(null)
                            else onSelectContextFilter(ctx.tag)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(FluidSpacing.sm))
        }

        // Bouncy animated mode content transition
        item {
            AnimatedContent(
                targetState = selectedMode,
                transitionSpec = {
                    if (isReducedMotion) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    } else {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = FluidSprings.morph(false)
                        ) { (it * 0.15f).toInt() * direction } + fadeIn(
                            animationSpec = tween(180)
                        ) + scaleIn(
                            initialScale = 0.95f,
                            animationSpec = FluidSprings.morph(false)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = FluidSprings.morph(false)
                            ) { (-it * 0.15f).toInt() * direction } + fadeOut(
                                animationSpec = tween(150)
                            ) + scaleOut(
                                targetScale = 0.95f,
                                animationSpec = FluidSprings.morph(false)
                            )
                        )
                    }
                },
                label = "livingFlowModeTransition"
            ) { mode ->
                if (mode == 0) {
                    // MODE 0: Today & Next
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FluidSpacing.lg)
                    ) {
                        // 1. DUE TODAY & FLAGGED (MOST VISIBLE / AT TOP)
                        FluidInsetCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentGlowColor = momentumAccentColor.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(momentumAccentColor.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Today,
                                                contentDescription = null,
                                                tint = momentumAccentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Due Today & Flagged",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = FluidTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = if (totalTodayTargetCount > 0) "$completedTodayCount of $totalTodayTargetCount completed • $progressPercent%"
                                                else "Daily actions & priority commitments",
                                                fontSize = 11.sp,
                                                color = FluidTheme.colors.textSecondary
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .background(momentumAccentColor.copy(alpha = 0.12f))
                                            .border(1.dp, momentumAccentColor.copy(alpha = 0.28f), FluidShapes.full)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        AnimatedContent(
                                            targetState = todayTasks.size,
                                            transitionSpec = {
                                                if (isReducedMotion) {
                                                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                                } else {
                                                    (scaleIn(initialScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeIn(tween(150)))
                                                        .togetherWith(
                                                            scaleOut(targetScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeOut(tween(150))
                                                        )
                                                }
                                            },
                                            label = "todayTasksCount"
                                        ) { count ->
                                            Text(
                                                text = "$count",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = momentumAccentColor
                                            )
                                        }
                                    }
                                }

                                if (totalTodayTargetCount > 0) {
                                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                                    FluidGlowingProgressBar(
                                        progress = effectiveProgress,
                                        accentColor = momentumAccentColor,
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(FluidTheme.colors.accent.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Today,
                                            contentDescription = null,
                                            tint = FluidTheme.colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Due Today & Flagged",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textPrimary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(FluidTheme.colors.divider)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    AnimatedContent(
                                        targetState = todayTasks.size,
                                        transitionSpec = {
                                            if (isReducedMotion) {
                                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                            } else {
                                                (scaleIn(initialScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeIn(tween(150)))
                                                    .togetherWith(
                                                        scaleOut(targetScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeOut(tween(150))
                                                    )
                                            }
                                        },
                                        label = "todayTasksCount"
                                    ) { count ->
                                        Text(
                                            text = "$count",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = FluidTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(FluidSpacing.sm))

                            if (todayTasks.isEmpty()) {
                                Text(
                                    text = "No scheduled or flagged tasks for today.",
                                    fontSize = 14.sp,
                                    color = FluidTheme.colors.textTertiary,
                                    modifier = Modifier.padding(vertical = FluidSpacing.sm, horizontal = FluidSpacing.xs)
                                )
                            } else {
                                todayTasks.forEachIndexed { index, task ->
                                    key(task.id) {
                                        this@Column.AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(FluidSprings.morph(isReducedMotion)) +
                                                    expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                                    scaleIn(
                                                        initialScale = 0.95f,
                                                        animationSpec = FluidSprings.release(isReducedMotion)
                                                    ),
                                            exit = fadeOut(FluidSprings.morph(isReducedMotion)) +
                                                    shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                                    scaleOut(
                                                        targetScale = 0.95f,
                                                        animationSpec = FluidSprings.release(isReducedMotion)
                                                    )
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        thickness = 1.dp,
                                                        color = FluidTheme.colors.divider,
                                                        modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                                    )
                                                }
                                                TaskRow(
                                                    task = task,
                                                    project = projects.find { it.id == task.projectId },
                                                    onToggleComplete = { onToggleComplete(task.id) },
                                                    onToggleFlag = { onToggleFlag(task.id) },
                                                    onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                                    onAssignToday = { onAssignToday(task.id) },
                                                    onDelete = { onDeleteTask(task.id) },
                                                    healthSnapshot = healthSnapshot,
                                                    onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                                    onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                                    onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                                    onStartFocus = { onStartFocus(task.id, task.title) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Next Actions (GTD Horizon)
                        FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(FluidTheme.colors.success.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            tint = FluidTheme.colors.success,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Next Actions",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FluidTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = "GTD Horizon • Ready to execute",
                                            fontSize = 11.sp,
                                            color = FluidTheme.colors.textTertiary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(FluidTheme.colors.divider)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    AnimatedContent(
                                        targetState = nextActionTasks.size,
                                        transitionSpec = {
                                            if (isReducedMotion) {
                                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                            } else {
                                                (scaleIn(initialScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeIn(tween(150)))
                                                    .togetherWith(
                                                        scaleOut(targetScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeOut(tween(150))
                                                    )
                                            }
                                        },
                                        label = "nextActionsCount"
                                    ) { count ->
                                        Text(
                                            text = "$count",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = FluidTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(FluidSpacing.sm))

                            if (nextActionTasks.isEmpty()) {
                                Text(
                                    text = "No pending next actions. Clarify inbox items or add next steps.",
                                    fontSize = 14.sp,
                                    color = FluidTheme.colors.textTertiary,
                                    modifier = Modifier.padding(vertical = FluidSpacing.sm, horizontal = FluidSpacing.xs)
                                )
                            } else {
                                nextActionTasks.forEachIndexed { index, task ->
                                    key(task.id) {
                                        this@Column.AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(FluidSprings.morph(isReducedMotion)) +
                                                    expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                                    scaleIn(
                                                        initialScale = 0.95f,
                                                        animationSpec = FluidSprings.release(isReducedMotion)
                                                    ),
                                            exit = fadeOut(FluidSprings.morph(isReducedMotion)) +
                                                    shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                                    scaleOut(
                                                        targetScale = 0.95f,
                                                        animationSpec = FluidSprings.release(isReducedMotion)
                                                    )
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        thickness = 1.dp,
                                                        color = FluidTheme.colors.divider,
                                                        modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                                    )
                                                }
                                                TaskRow(
                                                    task = task,
                                                    project = projects.find { it.id == task.projectId },
                                                    onToggleComplete = { onToggleComplete(task.id) },
                                                    onToggleFlag = { onToggleFlag(task.id) },
                                                    onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                                    onAssignToday = { onAssignToday(task.id) },
                                                    onDelete = { onDeleteTask(task.id) },
                                                    healthSnapshot = healthSnapshot,
                                                    onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                                    onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                                    onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                                    onStartFocus = { onStartFocus(task.id, task.title) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. DAILY MOMENTUM HERO CARD
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
                                    Text(
                                        text = "DAILY MOMENTUM",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color = momentumAccentColor
                                    )

                                    // Percentage Badge with Bouncy Slide + ScaleIn
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
                                            label = "momentumPercentage"
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

                                // Large expressive numeral showing completed / total with bouncy vertical roll
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    AnimatedContent(
                                        targetState = completedTodayCount,
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
                                        label = "completedTodayNumeral"
                                    ) { count ->
                                        Text(
                                            text = "$count",
                                            fontSize = 40.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = (-0.5).sp,
                                            color = FluidTheme.colors.textPrimary
                                        )
                                    }

                                    Text(
                                        text = " / $totalTodayTargetCount",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = FluidTheme.colors.textTertiary,
                                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(FluidSpacing.sm))
                                    Text(
                                        text = "completed today",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = FluidTheme.colors.textSecondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(FluidSpacing.xs))

                                Text(
                                    text = when {
                                        totalTodayTargetCount == 0 -> "No scheduled commitments today. Pick an action from your horizon."
                                        completedTodayCount >= totalTodayTargetCount -> "All daily targets fulfilled • Pure Living Flow ✨"
                                        else -> "Keep your momentum going. Focus on the next immediate physical action."
                                    },
                                    fontSize = 13.sp,
                                    color = FluidTheme.colors.textSecondary
                                )

                                Spacer(modifier = Modifier.height(FluidSpacing.md))

                                // Luminous comet progress bar
                                FluidGlowingProgressBar(
                                    progress = effectiveProgress,
                                    accentColor = momentumAccentColor,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(FluidSpacing.md))

                                // 5-Stop Daily Momentum Spectrum Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 5 Glowing Spectrum Dots + Stage Label
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val spectrumDots = MomentumSpectrum.stops
                                        val activeDotIndex = when {
                                            effectiveProgress >= 0.80f -> 4
                                            effectiveProgress >= 0.60f -> 3
                                            effectiveProgress >= 0.40f -> 2
                                            effectiveProgress >= 0.20f -> 1
                                            else -> 0
                                        }

                                        spectrumDots.forEachIndexed { idx, dotColor ->
                                            val isActive = idx == activeDotIndex
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isActive) 12.dp else 8.dp)
                                                    .then(
                                                        if (isActive) {
                                                            Modifier.shadow(elevation = 6.dp, shape = CircleShape, spotColor = dotColor)
                                                        } else Modifier
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
                                            text = MomentumSpectrum.stageLabel(effectiveProgress),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FluidTheme.colors.textSecondary
                                        )
                                    }

                                    // Shizuku System UI Sync status pill chip
                                    val isSyncActive = syncSystemColorsWithShizuku && shizukuStatus.isReady
                                    val shizukuPillText = if (isSyncActive) "System UI Sync: Active" else "Sync System UI Colors →"
                                    Box(
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .background(
                                                if (isSyncActive) FluidTheme.colors.success.copy(alpha = 0.12f)
                                                else FluidTheme.colors.accent.copy(alpha = 0.10f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSyncActive) FluidTheme.colors.success.copy(alpha = 0.28f)
                                                else FluidTheme.colors.accent.copy(alpha = 0.24f),
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
                                                    .background(if (isSyncActive) FluidTheme.colors.success else FluidTheme.colors.accent)
                                            )
                                            Text(
                                                text = shizukuPillText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSyncActive) FluidTheme.colors.success else FluidTheme.colors.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. DEDICATED LIVING GOAL BAR
                        FluidGoalSpectrumBar(
                            evaluatedGoals = evaluatedGoals,
                            goalMomentumProgress = effectiveProgress,
                            shizukuReady = shizukuStatus.isReady,
                            syncSystemColors = syncSystemColorsWithShizuku,
                            onOpenGoalsTab = onOpenGoalsTab,
                            onOpenGoalCreator = onOpenGoalCreator,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 5. GTD DAILY RITUALS & DEEP FLOW FOCUS MODE CARD
                        FluidInsetCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentGlowColor = momentumAccentColor.copy(alpha = 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(FluidTheme.colors.accent.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isFocusActive) Icons.Rounded.Headphones else Icons.Rounded.SelfImprovement,
                                            contentDescription = null,
                                            tint = FluidTheme.colors.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isFocusActive) "Deep Flow Focus Active" else "GTD Rituals & Focus",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FluidTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = if (isFocusActive) "$focusRemainingFormatted remaining • Ambient soundscape" else "Morning alignment & sunset review",
                                            fontSize = 11.sp,
                                            color = FluidTheme.colors.textSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FluidPillButton(
                                        text = if (isFocusActive) "Timer" else "Focus",
                                        onClick = onOpenFocusSheet,
                                        isPrimary = isFocusActive,
                                        modifier = Modifier.height(32.dp)
                                    )
                                    FluidPillButton(
                                        text = "Morning",
                                        onClick = onOpenMorningRitual,
                                        isPrimary = false,
                                        modifier = Modifier.height(32.dp)
                                    )
                                    FluidPillButton(
                                        text = "Sunset",
                                        onClick = onOpenEveningRitual,
                                        isPrimary = false,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }

                        // 6. CONTRIBUTION HEATMAP CHART
                        FluidContributionChart(
                            summary = contributionChart,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 7. UP NEXT THIS WEEK PREVIEW
                        val upcomingPreviewTasks = remember(upcomingWeekDays) {
                            upcomingWeekDays.flatMap { day ->
                                day.tasks.map { task -> day to task }
                            }.take(3)
                        }

                        FluidInsetCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentGlowColor = FluidTheme.colors.accent.copy(alpha = 0.20f)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(FluidTheme.colors.accent.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DateRange,
                                                contentDescription = null,
                                                tint = FluidTheme.colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Up Next This Week",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = FluidTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = "Next 7 days preview • $upcomingWeekTotalCount queued",
                                                fontSize = 11.sp,
                                                color = FluidTheme.colors.textTertiary
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                            .border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$upcomingWeekTotalCount",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FluidTheme.colors.accent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                                if (upcomingPreviewTasks.isEmpty()) {
                                    Text(
                                        text = "Your upcoming week is open. No scheduled commitments or recurring tasks.",
                                        fontSize = 14.sp,
                                        color = FluidTheme.colors.textTertiary,
                                        modifier = Modifier.padding(vertical = FluidSpacing.sm, horizontal = FluidSpacing.xs)
                                    )
                                } else {
                                    upcomingPreviewTasks.forEachIndexed { index, (dayGroup, task) ->
                                        key("preview_${task.id}_${dayGroup.epochDay}") {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        thickness = 1.dp,
                                                        color = FluidTheme.colors.divider,
                                                        modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                                    )
                                                }
                                                TaskRow(
                                                    task = task,
                                                    project = projects.find { it.id == task.projectId },
                                                    onToggleComplete = { onToggleComplete(task.id) },
                                                    onToggleFlag = { onToggleFlag(task.id) },
                                                    onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                                    onAssignToday = { onAssignToday(task.id) },
                                                    onDelete = { onDeleteTask(task.id) },
                                                    healthSnapshot = healthSnapshot,
                                                    onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                                    onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                                    onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                                    onStartFocus = { onStartFocus(task.id, task.title) }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(FluidSpacing.md))

                                FluidPillButton(
                                    text = "View Full 7-Day Queue →",
                                    onClick = { selectedMode = 1 },
                                    isPrimary = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Floating nav bar clearance spacer
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                } else {
                    // MODE 1: Next Week Queue
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FluidSpacing.lg)
                    ) {
                        // 7-Day Horizon Queue Header Card
                        FluidInsetCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentGlowColor = FluidTheme.colors.accent.copy(alpha = 0.35f)
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
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(FluidTheme.colors.accent.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Upcoming,
                                                contentDescription = null,
                                                tint = FluidTheme.colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = "7-DAY HORIZON QUEUE",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            color = FluidTheme.colors.accent
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                            .border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$upcomingWeekTotalCount queued",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FluidTheme.colors.accent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                                Text(
                                    text = "Scheduled tasks and recurring patterns (Daily, Weekly, Monthly) projected across the next 7 days for clear mental space.",
                                    fontSize = 13.sp,
                                    color = FluidTheme.colors.textSecondary
                                )
                            }
                        }

                        // Daily groups for the next 7 days
                        upcomingWeekDays.forEach { dayGroup ->
                            val hasGlow = dayGroup.isTomorrow || dayGroup.tasks.isNotEmpty()
                            val glowColor = when {
                                dayGroup.isTomorrow -> FluidTheme.colors.accent.copy(alpha = 0.30f)
                                dayGroup.tasks.isNotEmpty() -> FluidTheme.colors.accent.copy(alpha = 0.15f)
                                else -> null
                            }

                            FluidInsetCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentGlowColor = glowColor
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (dayGroup.isTomorrow) FluidTheme.colors.accent.copy(alpha = 0.15f)
                                                        else FluidTheme.colors.divider
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (dayGroup.isTomorrow) Icons.Rounded.Today else Icons.Rounded.DateRange,
                                                    contentDescription = null,
                                                    tint = if (dayGroup.isTomorrow) FluidTheme.colors.accent else FluidTheme.colors.textSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = dayGroup.dayTitle,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (dayGroup.isTomorrow) FluidTheme.colors.accent else FluidTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = dayGroup.dateSubtitle,
                                                    fontSize = 12.sp,
                                                    color = FluidTheme.colors.textTertiary
                                                )
                                            }
                                        }

                                        // Task count pill badge
                                        Box(
                                            modifier = Modifier
                                                .clip(FluidShapes.full)
                                                .background(
                                                    if (dayGroup.tasks.isNotEmpty()) FluidTheme.colors.accent.copy(alpha = 0.12f)
                                                    else FluidTheme.colors.divider
                                                )
                                                .then(
                                                    if (dayGroup.tasks.isNotEmpty()) {
                                                        Modifier.border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${dayGroup.tasks.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (dayGroup.tasks.isNotEmpty()) FluidTheme.colors.accent else FluidTheme.colors.textTertiary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                                    if (dayGroup.tasks.isEmpty()) {
                                        Text(
                                            text = "Clear day — open for flow",
                                            fontSize = 13.sp,
                                            color = FluidTheme.colors.textTertiary,
                                            modifier = Modifier.padding(vertical = FluidSpacing.xs, horizontal = FluidSpacing.xs)
                                        )
                                    } else {
                                        dayGroup.tasks.forEachIndexed { index, task ->
                                            key("${dayGroup.epochDay}_${task.id}") {
                                                this@Column.AnimatedVisibility(
                                                    visible = true,
                                                    enter = fadeIn(FluidSprings.morph(isReducedMotion)) +
                                                            expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)),
                                                    exit = fadeOut(FluidSprings.morph(isReducedMotion)) +
                                                            shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion))
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        if (index > 0) {
                                                            HorizontalDivider(
                                                                thickness = 1.dp,
                                                                color = FluidTheme.colors.divider,
                                                                modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                                            )
                                                        }
                                                        TaskRow(
                                                            task = task,
                                                            project = projects.find { it.id == task.projectId },
                                                            onToggleComplete = { onToggleComplete(task.id) },
                                                            onToggleFlag = { onToggleFlag(task.id) },
                                                            onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                                            onAssignToday = { onAssignToday(task.id) },
                                                            onDelete = { onDeleteTask(task.id) },
                                                            healthSnapshot = healthSnapshot,
                                                            onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                                            onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                                            onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                                            onStartFocus = { onStartFocus(task.id, task.title) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Beyond Next Week section if tasks exist
                        if (laterScheduledTasks.isNotEmpty()) {
                            FluidInsetCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentGlowColor = FluidTheme.colors.warning.copy(alpha = 0.15f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(FluidTheme.colors.warning.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Schedule,
                                                    contentDescription = null,
                                                    tint = FluidTheme.colors.warning,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Beyond Next Week",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = FluidTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = "Future commitments further on the horizon",
                                                    fontSize = 11.sp,
                                                    color = FluidTheme.colors.textTertiary
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(FluidShapes.full)
                                                .background(FluidTheme.colors.warning.copy(alpha = 0.12f))
                                                .border(1.dp, FluidTheme.colors.warning.copy(alpha = 0.28f), FluidShapes.full)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${laterScheduledTasks.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = FluidTheme.colors.warning
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                                    laterScheduledTasks.forEachIndexed { index, task ->
                                        key("later_${task.id}") {
                                            this@Column.AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn(FluidSprings.morph(isReducedMotion)) +
                                                        expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)),
                                                exit = fadeOut(FluidSprings.morph(isReducedMotion)) +
                                                        shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion))
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    if (index > 0) {
                                                        HorizontalDivider(
                                                            thickness = 1.dp,
                                                            color = FluidTheme.colors.divider,
                                                            modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                                        )
                                                    }
                                                    TaskRow(
                                                        task = task,
                                                        project = projects.find { it.id == task.projectId },
                                                        onToggleComplete = { onToggleComplete(task.id) },
                                                        onToggleFlag = { onToggleFlag(task.id) },
                                                        onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                                        onAssignToday = { onAssignToday(task.id) },
                                                        onDelete = { onDeleteTask(task.id) },
                                                        healthSnapshot = healthSnapshot,
                                                        onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                                        onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                                        onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                                        onStartFocus = { onStartFocus(task.id, task.title) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating nav bar clearance spacer
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

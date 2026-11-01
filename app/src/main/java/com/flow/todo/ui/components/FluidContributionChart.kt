package com.flow.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.ContributionChartSummary
import com.flow.todo.domain.model.ContributionDayCell
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.MomentumSpectrum

private enum class ContributionSource(val label: String) {
    COMBINED("Combined Flow"),
    TASKS("Tasks Only"),
    GOALS("Goals & Momentum")
}

private val GITHUB_GREEN_DARK = listOf(
    Color(0xFF0E4429),
    Color(0xFF006D32),
    Color(0xFF26A641),
    Color(0xFF39D353)
)

private val GITHUB_GREEN_LIGHT = listOf(
    Color(0xFF9BE9A8),
    Color(0xFF40C463),
    Color(0xFF30A14E),
    Color(0xFF216E39)
)

/**
 * GitHub-Inspired Task & Goal Completion Contribution Heatmap Card.
 * Displays interactive 7xN contribution grid with telemetry streak badges,
 * filter sources, time ranges, palette toggles, day inspector, and intensity legend.
 */
@Composable
fun FluidContributionChart(
    summary: ContributionChartSummary,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isDark = colors.isDark
    val haptic = LocalHapticFeedback.current

    var selectedSource by remember { mutableStateOf(ContributionSource.COMBINED) }
    var selectedWeeksCount by remember { mutableIntStateOf(26) }
    var isGithubPalette by remember { mutableStateOf(false) }
    var selectedCell by remember { mutableStateOf<ContributionDayCell?>(null) }

    val scrollState = rememberScrollState()

    // Slice the weeks to the selected time range
    val visibleWeeks = remember(summary.weeks, selectedWeeksCount) {
        if (summary.weeks.isEmpty()) {
            emptyList()
        } else {
            summary.weeks.takeLast(selectedWeeksCount)
        }
    }

    // Scroll to the latest (rightmost) week whenever the week count or data changes
    LaunchedEffect(visibleWeeks.size) {
        if (visibleWeeks.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    fun resolveColor(level: Int, isFuture: Boolean): Color {
        if (isFuture) {
            return if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
        }
        val clampedLevel = level.coerceIn(0, 4)
        if (clampedLevel == 0) {
            return if (isDark) Color(0xFF161B22) else Color(0xFFEBEDF0)
        }
        return if (isGithubPalette) {
            val palette = if (isDark) GITHUB_GREEN_DARK else GITHUB_GREEN_LIGHT
            palette[(clampedLevel - 1).coerceIn(0, 3)]
        } else {
            MomentumSpectrum.colorForProgress(clampedLevel / 4f)
        }
    }

    FluidInsetCard(
        modifier = modifier.fillMaxWidth(),
        accentGlowColor = colors.accent.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. TOP HEADER & TITLE
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
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "TASK & GOAL CONTRIBUTIONS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = colors.accent
                    )
                }

                // Active days count chip
                Box(
                    modifier = Modifier
                        .clip(FluidShapes.full)
                        .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                        .border(1.dp, colors.divider.copy(alpha = 0.5f), FluidShapes.full)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${summary.activeDaysCount} active days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.sm))

            // 2. TELEMETRY BADGES ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badges = listOf(
                    "🔥 ${summary.currentStreakDays}d streak",
                    "🏆 ${summary.longestStreakDays}d best",
                    "✓ ${summary.totalTasksCompletedInWindow} tasks",
                    "🎯 ${summary.totalGoalsMetInWindow} goals"
                )

                badges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.04f))
                            .border(1.dp, colors.divider.copy(alpha = 0.6f), FluidShapes.full)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.md))

            // 3. INTERACTIVE FILTER CONTROLS
            // Row 1: Contribution Source (Combined Flow, Tasks Only, Goals & Momentum)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContributionSource.entries.forEach { src ->
                    val isSelected = selectedSource == src
                    FluidFilterChip(
                        label = src.label,
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedSource = src
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.xs))

            // Row 2: Time Range & Color Theme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Range chips: 16W, 26W, 52W (Year)
                listOf(16 to "16W", 26 to "26W", 52 to "52W (Year)").forEach { (weeks, label) ->
                    val isSelected = selectedWeeksCount == weeks
                    FluidFilterChip(
                        label = label,
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedWeeksCount = weeks
                        }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Palette toggle chip: Spectrum Colors vs GitHub Green
                FluidFilterChip(
                    label = if (isGithubPalette) "GitHub Green 🌿" else "Spectrum Colors 🌈",
                    selected = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isGithubPalette = !isGithubPalette
                    },
                    accentColor = if (isGithubPalette) {
                        if (isDark) Color(0xFF39D353) else Color(0xFF216E39)
                    } else {
                        colors.accent
                    }
                )
            }

            Spacer(modifier = Modifier.height(FluidSpacing.md))

            // 4. 7-ROW (Mon..Sun) × N-COLUMN GITHUB HEATMAP GRID
            val dayRowLabels = listOf("Mon", "", "Wed", "", "Fri", "", "Sun")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Left day-of-week labels aligned with the 7 rows (13.dp height, 3.5.dp spacing)
                Column(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    dayRowLabels.forEach { label ->
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 13.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textTertiary
                            )
                        }
                    }
                }

                // Horizontally scrollable heatmap grid
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    if (visibleWeeks.isEmpty()) {
                        // Empty fallback grid placeholder (16 columns of empty cells)
                        repeat(16) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                                repeat(7) {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(if (isDark) Color(0xFF161B22) else Color(0xFFEBEDF0))
                                            .border(
                                                0.5.dp,
                                                if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f),
                                                RoundedCornerShape(3.5.dp)
                                            )
                                    )
                                }
                            }
                        }
                    } else {
                        visibleWeeks.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                                week.forEach { cell ->
                                    val isSelected = selectedCell?.epochDay == cell.epochDay
                                    val isToday = cell.isToday
                                    val cellLevel = when (selectedSource) {
                                        ContributionSource.COMBINED -> cell.combinedLevel
                                        ContributionSource.TASKS -> cell.taskLevel
                                        ContributionSource.GOALS -> cell.goalLevel
                                    }.coerceIn(0, 4)

                                    val cellColor = resolveColor(cellLevel, cell.isFuture)

                                    val borderModifier = when {
                                        isSelected -> Modifier.border(
                                            1.25.dp,
                                            Color.White,
                                            RoundedCornerShape(3.5.dp)
                                        )
                                        isToday -> Modifier.border(
                                            1.25.dp,
                                            colors.accent,
                                            RoundedCornerShape(3.5.dp)
                                        )
                                        else -> Modifier.border(
                                            0.5.dp,
                                            if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f),
                                            RoundedCornerShape(3.5.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(cellColor)
                                            .then(borderModifier)
                                            .clickable(!cell.isFuture) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedCell = cell
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.md))

            // 5. BOTTOM SELECTED DAY INSPECTOR & GITHUB `Less [][][][][] More` LEGEND
            val activeCell = selectedCell
                ?: summary.cells.firstOrNull { it.isToday }
                ?: summary.cells.lastOrNull()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day stats inspector on the left
                val inspectorText = if (activeCell != null && activeCell.epochDay != 0L) {
                    "${activeCell.dateLabel}: ${activeCell.tasksCompleted} tasks • ${activeCell.goalsCompleted}/${activeCell.totalGoals} goals (${(activeCell.momentumScore * 100).toInt()}%)"
                } else {
                    "Tap any day cell to inspect momentum"
                }

                Text(
                    text = inspectorText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Less [][][][][] More legend on the right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Less",
                        fontSize = 10.sp,
                        color = colors.textTertiary
                    )

                    (0..4).forEach { lvl ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(resolveColor(lvl, isFuture = false))
                                .border(
                                    0.5.dp,
                                    if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                                    RoundedCornerShape(2.5.dp)
                                )
                        )
                    }

                    Text(
                        text = "More",
                        fontSize = 10.sp,
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}

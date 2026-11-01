package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.flow.todo.domain.model.DailyRitualType
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRitualSheet(
    ritualType: DailyRitualType,
    todayTasks: List<TaskItem>,
    inboxTasks: List<TaskItem>,
    rolloverTasks: List<TaskItem>,
    healthSnapshot: HealthDailySnapshot,
    dailyProgress: Float,
    completedTodayCount: Int,
    totalTodayTargetCount: Int,
    onAssignToday: (String) -> Unit,
    onMoveBucket: (String, GtdBucket) -> Unit,
    onToggleTaskComplete: (String) -> Unit,
    onCompleteRitual: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val accentColor = MomentumSpectrum.colorForProgress(dailyProgress)

    var selectedTop3 by remember {
        mutableStateOf(todayTasks.filter { it.isFlagged }.take(3).map { it.id }.toSet())
    }

    val todayFormatted = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FluidTheme.colors.bgBase.copy(alpha = if (FluidTheme.colors.isDark) 0.95f else 0.92f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = FluidSpacing.lg, vertical = FluidSpacing.md)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (ritualType == DailyRitualType.MORNING_WAKE) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay,
                            contentDescription = "Ritual",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (ritualType == DailyRitualType.MORNING_WAKE) "WAKE TO FLOW • MORNING" else "SUNSET REVIEW • EVENING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = accentColor
                        )
                        Text(
                            text = todayFormatted,
                            fontSize = 13.sp,
                            color = FluidTheme.colors.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FluidTheme.colors.divider)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = FluidTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.xl))

            if (ritualType == DailyRitualType.MORNING_WAKE) {
                // Morning Section 1: Health Connect Sleep Summary
                val sleepVal = healthSnapshot.valueFor(HealthMetricType.SLEEP_HOURS)
                FluidInsetCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentGlowColor = accentColor
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "REST & RECOVERY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FluidTheme.colors.textTertiary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (sleepVal > 0.0) String.format(Locale.US, "%.1f hrs sleep logged", sleepVal) else "Syncing sleep data...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FluidTheme.colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "Sleep",
                                tint = FluidTheme.colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (sleepVal in 0.1..5.9) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Short sleep detected (< 6 hrs). Flow suggests keeping today focused on 1–2 essential tasks to protect your energy.",
                            fontSize = 12.sp,
                            color = FluidTheme.colors.warning
                        )
                    }
                }

                // Morning Section 2: Rollovers from yesterday
                if (rolloverTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(FluidSpacing.lg))
                    FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "YESTERDAY'S ROLLOVERS (${rolloverTasks.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluidTheme.colors.textTertiary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        rolloverTasks.take(4).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    color = FluidTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Today",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.accent,
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onAssignToday(task.id)
                                            }
                                            .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = "Someday",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textSecondary,
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onMoveBucket(task.id, GtdBucket.SOMEDAY)
                                            }
                                            .background(FluidTheme.colors.divider)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Morning Section 3: Select Today's Big 3
                Spacer(modifier = Modifier.height(FluidSpacing.lg))
                FluidInsetCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentGlowColor = accentColor
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TODAY'S BIG 3 FOCUS (${selectedTop3.size}/3)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluidTheme.colors.textTertiary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Star 3 essential tasks",
                            fontSize = 11.sp,
                            color = FluidTheme.colors.textTertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val candidateTasks = todayTasks.take(6)
                    if (candidateTasks.isEmpty()) {
                        Text(
                            text = "No tasks scheduled for today yet. Capture your top thoughts to set your Big 3.",
                            fontSize = 13.sp,
                            color = FluidTheme.colors.textSecondary
                        )
                    } else {
                        candidateTasks.forEach { task ->
                            val isSelected = task.id in selectedTop3
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(FluidShapes.sm)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTop3 = if (isSelected) {
                                            selectedTop3 - task.id
                                        } else {
                                            if (selectedTop3.size < 3) selectedTop3 + task.id else selectedTop3
                                        }
                                    }
                                    .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) FluidTheme.colors.textPrimary else FluidTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    contentDescription = "Select Big 3",
                                    tint = if (isSelected) accentColor else FluidTheme.colors.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.xl))

                FluidPillButton(
                    text = "Begin Today's Flow",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCompleteRitual(selectedTop3)
                    },
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )

            } else {
                // EVENING SUNSET REVIEW
                // Section 1: Momentum & Goals Celebration
                FluidInsetCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentGlowColor = accentColor
                ) {
                    Text(
                        text = "TODAY'S MOMENTUM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluidTheme.colors.textTertiary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = MomentumSpectrum.stageLabel(dailyProgress),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$completedTodayCount of $totalTodayTargetCount target tasks completed",
                        fontSize = 13.sp,
                        color = FluidTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FluidGlowingProgressBar(
                        progress = dailyProgress,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section 2: Inbox Zero Check
                if (inboxTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(FluidSpacing.lg))
                    FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INBOX CLEAN-UP (${inboxTasks.size} items)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FluidTheme.colors.textTertiary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Clear to Inbox Zero",
                                fontSize = 11.sp,
                                color = FluidTheme.colors.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        inboxTasks.take(4).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    color = FluidTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Done",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.success,
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onToggleTaskComplete(task.id)
                                            }
                                            .background(FluidTheme.colors.success.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = "Tomorrow",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.accent,
                                        modifier = Modifier
                                            .clip(FluidShapes.full)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onAssignToday(task.id)
                                            }
                                            .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(FluidSpacing.lg))
                    FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Inbox Zero",
                                tint = FluidTheme.colors.success,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Inbox Zero Achieved!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluidTheme.colors.textPrimary
                                )
                                Text(
                                    text = "All thoughts clarified. Clear mind for a peaceful evening.",
                                    fontSize = 12.sp,
                                    color = FluidTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.xl))

                FluidPillButton(
                    text = "Complete Evening Review",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCompleteRitual(emptySet())
                    },
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(FluidSpacing.md))
        }
    }
}

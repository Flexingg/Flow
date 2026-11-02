package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.CalendarDailySummary
import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidGlowingProgressBar
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
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    calendarEvents: List<CalendarEventItem>,
    scheduledTasks: List<TaskItem>,
    selectedDate: LocalDate,
    dailySummary: CalendarDailySummary,
    dailyProgress: Float,
    isImporting: Boolean = false,
    importMessage: String? = null,
    onSelectDate: (LocalDate) -> Unit,
    onToggleEventCompletion: (String) -> Unit,
    onToggleTaskCompletion: (String) -> Unit = {},
    onImportDeviceCalendars: () -> Unit,
    onPickIcsFile: () -> Unit,
    onClearImportMessage: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }

    var currentYearMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var isMonthGridExpanded by remember { mutableStateOf(true) }
    var showImportSheet by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val startOfSelectedDayMillis = remember(selectedDate) {
        selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val endOfSelectedDayMillis = remember(selectedDate) {
        selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }

    // Filter events for selected date
    val dayEvents = remember(calendarEvents, startOfSelectedDayMillis, endOfSelectedDayMillis) {
        calendarEvents.filter {
            it.startEpochMillis <= endOfSelectedDayMillis && it.endEpochMillis >= startOfSelectedDayMillis
        }.sortedWith(compareBy({ !it.isAllDay }, { it.startEpochMillis }))
    }

    // Filter GTD tasks due on selected date
    val dayTasks = remember(scheduledTasks, selectedDate) {
        val selectedEpoch = selectedDate.toEpochDay()
        scheduledTasks.filter { task ->
            task.dueDateEpochDay == selectedEpoch ||
                (task.recurrenceRule?.isRecurring == true && task.recurrenceRule.occursOnDate(selectedDate, task.dueDateEpochDay))
        }
    }

    val completedEventsCount = dayEvents.count { it.isCompleted }
    val totalEventsCount = dayEvents.size
    val eventCompletionRatio = if (totalEventsCount > 0) completedEventsCount.toFloat() / totalEventsCount.toFloat() else 0f

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "calendar_controls_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Month Navigation Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .fluidInteractive(
                                pressScale = 0.90f,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentYearMonth = currentYearMonth.minusMonths(1)
                                }
                            )
                            .fluidGlass(GlassLevel.THIN, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous Month",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .fluidInteractive(
                                pressScale = 0.90f,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentYearMonth = currentYearMonth.plusMonths(1)
                                }
                            )
                            .fluidGlass(GlassLevel.THIN, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Next Month",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Action Buttons: Today & Import
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedDate != today) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .fluidInteractive(
                                    pressScale = 0.92f,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentYearMonth = YearMonth.from(today)
                                        onSelectDate(today)
                                    }
                                )
                                .fluidGlass(
                                    level = GlassLevel.THIN,
                                    shape = FluidShapes.full,
                                    isSelected = true,
                                    accentColor = colors.accent
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Today",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .fluidInteractive(
                                pressScale = 0.92f,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showImportSheet = true
                                }
                            )
                            .fluidGlass(GlassLevel.THIN, FluidShapes.full)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = "Import",
                                tint = colors.accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Import",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // Living Flow Month Grid Card
        item(key = "living_flow_month_grid") {
            FluidInsetCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                MonthCalendarGrid(
                    yearMonth = currentYearMonth,
                    selectedDate = selectedDate,
                    today = today,
                    calendarEvents = calendarEvents,
                    scheduledTasks = scheduledTasks,
                    onDateClick = { date ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectDate(date)
                        if (YearMonth.from(date) != currentYearMonth) {
                            currentYearMonth = YearMonth.from(date)
                        }
                    }
                )
            }
        }

        // Week Pebble Strip for rapid day switching
        item(key = "week_pebble_strip") {
            WeekPebbleStrip(
                selectedDate = selectedDate,
                today = today,
                calendarEvents = calendarEvents,
                onSelectDate = { date ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectDate(date)
                    if (YearMonth.from(date) != currentYearMonth) {
                        currentYearMonth = YearMonth.from(date)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Daily Momentum & Event Completion Hero Card
        item(key = "daily_calendar_hero_card") {
            val dateLabel = if (selectedDate == today) {
                "Today • ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))}"
            } else {
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
            }

            FluidInsetCard(
                accentGlowColor = colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dateLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalEventsCount > 0) {
                                "$completedEventsCount of $totalEventsCount events completed (${(eventCompletionRatio * 100).toInt()}%)"
                            } else {
                                "No scheduled events • Open for deep flow"
                            },
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }

                    if (totalEventsCount > 0) {
                        val stageColor = MomentumSpectrum.colorForProgress(eventCompletionRatio)
                        Box(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .background(stageColor.copy(alpha = 0.16f))
                                .border(1.dp, stageColor.copy(alpha = 0.32f), FluidShapes.full)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${(eventCompletionRatio * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = stageColor
                            )
                        }
                    }
                }

                if (totalEventsCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FluidGlowingProgressBar(
                        progress = eventCompletionRatio,
                        accentColor = MomentumSpectrum.colorForProgress(eventCompletionRatio),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Import status banner
        if (!importMessage.isNullOrBlank()) {
            item(key = "import_message_banner") {
                FluidInsetCard(
                    accentGlowColor = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EventAvailable,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = importMessage,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .fluidInteractive(pressScale = 0.88f, onClick = onClearImportMessage)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Agenda Stream Section Header
        item(key = "agenda_section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AGENDA & EVENTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = colors.textTertiary
                )
                Text(
                    text = "${dayEvents.size} events",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        // Agenda Event Rows
        if (dayEvents.isEmpty()) {
            item(key = "empty_agenda_card") {
                FluidInsetCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .fluidGlass(GlassLevel.THIN, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No events on this day",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import calendars or enjoy an open schedule for deep flow.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FluidPillButton(
                            text = "Import Calendars",
                            onClick = { showImportSheet = true },
                            leadingIcon = Icons.Rounded.CloudDownload
                        )
                    }
                }
            }
        } else {
            items(
                count = dayEvents.size,
                key = { dayEvents[it].id }
            ) { index ->
                val event = dayEvents[index]
                CalendarEventRow(
                    event = event,
                    onToggleComplete = { onToggleEventCompletion(event.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }

        // Associated GTD Tasks Due on this Day
        if (dayTasks.isNotEmpty()) {
            item(key = "scheduled_tasks_header") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TASKS SCHEDULED FOR THIS DAY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.textTertiary
                    )
                    Text(
                        text = "${dayTasks.size} tasks",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            item(key = "scheduled_tasks_card") {
                FluidInsetCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    dayTasks.forEachIndexed { idx, task ->
                        if (idx > 0) {
                            HorizontalDivider(
                                color = colors.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (task.isCompleted) colors.accent else Color.Transparent)
                                    .border(1.5.dp, if (task.isCompleted) colors.accent else colors.textTertiary, CircleShape)
                                    .clickable { onToggleTaskCompletion(task.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (task.isCompleted) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = task.title,
                                fontSize = 14.sp,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.isCompleted) colors.textTertiary else colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Bottom spacing for floating navigation island
        item(key = "calendar_bottom_spacer") {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Modal Import Bottom Sheet
    if (showImportSheet) {
        CalendarImportSheet(
            onDismiss = { showImportSheet = false },
            onImportDevice = {
                showImportSheet = false
                onImportDeviceCalendars()
            },
            onPickIcs = {
                showImportSheet = false
                onPickIcsFile()
            }
        )
    }
}

/**
 * Tactile Living Flow Calendar Event Row with interactive check-off physics
 */
@Composable
fun CalendarEventRow(
    event: CalendarEventItem,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    val eventColor = remember(event.calendarColorHex) {
        Color(event.calendarColorHex)
    }

    val checkboxSpringSpec: FiniteAnimationSpec<Float> = if (isReducedMotion) {
        tween(durationMillis = 150)
    } else {
        spring(dampingRatio = 0.35f, stiffness = 420f)
    }

    val checkboxScale by animateFloatAsState(
        targetValue = if (event.isCompleted) 1.0f else 0.92f,
        animationSpec = checkboxSpringSpec,
        label = "calCheckboxScale"
    )

    val burstAnimatable = remember { Animatable(0f) }
    LaunchedEffect(event.isCompleted) {
        if (event.isCompleted && !isReducedMotion) {
            burstAnimatable.snapTo(0f)
            burstAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        } else {
            burstAnimatable.snapTo(0f)
        }
    }

    val timeLabel = remember(event.startEpochMillis, event.endEpochMillis, event.isAllDay) {
        if (event.isAllDay) {
            "All Day"
        } else {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalTime()
            val end = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone).toLocalTime()
            val formatter = DateTimeFormatter.ofPattern("h:mm a")
            "${start.format(formatter)} – ${end.format(formatter)}"
        }
    }

    FluidInsetCard(
        accentGlowColor = if (event.isCompleted) null else eventColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactile Living Flow Checkbox
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .drawBehind {
                        val burstProgress = burstAnimatable.value
                        if (burstProgress > 0f && burstProgress < 1f) {
                            val maxRadius = size.minDimension * 0.95f
                            val currentRadius = size.minDimension * 0.45f + (maxRadius - size.minDimension * 0.45f) * burstProgress
                            val alpha = (1f - burstProgress) * 0.65f
                            drawCircle(
                                color = eventColor.copy(alpha = alpha),
                                radius = currentRadius,
                                style = Stroke(width = 2.dp.toPx() * (1f - burstProgress))
                            )
                        }
                    }
                    .fluidInteractive(
                        pressScale = 0.88f,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleComplete()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = checkboxScale
                            scaleY = checkboxScale
                        }
                        .clip(CircleShape)
                        .background(if (event.isCompleted) eventColor else Color.Transparent)
                        .border(
                            width = if (event.isCompleted) 1.5.dp else 1.25.dp,
                            color = if (event.isCompleted) eventColor else colors.textTertiary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    this@Row.AnimatedVisibility(
                        visible = event.isCompleted,
                        enter = scaleIn(spring(dampingRatio = 0.35f, stiffness = 500f)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Event Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Time badge
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent
                        )
                    }

                    // Calendar source badge
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(eventColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(eventColor)
                            )
                            Text(
                                text = event.calendarDisplayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = eventColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = event.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (event.isCompleted) colors.textTertiary else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (event.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = event.location,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (event.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.description,
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Living Flow Month Calendar Grid
 */
@Composable
fun MonthCalendarGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    calendarEvents: List<CalendarEventItem>,
    scheduledTasks: List<TaskItem>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors

    val daysInMonth = remember(yearMonth) {
        val firstOfMonth = yearMonth.atDay(1)
        val firstDayOfWeek = firstOfMonth.dayOfWeek.value // 1 = Monday .. 7 = Sunday
        val startDate = firstOfMonth.minusDays((firstDayOfWeek - 1).toLong())

        val lastOfMonth = yearMonth.atEndOfMonth()
        val lastDayOfWeek = lastOfMonth.dayOfWeek.value
        val endDate = lastOfMonth.plusDays((7 - lastDayOfWeek).toLong())

        val days = mutableListOf<LocalDate>()
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            days.add(curr)
            curr = curr.plusDays(1)
        }
        days
    }

    val weekdays = remember {
        listOf("M", "T", "W", "T", "F", "S", "S")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Weekday header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekdays.forEach { dayName ->
                Text(
                    text = dayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Weeks rows (chunked by 7)
        daysInMonth.chunked(7).forEach { weekDays ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                weekDays.forEach { date ->
                    val isCurrentMonth = date.month == yearMonth.month
                    val isSelected = date == selectedDate
                    val isToday = date == today

                    val zone = remember { ZoneId.systemDefault() }
                    val dayStart = remember(date) { date.atStartOfDay(zone).toInstant().toEpochMilli() }
                    val dayEnd = remember(date) { date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L }

                    val hasEvents = remember(calendarEvents, dayStart, dayEnd) {
                        calendarEvents.any { it.startEpochMillis <= dayEnd && it.endEpochMillis >= dayStart }
                    }

                    val hasTasks = remember(scheduledTasks, date) {
                        val epoch = date.toEpochDay()
                        scheduledTasks.any { it.dueDateEpochDay == epoch }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> colors.accent
                                    isToday -> colors.accent.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                }
                            )
                            .then(
                                if (isToday && !isSelected) {
                                    Modifier.border(1.25.dp, colors.accent, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onDateClick(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> Color.White
                                    !isCurrentMonth -> colors.textTertiary.copy(alpha = 0.4f)
                                    isToday -> colors.accent
                                    else -> colors.textPrimary
                                }
                            )

                            // Density dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(4.dp)
                            ) {
                                if (hasEvents) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else colors.accent)
                                    )
                                }
                                if (hasTasks) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFFFF9F0A))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Week Pebble Strip for ultra-fast day-by-day switching
 */
@Composable
fun WeekPebbleStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    calendarEvents: List<CalendarEventItem>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val monday = remember(selectedDate) {
        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekDays = remember(monday) {
        (0L..6L).map { monday.plusDays(it) }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        weekDays.forEach { date ->
            val isSelected = date == selectedDate
            val isToday = date == today

            val zone = remember { ZoneId.systemDefault() }
            val dayStart = remember(date) { date.atStartOfDay(zone).toInstant().toEpochMilli() }
            val dayEnd = remember(date) { date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L }

            val hasEvents = remember(calendarEvents, dayStart, dayEnd) {
                calendarEvents.any { it.startEpochMillis <= dayEnd && it.endEpochMillis >= dayStart }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(FluidShapes.md)
                    .background(
                        when {
                            isSelected -> colors.accent
                            isToday -> colors.accent.copy(alpha = 0.12f)
                            else -> colors.surfaceRaised
                        }
                    )
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(1.25.dp, colors.accent, FluidShapes.md)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectDate(date) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isSelected -> Color.White.copy(alpha = 0.85f)
                            isToday -> colors.accent
                            else -> colors.textSecondary
                        }
                    )

                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> Color.White
                            isToday -> colors.accent
                            else -> colors.textPrimary
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !hasEvents -> Color.Transparent
                                    isSelected -> Color.White
                                    else -> colors.accent
                                }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Calendar Import Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarImportSheet(
    onDismiss: () -> Unit,
    onImportDevice: () -> Unit,
    onPickIcs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Import Calendars",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bring your meetings and schedule into Flow so you can check events off alongside your GTD tasks.",
                fontSize = 14.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Device Calendar Sync
            FluidInsetCard(
                accentGlowColor = colors.accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .fluidGlass(GlassLevel.THIN, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Device Calendars",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Google Calendar, Outlook, and system accounts",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FluidPillButton(
                    text = "Sync Device Calendars Now",
                    onClick = onImportDevice,
                    leadingIcon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 2: .ics File Import
            FluidInsetCard(
                accentGlowColor = Color(0xFFAF52DE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .fluidGlass(GlassLevel.THIN, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            tint = Color(0xFFAF52DE),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import .ics File",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Load an iCalendar file from your device",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FluidPillButton(
                    text = "Pick .ics File",
                    onClick = onPickIcs,
                    leadingIcon = Icons.Rounded.FileDownload,
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

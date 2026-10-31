package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private enum class QuickDateOption(val label: String) {
    NO_DATE("No Date"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    IN_3_DAYS("In 3 Days"),
    THIS_WEEKEND("This Weekend"),
    NEXT_WEEK("Next Week (+7d)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onSaveTask: (
        title: String,
        notes: String,
        bucket: GtdBucket,
        dueDateEpochDay: Long?,
        projectId: String?,
        isFlagged: Boolean,
        recurrenceRule: RecurrenceRule?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val isReducedMotion = FluidTheme.isReducedMotion
    val today = remember { LocalDate.now() }

    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedBucket by remember { mutableStateOf(GtdBucket.INBOX) }
    var selectedDateOption by remember { mutableStateOf(QuickDateOption.NO_DATE) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var isFlagged by remember { mutableStateOf(false) }

    // Recurrence configuration state
    var selectedRecurrenceType by remember { mutableStateOf(RecurrenceType.NONE) }
    var selectedDailyInterval by remember { mutableIntStateOf(1) }
    var selectedWeeklyDayOfWeek by remember { mutableStateOf(today.dayOfWeek) }
    var selectedMonthlyDay by remember { mutableIntStateOf(today.dayOfMonth) }
    var selectedNthWeekdayOrdinal by remember { mutableIntStateOf(1) }
    var selectedNthWeekdayDayOfWeek by remember { mutableStateOf(today.dayOfWeek) }

    var isTitleFocused by remember { mutableStateOf(false) }
    var isNotesFocused by remember { mutableStateOf(false) }

    // Bouncy entrance animation for sheet container
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sheetVisible = true
        focusRequester.requestFocus()
    }

    val sheetEntranceScale by animateFloatAsState(
        targetValue = if (sheetVisible || isReducedMotion) 1.0f else 0.92f,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "quickCaptureEntranceScale"
    )

    val sheetEntranceAlpha by animateFloatAsState(
        targetValue = if (sheetVisible || isReducedMotion) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "quickCaptureEntranceAlpha"
    )

    // Animated focus states for input boxes
    val titleFieldScale by animateFloatAsState(
        targetValue = if (isTitleFocused && !isReducedMotion) 1.01f else 1.0f,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "titleFieldScale"
    )

    val titleBorderWidth by animateDpAsState(
        targetValue = if (isTitleFocused) 1.5.dp else 1.dp,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "titleBorderWidth"
    )

    val notesFieldScale by animateFloatAsState(
        targetValue = if (isNotesFocused && !isReducedMotion) 1.01f else 1.0f,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "notesFieldScale"
    )

    val notesBorderWidth by animateDpAsState(
        targetValue = if (isNotesFocused) 1.5.dp else 1.dp,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "notesBorderWidth"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = FluidTheme.colors.scrim
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = sheetEntranceScale
                    scaleY = sheetEntranceScale
                    alpha = sheetEntranceAlpha
                }
                .fluidGlass(
                    level = GlassLevel.THICK,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Grabber Pill (36x4.dp)
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(FluidShapes.full)
                        .background(FluidTheme.colors.textTertiary.copy(alpha = 0.35f))
                )

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // Sheet Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Capture",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluidTheme.colors.textPrimary
                    )

                    // Flag quick toggle in header
                    FluidFilterChip(
                        label = if (isFlagged) "Flagged" else "Flag",
                        selected = isFlagged,
                        onClick = { isFlagged = !isFlagged },
                        leadingIcon = Icons.Rounded.Flag,
                        accentColor = FluidTheme.colors.warning
                    )
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // Auto-focused Task Title Field with bubbly scale and contour glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = titleFieldScale
                            scaleY = titleFieldScale
                        }
                        .clip(FluidShapes.md)
                        .background(
                            if (FluidTheme.colors.isDark) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color.Black.copy(alpha = 0.04f)
                            }
                        )
                        .border(
                            width = titleBorderWidth,
                            brush = if (isTitleFocused) {
                                Brush.verticalGradient(
                                    listOf(
                                        FluidTheme.colors.accent,
                                        FluidTheme.colors.accent.copy(alpha = 0.5f)
                                    )
                                )
                            } else {
                                SolidColor(FluidTheme.colors.divider)
                            },
                            shape = FluidShapes.md
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (title.isEmpty()) {
                        Text(
                            text = "What's on your mind? (Capture thoughts)",
                            fontSize = 16.sp,
                            color = FluidTheme.colors.textTertiary
                        )
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = FluidTheme.colors.textPrimary
                        ),
                        cursorBrush = SolidColor(FluidTheme.colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isTitleFocused = it.isFocused }
                    )
                }

                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                // Notes Field with bubbly focus scale and subtle contour glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = notesFieldScale
                            scaleY = notesFieldScale
                        }
                        .clip(FluidShapes.md)
                        .background(
                            if (FluidTheme.colors.isDark) {
                                Color.White.copy(alpha = 0.06f)
                            } else {
                                Color.Black.copy(alpha = 0.03f)
                            }
                        )
                        .border(
                            width = notesBorderWidth,
                            brush = if (isNotesFocused) {
                                Brush.verticalGradient(
                                    listOf(
                                        FluidTheme.colors.accent.copy(alpha = 0.8f),
                                        FluidTheme.colors.accent.copy(alpha = 0.3f)
                                    )
                                )
                            } else {
                                SolidColor(FluidTheme.colors.divider)
                            },
                            shape = FluidShapes.md
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (notes.isEmpty()) {
                        Text(
                            text = "Add notes or context (optional)",
                            fontSize = 14.sp,
                            color = FluidTheme.colors.textTertiary
                        )
                    }
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        maxLines = 3,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = FluidTheme.colors.textPrimary
                        ),
                        cursorBrush = SolidColor(FluidTheme.colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isNotesFocused = it.isFocused }
                    )
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 1. GTD Bucket Chips Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "GTD BUCKET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = FluidTheme.colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                    ) {
                        FluidFilterChip(
                            label = "Inbox",
                            selected = selectedBucket == GtdBucket.INBOX,
                            onClick = { selectedBucket = GtdBucket.INBOX },
                            leadingIcon = Icons.Rounded.Inbox
                        )
                        FluidFilterChip(
                            label = "Next Action",
                            selected = selectedBucket == GtdBucket.NEXT_ACTION,
                            onClick = { selectedBucket = GtdBucket.NEXT_ACTION },
                            leadingIcon = Icons.Rounded.Bolt
                        )
                        FluidFilterChip(
                            label = "Scheduled",
                            selected = selectedBucket == GtdBucket.SCHEDULED,
                            onClick = {
                                selectedBucket = GtdBucket.SCHEDULED
                                if (selectedDateOption == QuickDateOption.NO_DATE) {
                                    selectedDateOption = QuickDateOption.TODAY
                                }
                            },
                            leadingIcon = Icons.Rounded.Schedule
                        )
                        FluidFilterChip(
                            label = "Someday",
                            selected = selectedBucket == GtdBucket.SOMEDAY,
                            onClick = { selectedBucket = GtdBucket.SOMEDAY },
                            leadingIcon = Icons.Rounded.Lightbulb
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 2. When (Date) Chips Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DUE DATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = FluidTheme.colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                    ) {
                        QuickDateOption.entries.forEach { option ->
                            FluidFilterChip(
                                label = option.label,
                                selected = selectedDateOption == option,
                                onClick = {
                                    selectedDateOption = option
                                    if (option != QuickDateOption.NO_DATE && selectedBucket == GtdBucket.INBOX) {
                                        selectedBucket = GtdBucket.SCHEDULED
                                    }
                                },
                                leadingIcon = if (option != QuickDateOption.NO_DATE) Icons.Rounded.CalendarToday else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 3. Repeat / Recurrence Section
                val builtRule = remember(
                    selectedRecurrenceType,
                    selectedDailyInterval,
                    selectedWeeklyDayOfWeek,
                    selectedMonthlyDay,
                    selectedNthWeekdayOrdinal,
                    selectedNthWeekdayDayOfWeek
                ) {
                    if (selectedRecurrenceType == RecurrenceType.NONE) {
                        null
                    } else {
                        RecurrenceRule(
                            type = selectedRecurrenceType,
                            interval = when (selectedRecurrenceType) {
                                RecurrenceType.DAILY -> selectedDailyInterval
                                else -> 1
                            },
                            dayOfMonth = selectedMonthlyDay,
                            nthWeekday = selectedNthWeekdayOrdinal,
                            dayOfWeek = when (selectedRecurrenceType) {
                                RecurrenceType.WEEKLY -> selectedWeeklyDayOfWeek
                                RecurrenceType.MONTHLY_NTH_WEEKDAY -> selectedNthWeekdayDayOfWeek
                                else -> today.dayOfWeek
                            }
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REPEAT / RECURRENCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = FluidTheme.colors.textTertiary
                        )
                        if (builtRule != null) {
                            Text(
                                text = builtRule.toDisplayLabel(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.accent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                    ) {
                        RecurrenceType.entries.forEach { type ->
                            FluidFilterChip(
                                label = type.label,
                                selected = selectedRecurrenceType == type,
                                onClick = {
                                    selectedRecurrenceType = type
                                    if (type != RecurrenceType.NONE && selectedBucket == GtdBucket.INBOX) {
                                        selectedBucket = GtdBucket.SCHEDULED
                                    }
                                },
                                leadingIcon = Icons.Rounded.Repeat
                            )
                        }
                    }

                    // Animated sub-options
                    this@Column.AnimatedVisibility(
                        visible = selectedRecurrenceType != RecurrenceType.NONE,
                        enter = expandVertically(FluidSprings.morph(isReducedMotion)) + fadeIn(),
                        exit = shrinkVertically(FluidSprings.morph(isReducedMotion)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = FluidSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                        ) {
                            when (selectedRecurrenceType) {
                                RecurrenceType.DAILY -> {
                                    Text(
                                        text = "INTERVAL",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                                    ) {
                                        listOf(
                                            1 to "Every day",
                                            2 to "Every 2 days",
                                            3 to "Every 3 days"
                                        ).forEach { (interval, label) ->
                                            FluidFilterChip(
                                                label = label,
                                                selected = selectedDailyInterval == interval,
                                                onClick = { selectedDailyInterval = interval }
                                            )
                                        }
                                    }
                                }
                                RecurrenceType.WEEKLY -> {
                                    Text(
                                        text = "WEEKDAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                                    ) {
                                        listOf(
                                            DayOfWeek.MONDAY to "Mon",
                                            DayOfWeek.TUESDAY to "Tue",
                                            DayOfWeek.WEDNESDAY to "Wed",
                                            DayOfWeek.THURSDAY to "Thu",
                                            DayOfWeek.FRIDAY to "Fri",
                                            DayOfWeek.SATURDAY to "Sat",
                                            DayOfWeek.SUNDAY to "Sun"
                                        ).forEach { (dow, label) ->
                                            FluidFilterChip(
                                                label = label,
                                                selected = selectedWeeklyDayOfWeek == dow,
                                                onClick = { selectedWeeklyDayOfWeek = dow }
                                            )
                                        }
                                    }
                                }
                                RecurrenceType.MONTHLY_DAY -> {
                                    Text(
                                        text = "DAY OF MONTH",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                                    ) {
                                        (1..31).forEach { day ->
                                            FluidFilterChip(
                                                label = RecurrenceRule.ordinalSuffix(day),
                                                selected = selectedMonthlyDay == day,
                                                onClick = { selectedMonthlyDay = day }
                                            )
                                        }
                                    }
                                }
                                RecurrenceType.MONTHLY_NTH_WEEKDAY -> {
                                    Text(
                                        text = "ORDINAL",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                                    ) {
                                        listOf(
                                            1 to "1st",
                                            2 to "2nd",
                                            3 to "3rd",
                                            4 to "4th",
                                            -1 to "Last"
                                        ).forEach { (ord, label) ->
                                            FluidFilterChip(
                                                label = label,
                                                selected = selectedNthWeekdayOrdinal == ord,
                                                onClick = { selectedNthWeekdayOrdinal = ord }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "WEEKDAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                                    ) {
                                        listOf(
                                            DayOfWeek.MONDAY to "Mon",
                                            DayOfWeek.TUESDAY to "Tue",
                                            DayOfWeek.WEDNESDAY to "Wed",
                                            DayOfWeek.THURSDAY to "Thu",
                                            DayOfWeek.FRIDAY to "Fri",
                                            DayOfWeek.SATURDAY to "Sat",
                                            DayOfWeek.SUNDAY to "Sun"
                                        ).forEach { (dow, label) ->
                                            FluidFilterChip(
                                                label = label,
                                                selected = selectedNthWeekdayDayOfWeek == dow,
                                                onClick = { selectedNthWeekdayDayOfWeek = dow }
                                            )
                                        }
                                    }
                                }
                                RecurrenceType.NONE -> {}
                            }

                            // Live preview pill label
                            builtRule?.let { rule ->
                                Spacer(modifier = Modifier.height(FluidSpacing.xs))
                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                        .border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Repeat,
                                            contentDescription = null,
                                            tint = FluidTheme.colors.accent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Repeats: ${rule.toDisplayLabel()}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = FluidTheme.colors.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 4. Project Chips Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PROJECT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = FluidTheme.colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                    ) {
                        FluidFilterChip(
                            label = "None",
                            selected = selectedProjectId == null,
                            onClick = { selectedProjectId = null }
                        )
                        projects.forEach { project ->
                            val color = Color(project.colorHex)
                            FluidFilterChip(
                                label = project.name,
                                selected = selectedProjectId == project.id,
                                onClick = { selectedProjectId = project.id },
                                accentColor = color
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.xl))

                // Full-width Primary "Add to Flow" Button
                FluidPillButton(
                    text = "Add to Flow",
                    onClick = {
                        if (title.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val now = LocalDate.now()
                            val dueDateEpochDay = when (selectedDateOption) {
                                QuickDateOption.NO_DATE -> null
                                QuickDateOption.TODAY -> now.toEpochDay()
                                QuickDateOption.TOMORROW -> now.plusDays(1).toEpochDay()
                                QuickDateOption.IN_3_DAYS -> now.plusDays(3).toEpochDay()
                                QuickDateOption.THIS_WEEKEND -> now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toEpochDay()
                                QuickDateOption.NEXT_WEEK -> now.plusDays(7).toEpochDay()
                            }
                            onSaveTask(
                                title.trim(),
                                notes.trim(),
                                selectedBucket,
                                dueDateEpochDay,
                                selectedProjectId,
                                isFlagged,
                                builtRule
                            )
                            onDismiss()
                        }
                    },
                    isPrimary = true,
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(FluidSpacing.sm))
            }
        }
    }
}

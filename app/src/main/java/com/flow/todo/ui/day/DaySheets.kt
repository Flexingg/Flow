package com.flow.todo.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.DeviceCalendarInfo
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.nlp.NaturalLanguageTaskParser
import com.flow.todo.domain.tasks.TaskSections
import com.flow.todo.domain.timeline.DayTimeline
import com.flow.todo.domain.timeline.FreeGap
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.state.DayMarker
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = FluidTheme.colors.scrim
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fluidGlass(GlassLevel.THICK, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm),
            content = content
        )
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String?) {
    val colors = FluidTheme.colors
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    subtitle?.let { Text(it, fontSize = 13.sp, color = colors.textSecondary) }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = FluidTheme.colors.textTertiary)
}

@Composable
private fun DurationChips(selected: Int?, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DURATIONS.forEach { m -> FluidFilterChip(NaturalLanguageTaskParser.formatDuration(m), selected == m, { onPick(m) }) }
    }
}

/** Material time picker in a dialog; returns minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initialMinute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

// ------------------------------------------------------------------ task

@Composable
fun TaskBlockSheet(
    task: TaskItem,
    epochDay: Long,
    nextFreeStart: Int?,
    actions: DayActions,
    onDismiss: () -> Unit
) {
    val colors = FluidTheme.colors
    var pickTime by remember { mutableStateOf(false) }
    val time = task.startMinute?.let {
        NaturalLanguageTaskParser.formatClock(it) + " – " + NaturalLanguageTaskParser.formatClock(it + task.effectiveDurationMinutes)
    }
    FlowSheet(onDismiss) {
        SheetTitle(task.title, listOfNotNull(TaskSections.dueLabel(task.dueDateEpochDay), time).joinToString(" · ").ifBlank { "No date" })
        if (task.notes.isNotBlank()) Text(task.notes, fontSize = 14.sp, color = colors.textSecondary)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FluidFilterChip(if (task.isCompleted) "Done ✓" else "Mark done", task.isCompleted, { actions.onToggleTask(task.id); onDismiss() })
            FluidFilterChip(if (task.isFlagged) "★ Starred" else "☆ Star", task.isFlagged, { actions.onToggleStar(task.id) }, accentColor = colors.warning)
        }

        Label("TIME BLOCK")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            nextFreeStart?.let { start ->
                FluidFilterChip("Next free · ${NaturalLanguageTaskParser.formatClock(start)}", false, {
                    actions.onScheduleTask(task.id, epochDay, start, null); onDismiss()
                })
            }
            FluidFilterChip(task.startMinute?.let { "Change time" } ?: "Pick a time", false, { pickTime = true })
            if (task.startMinute != null) {
                FluidFilterChip("Remove time", false, { actions.onScheduleTask(task.id, epochDay, null, null); onDismiss() })
            }
        }
        if (task.startMinute != null) {
            Label("LENGTH")
            DurationChips(task.durationMinutes ?: TaskItem.DEFAULT_TASK_MINUTES) { actions.onResizeTask(task.id, it) }
        }
        Spacer(Modifier.height(4.dp))
        FluidPillButton(text = "Delete task", isPrimary = false, onClick = { actions.onDeleteTask(task.id); onDismiss() }, modifier = Modifier.fillMaxWidth())
    }
    if (pickTime) {
        TimePickDialog(
            initialMinute = task.startMinute ?: (nextFreeStart ?: 9 * 60),
            onPick = { m -> pickTime = false; actions.onScheduleTask(task.id, epochDay, m, null); onDismiss() },
            onDismiss = { pickTime = false }
        )
    }
}

// ------------------------------------------------------------------ event

@Composable
fun EventBlockSheet(event: CalendarEventItem, epochDay: Long, actions: DayActions, onDismiss: () -> Unit) {
    val colors = FluidTheme.colors
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
    val startMinute = ((event.startEpochMillis - dayStart) / 60_000L).toInt()
    val duration = ((event.endEpochMillis - event.startEpochMillis) / 60_000L).toInt()
    val withinDay = !event.isAllDay && startMinute >= 0 && startMinute + duration <= DayTimeline.DAY_MINUTES
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    val time = if (event.isAllDay) "All day" else
        Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).format(fmt) + " – " +
            Instant.ofEpochMilli(event.endEpochMillis).atZone(zone).format(fmt)

    FlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Color(event.calendarColorHex))
            Spacer(Modifier.width(6.dp))
            Text(event.calendarDisplayName, fontSize = 12.sp, color = colors.textSecondary)
        }
        SheetTitle(event.title, time)
        if (event.location.isNotBlank()) Text("📍 ${event.location}", fontSize = 13.sp, color = colors.textSecondary)
        if (event.description.isNotBlank()) Text(event.description, fontSize = 13.sp, color = colors.textSecondary, maxLines = 6)

        FluidFilterChip(if (event.isCompleted) "Checked off ✓" else "Check off", event.isCompleted, { actions.onToggleEvent(event.id) })

        if (withinDay) {
            Label(if (event.source == "DEVICE") "EDIT (SYNCS TO YOUR CALENDAR)" else "EDIT")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FluidFilterChip("Change time", false, { pickTime = true })
                FluidFilterChip("−15m", false, { actions.onRescheduleEvent(event.id, epochDay, startMinute - 15, startMinute - 15 + duration) })
                FluidFilterChip("+15m", false, { actions.onRescheduleEvent(event.id, epochDay, startMinute + 15, startMinute + 15 + duration) })
            }
            Label("LENGTH")
            DurationChips(duration) { m -> actions.onRescheduleEvent(event.id, epochDay, startMinute, startMinute + m) }
        }
        Spacer(Modifier.height(4.dp))
        FluidPillButton(text = "Delete event", isPrimary = false, onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
    }
    if (pickTime) {
        TimePickDialog(
            initialMinute = startMinute.coerceIn(0, DayTimeline.DAY_MINUTES - 1),
            onPick = { m ->
                pickTime = false
                val start = DayTimeline.clampStart(m, duration)
                actions.onRescheduleEvent(event.id, epochDay, start, start + duration)
            },
            onDismiss = { pickTime = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${event.title}\"?") },
            text = { Text(if (event.source == "DEVICE") "It will also be removed from ${event.calendarDisplayName}." else "Removes this imported event from Flow.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.onDeleteEvent(event.id); onDismiss() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ------------------------------------------------------------------ free gap

@Composable
fun GapSheet(
    gap: FreeGap,
    epochDay: Long,
    schedulable: List<TaskItem>,
    writableCalendars: List<DeviceCalendarInfo>,
    actions: DayActions,
    onDismiss: () -> Unit
) {
    val colors = FluidTheme.colors
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(minOf(60, gap.durationMinutes)) }
    var asEvent by remember { mutableStateOf(false) }
    var calendarId by remember { mutableStateOf(writableCalendars.firstOrNull()?.id) }
    val range = NaturalLanguageTaskParser.formatClock(gap.startMinute) + " – " + NaturalLanguageTaskParser.formatClock(gap.endMinute)

    FlowSheet(onDismiss) {
        SheetTitle(DayTimeline.formatGap(gap.durationMinutes), range)

        Label("ADD SOMETHING NEW")
        OutlinedTextField(
            value = title, onValueChange = { title = it }, singleLine = true,
            label = { Text(if (asEvent) "Event title" else "Task title") }, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FluidFilterChip("Task", !asEvent, { asEvent = false })
            FluidFilterChip("Calendar event", asEvent, { asEvent = true })
        }
        if (asEvent) {
            if (writableCalendars.isEmpty()) {
                Text("Connect your calendar (Day screen) to add events.", fontSize = 12.sp, color = colors.textTertiary)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    writableCalendars.forEach { cal ->
                        FluidFilterChip(cal.displayName, calendarId == cal.id, { calendarId = cal.id }, accentColor = Color(cal.colorHex))
                    }
                }
            }
        }
        DurationChips(duration) { duration = it }
        FluidPillButton(
            text = if (asEvent) "Add event at ${NaturalLanguageTaskParser.formatClock(gap.startMinute)}"
            else "Add task at ${NaturalLanguageTaskParser.formatClock(gap.startMinute)}",
            enabled = title.isNotBlank() && (!asEvent || calendarId != null),
            onClick = {
                val cal = calendarId
                if (asEvent && cal != null) actions.onAddEvent(cal, title, epochDay, gap.startMinute, duration)
                else actions.onAddTask(title, epochDay, gap.startMinute, duration)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (schedulable.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Label("OR DROP IN A TO-DO")
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                schedulable.take(30).forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FluidShapes.sm)
                            .clickable { actions.onScheduleTask(task.id, epochDay, gap.startMinute, null); onDismiss() }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        if (task.isFlagged) Text("★ ", color = colors.warning, fontSize = 13.sp)
                        Text(task.title, fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        TaskSections.dueLabel(task.dueDateEpochDay)?.let { Text(it, fontSize = 12.sp, color = colors.textTertiary) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ month

@Composable
fun MonthSheet(
    selectedDate: LocalDate,
    markers: Map<Long, DayMarker>,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = FluidTheme.colors
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val today = LocalDate.now()
    FlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month", tint = colors.textSecondary) }
            Text(
                month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Next month", tint = colors.textSecondary) }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 11.sp, color = colors.textTertiary,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1 // Monday-first
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c - lead
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (index in 0 until month.lengthOfMonth()) {
                            val date = month.atDay(index + 1)
                            val selected = date == selectedDate
                            val marker = markers[date.toEpochDay()]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(FluidShapes.sm)
                                    .background(if (selected) colors.accent.copy(alpha = 0.2f) else Color.Transparent)
                                    .then(if (date == today) Modifier.border(1.dp, colors.accent.copy(alpha = 0.6f), FluidShapes.sm) else Modifier)
                                    .clickable { onSelect(date) }
                            ) {
                                Text(
                                    "${date.dayOfMonth}", fontSize = 14.sp,
                                    fontWeight = if (selected || date == today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) colors.accent else colors.textPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                    if ((marker?.eventCount ?: 0) > 0) Dot(colors.accent)
                                    if ((marker?.openTaskCount ?: 0) > 0) Dot(colors.warning)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

package com.flow.todo.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.DeviceCalendarInfo
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.nlp.NaturalLanguageTaskParser
import com.flow.todo.domain.timeline.DayTimeline
import com.flow.todo.domain.timeline.DayTimelineModel
import com.flow.todo.domain.timeline.FreeGap
import com.flow.todo.domain.timeline.PositionedItem
import com.flow.todo.domain.timeline.TimelineKind
import com.flow.todo.ui.state.DayMarker
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 64.dp
private val LABEL_WIDTH = 46.dp

/** Callbacks for everything the Day screen can change. */
class DayActions(
    val onSelectDate: (LocalDate) -> Unit,
    val onToggleTask: (String) -> Unit,
    val onToggleEvent: (String) -> Unit,
    val onToggleStar: (String) -> Unit,
    val onScheduleTask: (taskId: String, epochDay: Long, startMinute: Int?, durationMinutes: Int?) -> Unit,
    val onResizeTask: (taskId: String, durationMinutes: Int) -> Unit,
    val onRescheduleEvent: (eventId: String, epochDay: Long, startMinute: Int, endMinute: Int) -> Unit,
    val onDeleteTask: (String) -> Unit,
    val onDeleteEvent: (String) -> Unit,
    val onAddTask: (title: String, epochDay: Long, startMinute: Int?, durationMinutes: Int?) -> Unit,
    val onAddEvent: (calendarId: Long, title: String, epochDay: Long, startMinute: Int, durationMinutes: Int) -> Unit,
    val onConnectCalendar: () -> Unit,
    val onImportIcs: () -> Unit
)

/** What a sheet is open for. */
sealed class DaySheet {
    data class Task(val id: String) : DaySheet()
    data class Event(val id: String) : DaySheet()
    data class Gap(val gap: FreeGap) : DaySheet()
    object Month : DaySheet()
}

/**
 * Calendar-first home: week strip → all-day items → "to schedule" tray → hourly timeline.
 * Long-press a block to move it (15-min snaps), drag its bottom edge to resize, long-press a tray
 * chip and drop it on the timeline to time-block it. Tap free time to fill it.
 */
@Composable
fun DayScreen(
    model: DayTimelineModel,
    selectedDate: LocalDate,
    dayMarkers: Map<Long, DayMarker>,
    allTasks: List<TaskItem>,
    allEvents: List<CalendarEventItem>,
    writableCalendars: List<DeviceCalendarInfo>,
    canReadCalendar: Boolean,
    actions: DayActions,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val epochDay = model.epochDay
    val today = LocalDate.now()

    var sheet by remember { mutableStateOf<DaySheet?>(null) }
    val tasksById = remember(allTasks) { allTasks.associateBy { it.id } }
    val eventsById = remember(allEvents) { allEvents.associateBy { it.id } }

    // ---- tray → timeline drag state (root coordinates) ----
    var trayDragTask by remember { mutableStateOf<TaskItem?>(null) }
    var trayDragRoot by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var timelineTopInRoot by remember { mutableFloatStateOf(0f) }
    var timelineBottomInRoot by remember { mutableFloatStateOf(0f) }

    val hourPx = with(density) { HOUR_HEIGHT.toPx() }
    val pxPerMinute = hourPx / 60f
    val firstMinute = model.firstHour * 60

    fun minuteAtRootY(y: Float): Int =
        DayTimeline.snap((firstMinute + (y - timelineTopInRoot) / pxPerMinute).roundToInt())

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(epochDay, model.firstHour) {
        // Land on "now" (today) or the first block, an hour of context above it.
        val anchor = model.nowMinute ?: model.timed.minOfOrNull { it.item.startMinute } ?: (8 * 60)
        scrollState.scrollTo(((anchor - 60 - firstMinute).coerceAtLeast(0) * pxPerMinute).roundToInt())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = FluidSpacing.lg)
        ) {
            WeekStrip(
                selectedDate = selectedDate,
                today = today,
                markers = dayMarkers,
                onSelect = actions.onSelectDate,
                onOpenMonth = { sheet = DaySheet.Month }
            )
            Spacer(Modifier.height(FluidSpacing.sm))

            if (!canReadCalendar) {
                ConnectCalendarCard(onConnect = actions.onConnectCalendar, onImportIcs = actions.onImportIcs)
                Spacer(Modifier.height(FluidSpacing.sm))
            }

            if (model.allDayEvents.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    model.allDayEvents.forEach { e ->
                        AllDayChip(e, onClick = { sheet = DaySheet.Event(e.id) })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (model.unscheduledTasks.isNotEmpty()) {
                Text(
                    "TO SCHEDULE · long-press & drag onto the timeline",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = colors.textTertiary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    model.unscheduledTasks.forEach { task ->
                        TrayChip(
                            task = task,
                            isOverdue = (task.dueDateEpochDay ?: epochDay) < epochDay,
                            isDragging = trayDragTask?.id == task.id,
                            onClick = { sheet = DaySheet.Task(task.id) },
                            onDragStart = { rootPos ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                trayDragTask = task
                                trayDragRoot = rootPos
                            },
                            onDrag = { delta -> trayDragRoot += delta },
                            onDragEnd = {
                                val dropped = trayDragTask
                                if (dropped != null && trayDragRoot.y in timelineTopInRoot..timelineBottomInRoot) {
                                    val start = minuteAtRootY(trayDragRoot.y)
                                    actions.onScheduleTask(dropped.id, epochDay, start, null)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                trayDragTask = null
                            }
                        )
                    }
                }
                Spacer(Modifier.height(FluidSpacing.sm))
            }

            // ---- Timeline ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(FluidShapes.md)
                    .verticalScroll(scrollState)
            ) {
                Timeline(
                    model = model,
                    pxPerMinute = pxPerMinute,
                    actions = actions,
                    onOpenTask = { sheet = DaySheet.Task(it) },
                    onOpenEvent = { sheet = DaySheet.Event(it) },
                    onOpenGap = { sheet = DaySheet.Gap(it) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        timelineTopInRoot = coords.positionInRoot().y
                        timelineBottomInRoot = timelineTopInRoot + coords.size.height
                    }
                )
                // Room to scroll the evening out from under the floating nav bar
                Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
            }
        }

        // Ghost for a tray chip being dragged
        trayDragTask?.let { t ->
            val local = trayDragRoot - rootOrigin
            val preview = if (trayDragRoot.y in timelineTopInRoot..timelineBottomInRoot) {
                " → " + NaturalLanguageTaskParser.formatClock(minuteAtRootY(trayDragRoot.y))
            } else ""
            Box(
                Modifier
                    .offset { IntOffset((local.x - 60.dp.toPx()).roundToInt(), (local.y - 28.dp.toPx()).roundToInt()) }
                    .fluidGlass(GlassLevel.THICK, FluidShapes.full)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(t.title + preview, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
            }
        }
    }

    // ---- Sheets ----
    when (val s = sheet) {
        is DaySheet.Task -> tasksById[s.id]?.let { task ->
            TaskBlockSheet(
                task = task,
                epochDay = epochDay,
                nextFreeStart = model.gaps.firstOrNull()?.startMinute,
                actions = actions,
                onDismiss = { sheet = null }
            )
        } ?: SideEffect { sheet = null }
        is DaySheet.Event -> eventsById[s.id]?.let { event ->
            EventBlockSheet(event = event, epochDay = epochDay, actions = actions, onDismiss = { sheet = null })
        } ?: SideEffect { sheet = null }
        is DaySheet.Gap -> GapSheet(
            gap = s.gap,
            epochDay = epochDay,
            schedulable = allTasks.filter { !it.isCompleted && it.startMinute == null }
                .sortedWith(compareBy<TaskItem> { it.dueDateEpochDay ?: Long.MAX_VALUE }.thenByDescending { it.isFlagged }),
            writableCalendars = writableCalendars,
            actions = actions,
            onDismiss = { sheet = null }
        )
        DaySheet.Month -> MonthSheet(
            selectedDate = selectedDate,
            markers = dayMarkers,
            onSelect = { actions.onSelectDate(it); sheet = null },
            onDismiss = { sheet = null }
        )
        null -> Unit
    }
}

// ================================================================== week strip

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    markers: Map<Long, DayMarker>,
    onSelect: (LocalDate) -> Unit,
    onOpenMonth: () -> Unit
) {
    val colors = FluidTheme.colors
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var dragSum by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxWidth()
            .fluidGlass(GlassLevel.THIN, FluidShapes.lg)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .pointerInput(weekStart) {
                detectHorizontalDragGestures(
                    onDragStart = { dragSum = 0f },
                    onDragEnd = {
                        if (dragSum < -80f) onSelect(selectedDate.plusWeeks(1))
                        else if (dragSum > 80f) onSelect(selectedDate.minusWeeks(1))
                        dragSum = 0f
                    }
                ) { _, amount -> dragSum += amount }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(selectedDate.minusWeeks(1)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous week", tint = colors.textSecondary)
            }
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenMonth).padding(start = 4.dp)
            )
            if (selectedDate != today) {
                Text(
                    "Today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent,
                    modifier = Modifier.clip(FluidShapes.full).clickable { onSelect(today) }.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            IconButton(onClick = onOpenMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = "Month", tint = colors.textSecondary)
            }
            IconButton(onClick = { onSelect(selectedDate.plusWeeks(1)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next week", tint = colors.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val date = weekStart.plusDays(i.toLong())
                val selected = date == selectedDate
                val isToday = date == today
                val marker = markers[date.toEpochDay()]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(FluidShapes.md)
                        .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .then(if (isToday && !selected) Modifier.border(1.dp, colors.accent.copy(alpha = 0.6f), FluidShapes.md) else Modifier)
                        .clickable { onSelect(date) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        fontSize = 11.sp, color = if (selected) colors.accent else colors.textTertiary, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${date.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
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

@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun ConnectCalendarCard(onConnect: () -> Unit, onImportIcs: () -> Unit) {
    val colors = FluidTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .fluidGlass(GlassLevel.THIN, FluidShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Connect your calendar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text("Two-way with Google Calendar: move events here, see them there.", fontSize = 12.sp, color = colors.textSecondary)
        }
        Text(
            ".ics", fontSize = 12.sp, color = colors.textSecondary,
            modifier = Modifier.clip(FluidShapes.full).clickable(onClick = onImportIcs).padding(8.dp)
        )
        Text(
            "Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent,
            modifier = Modifier.clip(FluidShapes.full).clickable(onClick = onConnect).padding(8.dp)
        )
    }
}

@Composable
private fun AllDayChip(event: CalendarEventItem, onClick: () -> Unit) {
    val colors = FluidTheme.colors
    val c = Color(event.calendarColorHex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(FluidShapes.full)
            .background(c.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Dot(c)
        Spacer(Modifier.width(6.dp))
        Text(event.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun TrayChip(
    task: TaskItem,
    isOverdue: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = FluidTheme.colors
    var origin by remember { mutableStateOf(Offset.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { alpha = if (isDragging) 0.35f else 1f }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(FluidShapes.full)
            .border(1.dp, (if (isOverdue) colors.danger else colors.textTertiary).copy(alpha = 0.45f), FluidShapes.full)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> onDragStart(origin + local) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, amount ->
                    change.consume()
                    onDrag(amount)
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (task.isFlagged) {
            Icon(Icons.Rounded.Star, contentDescription = "Starred", tint = colors.warning, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            task.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (isOverdue) colors.danger else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
    }
}

// ================================================================== timeline

@Composable
private fun Timeline(
    model: DayTimelineModel,
    pxPerMinute: Float,
    actions: DayActions,
    onOpenTask: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenGap: (FreeGap) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val density = LocalDensity.current
    val firstMinute = model.firstHour * 60
    val totalMinutes = (model.lastHour - model.firstHour) * 60
    val height = with(density) { (totalMinutes * pxPerMinute).toDp() }
    fun y(minute: Int) = with(density) { ((minute - firstMinute) * pxPerMinute).toDp() }

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val trackWidth = maxWidth - LABEL_WIDTH

        // Hour grid
        for (h in model.firstHour until model.lastHour) {
            val top = y(h * 60)
            Box(Modifier.offset(y = top).fillMaxWidth().height(HOUR_HEIGHT)) {
                Text(
                    NaturalLanguageTaskParser.formatClock(h * 60).replace(":00", ""),
                    fontSize = 10.sp, color = colors.textTertiary,
                    modifier = Modifier.width(LABEL_WIDTH).padding(top = 0.dp)
                )
                Box(
                    Modifier
                        .offset(x = LABEL_WIDTH)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider)
                )
            }
        }

        // Free-time slots
        model.gaps.forEach { gap ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = LABEL_WIDTH + 2.dp, y = y(gap.startMinute) + 2.dp)
                    .width(trackWidth - 4.dp)
                    .height(y(gap.endMinute) - y(gap.startMinute) - 4.dp)
                    .clip(FluidShapes.sm)
                    .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f)), FluidShapes.sm)
                    .background(colors.accent.copy(alpha = 0.04f))
                    .clickable { onOpenGap(gap) }
            ) {
                Text(
                    DayTimeline.formatGap(gap.durationMinutes) + " · tap to plan",
                    fontSize = 11.sp, color = colors.accent.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Blocks
        model.timed.forEach { positioned ->
            TimelineBlock(
                positioned = positioned,
                epochDay = model.epochDay,
                trackWidth = trackWidth,
                pxPerMinute = pxPerMinute,
                firstMinute = firstMinute,
                actions = actions,
                onOpen = {
                    if (positioned.item.kind == TimelineKind.TASK) onOpenTask(positioned.item.id) else onOpenEvent(positioned.item.id)
                }
            )
        }

        // Now line
        model.nowMinute?.let { now ->
            if (now in firstMinute..(firstMinute + totalMinutes)) {
                Box(Modifier.offset(x = LABEL_WIDTH - 5.dp, y = y(now) - 5.dp).size(10.dp).clip(CircleShape).background(colors.danger))
                Box(Modifier.offset(x = LABEL_WIDTH, y = y(now) - 1.dp).width(trackWidth).height(2.dp).background(colors.danger))
            }
        }
    }
}

@Composable
private fun TimelineBlock(
    positioned: PositionedItem,
    epochDay: Long,
    trackWidth: androidx.compose.ui.unit.Dp,
    pxPerMinute: Float,
    firstMinute: Int,
    actions: DayActions,
    onOpen: () -> Unit
) {
    val colors = FluidTheme.colors
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val item = positioned.item
    val isTask = item.kind == TimelineKind.TASK
    val draggable = item.isWithinDay

    // Live drag/resize preview (pixels), committed on release.
    var moveDy by remember(item.id, item.startMinute) { mutableFloatStateOf(0f) }
    var resizeDy by remember(item.id, item.endMinute) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val previewStart = DayTimeline.clampStart(
        DayTimeline.snap(item.startMinute + (moveDy / pxPerMinute).roundToInt()), item.durationMinutes
    )
    val previewDuration = (DayTimeline.snap(item.durationMinutes + (resizeDy / pxPerMinute).roundToInt())).coerceAtLeast(15)
    val shownStart = if (dragging) previewStart else item.startMinute
    val shownDuration = if (resizeDy != 0f) previewDuration else item.durationMinutes

    val laneWidth = trackWidth / positioned.laneCount
    val top = with(density) { ((shownStart - firstMinute) * pxPerMinute).toDp() }
    val blockHeight = with(density) { (shownDuration * pxPerMinute).toDp() }.coerceAtLeast(22.dp)
    val tint = if (isTask) colors.accent else Color(item.colorArgb)

    Box(
        Modifier
            .offset(x = LABEL_WIDTH + laneWidth * positioned.lane + 2.dp, y = top + 1.dp)
            .width(laneWidth - 4.dp)
            .height(blockHeight - 2.dp)
            .graphicsLayer {
                if (dragging) { scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 12f }
            }
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = if (item.isDone) 0.10f else if (isTask) 0.20f else 0.26f))
            .border(1.dp, tint.copy(alpha = if (dragging) 0.9f else 0.45f), RoundedCornerShape(10.dp))
            .then(
                if (draggable) Modifier.pointerInput(item.id, item.startMinute) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            val newStart = DayTimeline.clampStart(
                                DayTimeline.snap(item.startMinute + (moveDy / pxPerMinute).roundToInt()), item.durationMinutes
                            )
                            dragging = false
                            moveDy = 0f
                            if (newStart != item.startMinute) {
                                if (isTask) actions.onScheduleTask(item.id, epochDay, newStart, null)
                                else actions.onRescheduleEvent(item.id, epochDay, newStart, newStart + item.durationMinutes)
                            }
                        },
                        onDragCancel = { dragging = false; moveDy = 0f }
                    ) { change, amount ->
                        change.consume()
                        moveDy += amount.y
                    }
                } else Modifier
            )
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.fillMaxSize().padding(start = 0.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(tint))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTask) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (item.isDone) tint else Color.Transparent)
                                .border(1.5.dp, tint, CircleShape)
                                .clickable { actions.onToggleTask(item.id) }
                        ) {
                            if (item.isDone) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (item.isDone) colors.textTertiary else colors.textPrimary,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (item.isImportant) Icon(Icons.Rounded.Star, "Starred", tint = colors.warning, modifier = Modifier.size(13.dp))
                }
                if (shownDuration >= 40) {
                    Text(
                        NaturalLanguageTaskParser.formatClock(shownStart) + " – " +
                            NaturalLanguageTaskParser.formatClock(shownStart + shownDuration),
                        fontSize = 11.sp, color = colors.textSecondary, maxLines = 1
                    )
                }
            }
        }

        // Resize handle (bottom edge)
        if (draggable) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .pointerInput(item.id, item.endMinute) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val newDuration = (DayTimeline.snap(item.durationMinutes + (resizeDy / pxPerMinute).roundToInt()))
                                    .coerceAtLeast(15)
                                resizeDy = 0f
                                if (newDuration != item.durationMinutes) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isTask) actions.onResizeTask(item.id, newDuration)
                                    else actions.onRescheduleEvent(item.id, epochDay, item.startMinute, item.startMinute + newDuration)
                                }
                            },
                            onDragCancel = { resizeDy = 0f }
                        ) { change, amount ->
                            change.consume()
                            resizeDy += amount
                        }
                    }
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(28.dp)
                        .height(3.dp)
                        .clip(FluidShapes.full)
                        .background(tint.copy(alpha = 0.6f))
                )
            }
        }
    }
}

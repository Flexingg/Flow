package com.flow.todo.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.ParsedTaskSuggestion
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import com.flow.todo.domain.model.SubtaskItem
import com.flow.todo.domain.nlp.NaturalLanguageTaskParser
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
    initialTitle: String = "",
    autoStartVoice: Boolean = false,
    onSaveTask: (
        title: String,
        notes: String,
        bucket: GtdBucket,
        dueDateEpochDay: Long?,
        projectId: String?,
        isFlagged: Boolean,
        recurrenceRule: RecurrenceRule?,
        healthGoal: HealthGoalSpec?,
        subtasks: List<SubtaskItem>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val isReducedMotion = FluidTheme.isReducedMotion
    val today = remember { LocalDate.now() }

    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf("") }
    var selectedBucket by remember { mutableStateOf(GtdBucket.INBOX) }
    var selectedDateOption by remember { mutableStateOf(QuickDateOption.NO_DATE) }
    var customParsedDueEpochDay by remember { mutableStateOf<Long?>(null) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var isFlagged by remember { mutableStateOf(false) }
    var smartParseEnabled by remember { mutableStateOf(true) }

    // Interactive Subtasks / Checklist state
    var subtasks by remember { mutableStateOf(listOf<SubtaskItem>()) }
    var newSubtaskText by remember { mutableStateOf("") }

    // Recurrence configuration state
    var selectedRecurrenceType by remember { mutableStateOf(RecurrenceType.NONE) }
    var selectedDailyInterval by remember { mutableIntStateOf(1) }
    var selectedWeeklyDayOfWeek by remember { mutableStateOf(today.dayOfWeek) }
    var selectedMonthlyDay by remember { mutableIntStateOf(today.dayOfMonth) }
    var selectedNthWeekdayOrdinal by remember { mutableIntStateOf(1) }
    var selectedNthWeekdayDayOfWeek by remember { mutableStateOf(today.dayOfWeek) }

    var isTitleFocused by remember { mutableStateOf(false) }
    var isNotesFocused by remember { mutableStateOf(false) }

    // Real-time Natural Language Task Parser
    val parsedSuggestion = remember(title, notes, projects, today) {
        NaturalLanguageTaskParser.parse(
            rawTitle = title,
            rawNotes = notes,
            projects = projects,
            today = today
        )
    }

    fun applyParsedSuggestionNow(suggestion: ParsedTaskSuggestion) {
        if (suggestion.cleanedTitle.isNotBlank()) {
            title = suggestion.cleanedTitle
        }
        suggestion.dueDateEpochDay?.let { epochDay ->
            customParsedDueEpochDay = epochDay
            val todayEpoch = today.toEpochDay()
            selectedDateOption = when (epochDay) {
                todayEpoch -> QuickDateOption.TODAY
                todayEpoch + 1L -> QuickDateOption.TOMORROW
                todayEpoch + 3L -> QuickDateOption.IN_3_DAYS
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toEpochDay() -> QuickDateOption.THIS_WEEKEND
                todayEpoch + 7L -> QuickDateOption.NEXT_WEEK
                else -> selectedDateOption
            }
        }
        suggestion.matchedProject?.let { selectedProjectId = it.id }
        if (suggestion.isFlagged) {
            isFlagged = true
        }
        suggestion.bucket?.let { selectedBucket = it }
        suggestion.recurrenceRule?.let { rule ->
            selectedRecurrenceType = rule.type
            selectedDailyInterval = rule.interval
            selectedWeeklyDayOfWeek = rule.dayOfWeek
            selectedMonthlyDay = rule.dayOfMonth
            selectedNthWeekdayOrdinal = rule.nthWeekday
            selectedNthWeekdayDayOfWeek = rule.dayOfWeek
        }
        if (suggestion.extractedSubtasks.isNotEmpty()) {
            val existingLower = subtasks.map { it.title.lowercase() }.toSet()
            val added = suggestion.extractedSubtasks
                .filter { it.lowercase() !in existingLower }
                .map { SubtaskItem(title = it) }
            subtasks = subtasks + added
        }
    }

    // 1-Tap Voice Speech Recognizer Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotEmpty()) {
                val suggestion = NaturalLanguageTaskParser.parse(
                    rawTitle = spokenText,
                    rawNotes = notes,
                    projects = projects,
                    today = today
                )
                title = suggestion.cleanedTitle.ifBlank { spokenText }
                applyParsedSuggestionNow(suggestion)
            }
        }
    }

    fun launchVoiceCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak your task (e.g. 'Submit deck tomorrow #Work !flag')"
            )
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    // Bouncy entrance animation for sheet container
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sheetVisible = true
        if (!autoStartVoice) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice) {
            launchVoiceCapture()
        }
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

                // Sheet Title + Voice & Flag quick toggles
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FluidFilterChip(
                            label = "Voice",
                            selected = false,
                            onClick = { launchVoiceCapture() },
                            leadingIcon = Icons.Rounded.Mic,
                            accentColor = FluidTheme.colors.accent
                        )

                        FluidFilterChip(
                            label = if (isFlagged) "Flagged" else "Flag",
                            selected = isFlagged,
                            onClick = { isFlagged = !isFlagged },
                            leadingIcon = Icons.Rounded.Flag,
                            accentColor = FluidTheme.colors.warning
                        )
                    }
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
                            text = "Try: \"Submit deck tomorrow #Work !flag\"",
                            fontSize = 15.sp,
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

                // Luminous Smart NLP Detection Strip
                this@Column.AnimatedVisibility(
                    visible = parsedSuggestion.hasAnyDetection,
                    enter = expandVertically(FluidSprings.morph(isReducedMotion)) + fadeIn(),
                    exit = shrinkVertically(FluidSprings.morph(isReducedMotion)) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FluidSpacing.xs)
                            .clip(FluidShapes.md)
                            .background(FluidTheme.colors.accent.copy(alpha = 0.10f))
                            .border(
                                width = 1.dp,
                                color = FluidTheme.colors.accent.copy(alpha = 0.28f),
                                shape = FluidShapes.md
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Smart Parse",
                                    tint = FluidTheme.colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "✨ Smart Parse:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluidTheme.colors.accent
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(
                                            if (smartParseEnabled) {
                                                FluidTheme.colors.accent.copy(alpha = 0.18f)
                                            } else {
                                                FluidTheme.colors.divider
                                            }
                                        )
                                        .clickable { smartParseEnabled = !smartParseEnabled }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (smartParseEnabled) "Auto-Apply: ON" else "Auto-Apply: OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (smartParseEnabled) {
                                            FluidTheme.colors.accent
                                        } else {
                                            FluidTheme.colors.textTertiary
                                        }
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(FluidTheme.colors.accent)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            applyParsedSuggestionNow(parsedSuggestion)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Apply Now",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parsedSuggestion.detectedTokens.forEach { token ->
                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(FluidTheme.colors.accent.copy(alpha = 0.15f))
                                        .border(
                                            width = 1.dp,
                                            color = FluidTheme.colors.accent.copy(alpha = 0.35f),
                                            shape = FluidShapes.full
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = token,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FluidTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                // Notes Field with Markdown hint
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
                            text = "Add notes (supports **bold**, *italic*, `code`, or - [ ] subtasks)",
                            fontSize = 13.sp,
                            color = FluidTheme.colors.textTertiary
                        )
                    }
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        maxLines = 4,
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

                // Interactive Subtasks / Checklist Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = FluidTheme.colors.textTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "SUBTASKS / CHECKLIST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = FluidTheme.colors.textTertiary
                            )
                        }

                        if (subtasks.isNotEmpty()) {
                            Text(
                                text = "${subtasks.count { it.isCompleted }}/${subtasks.size} steps",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.accent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    // Existing subtask items
                    if (subtasks.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = FluidSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            subtasks.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
                                        .clip(FluidShapes.sm)
                                        .background(
                                            if (FluidTheme.colors.isDark) {
                                                Color.White.copy(alpha = 0.05f)
                                            } else {
                                                Color.Black.copy(alpha = 0.03f)
                                            }
                                        )
                                        .clickable {
                                            subtasks = subtasks.map {
                                                if (it.id == item.id) it.copy(isCompleted = !it.isCompleted) else it
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (item.isCompleted) FluidTheme.colors.accent else Color.Transparent
                                            )
                                            .border(
                                                width = 1.25.dp,
                                                color = if (item.isCompleted) {
                                                    FluidTheme.colors.accent
                                                } else {
                                                    FluidTheme.colors.textTertiary.copy(alpha = 0.5f)
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.isCompleted) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = item.title,
                                        fontSize = 13.sp,
                                        color = if (item.isCompleted) {
                                            FluidTheme.colors.textTertiary
                                        } else {
                                            FluidTheme.colors.textPrimary
                                        },
                                        textDecoration = if (item.isCompleted) {
                                            TextDecoration.LineThrough
                                        } else {
                                            TextDecoration.None
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                subtasks = subtasks.filterNot { it.id == item.id }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove step",
                                            tint = FluidTheme.colors.textTertiary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add new subtask input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FluidShapes.md)
                            .background(
                                if (FluidTheme.colors.isDark) {
                                    Color.White.copy(alpha = 0.05f)
                                } else {
                                    Color.Black.copy(alpha = 0.025f)
                                }
                            )
                            .border(1.dp, FluidTheme.colors.divider, FluidShapes.md)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (newSubtaskText.isEmpty()) {
                                Text(
                                    text = "Add a checklist step...",
                                    fontSize = 13.sp,
                                    color = FluidTheme.colors.textTertiary
                                )
                            }
                            BasicTextField(
                                value = newSubtaskText,
                                onValueChange = { newSubtaskText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = FluidTheme.colors.textPrimary
                                ),
                                cursorBrush = SolidColor(FluidTheme.colors.accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .background(
                                    if (newSubtaskText.isNotBlank()) {
                                        FluidTheme.colors.accent
                                    } else {
                                        FluidTheme.colors.accent.copy(alpha = 0.15f)
                                    }
                                )
                                .clickable(enabled = newSubtaskText.isNotBlank()) {
                                    val clean = newSubtaskText.trim()
                                    if (clean.isNotEmpty()) {
                                        subtasks = subtasks + SubtaskItem(title = clean)
                                        newSubtaskText = ""
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Subtask",
                                    tint = if (newSubtaskText.isNotBlank()) {
                                        Color.White
                                    } else {
                                        FluidTheme.colors.accent
                                    },
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Add",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (newSubtaskText.isNotBlank()) {
                                        Color.White
                                    } else {
                                        FluidTheme.colors.accent
                                    }
                                )
                            }
                        }
                    }
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
                                    customParsedDueEpochDay = null
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
                            val manualDateEpochDay = when (selectedDateOption) {
                                QuickDateOption.NO_DATE -> customParsedDueEpochDay
                                QuickDateOption.TODAY -> now.toEpochDay()
                                QuickDateOption.TOMORROW -> now.plusDays(1).toEpochDay()
                                QuickDateOption.IN_3_DAYS -> now.plusDays(3).toEpochDay()
                                QuickDateOption.THIS_WEEKEND -> now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toEpochDay()
                                QuickDateOption.NEXT_WEEK -> now.plusDays(7).toEpochDay()
                            }

                            val useSmart = smartParseEnabled && parsedSuggestion.hasAnyDetection
                            val finalTitle = if (useSmart) {
                                parsedSuggestion.cleanedTitle.ifBlank { title.trim() }
                            } else {
                                title.trim()
                            }
                            val finalDueDate = manualDateEpochDay ?: if (useSmart) parsedSuggestion.dueDateEpochDay else null
                            val finalProjectId = selectedProjectId ?: if (useSmart) parsedSuggestion.matchedProject?.id else null
                            val finalFlagged = isFlagged || (useSmart && parsedSuggestion.isFlagged)
                            val finalRecurrence = builtRule ?: if (useSmart) parsedSuggestion.recurrenceRule else null
                            val finalBucket = when {
                                selectedBucket != GtdBucket.INBOX -> selectedBucket
                                useSmart && parsedSuggestion.bucket != null -> parsedSuggestion.bucket
                                finalDueDate != null -> GtdBucket.SCHEDULED
                                else -> GtdBucket.INBOX
                            }

                            val mergedSubtasks = buildList {
                                addAll(subtasks)
                                if (newSubtaskText.isNotBlank()) {
                                    add(SubtaskItem(title = newSubtaskText.trim()))
                                }
                                if (useSmart && parsedSuggestion.extractedSubtasks.isNotEmpty()) {
                                    val existingLower = map { it.title.lowercase() }.toSet()
                                    parsedSuggestion.extractedSubtasks.forEach { extracted ->
                                        if (extracted.lowercase() !in existingLower) {
                                            add(SubtaskItem(title = extracted))
                                        }
                                    }
                                }
                            }

                            onSaveTask(
                                finalTitle,
                                notes.trim(),
                                finalBucket,
                                finalDueDate,
                                finalProjectId,
                                finalFlagged,
                                finalRecurrence,
                                null,
                                mergedSubtasks
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

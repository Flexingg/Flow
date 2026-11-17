package com.flow.todo.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.ParsedTaskDraft
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import com.flow.todo.domain.model.SubtaskItem
import com.flow.todo.domain.nlp.NaturalLanguageTaskParser
import com.flow.todo.domain.tasks.TaskSections
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** How the user set the due date: not touched (use what the text says), or an explicit choice. */
private sealed class DueChoice {
    object FromText : DueChoice()
    data class Explicit(val epochDay: Long?) : DueChoice()
}

private enum class RepeatChoice(val label: String) { FROM_TEXT("Repeat"), NONE("No repeat"), DAILY("Daily"), WEEKLY("Weekly") }

/**
 * Lightweight Quick Capture: type (or say) the task and hit enter.
 *
 * Smart text: "pay rent friday", "gym every monday", "call mom tomorrow !flag",
 * "pack // passport // charger" (subtasks). Several lines (or a spoken list) become several tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    initialTitle: String = "",
    autoStartVoice: Boolean = false,
    onSaveTask: (
        title: String,
        notes: String,
        dueDateEpochDay: Long?,
        isFlagged: Boolean,
        recurrenceRule: RecurrenceRule?,
        healthGoal: HealthGoalSpec?,
        subtasks: List<SubtaskItem>
    ) -> Unit,
    onBatchAddTasks: (List<ParsedTaskDraft>) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val colors = FluidTheme.colors
    val today = remember { LocalDate.now() }
    val todayEpoch = today.toEpochDay()

    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf("") }
    var showNotes by remember { mutableStateOf(false) }
    var due by remember { mutableStateOf<DueChoice>(DueChoice.FromText) }
    var repeat by remember { mutableStateOf(RepeatChoice.FROM_TEXT) }
    var flaggedOverride by remember { mutableStateOf<Boolean?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val parsed = remember(title) { NaturalLanguageTaskParser.parse(title, today = today) }
    val lines = remember(title) { NaturalLanguageTaskParser.splitMultiTaskInput(title).takeIf { title.contains('\n') } ?: emptyList() }
    val isBatch = lines.size > 1

    val effectiveDue: Long? = when (val d = due) {
        DueChoice.FromText -> parsed.dueDateEpochDay
        is DueChoice.Explicit -> d.epochDay
    }
    val effectiveRepeat: RecurrenceRule? = when (repeat) {
        RepeatChoice.FROM_TEXT -> parsed.recurrenceRule
        RepeatChoice.NONE -> null
        RepeatChoice.DAILY -> RecurrenceRule(type = RecurrenceType.DAILY)
        RepeatChoice.WEEKLY -> RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            dayOfWeek = LocalDate.ofEpochDay(effectiveDue ?: todayEpoch).dayOfWeek
        )
    }
    val effectiveFlag = flaggedOverride ?: parsed.isFlagged

    fun save() {
        if (title.isBlank()) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (isBatch) {
            val drafts = NaturalLanguageTaskParser.parseMultiple(title, today = today).map { draft ->
                // Explicit chips apply to every task in the batch when the line itself didn't say otherwise.
                draft.copy(
                    dueDateEpochDay = draft.dueDateEpochDay ?: (due as? DueChoice.Explicit)?.epochDay,
                    isFlagged = draft.isFlagged || flaggedOverride == true
                )
            }
            onBatchAddTasks(drafts)
        } else {
            onSaveTask(
                parsed.cleanedTitle.ifBlank { title.trim() },
                notes.trim(),
                effectiveDue,
                effectiveFlag,
                effectiveRepeat,
                null,
                parsed.extractedSubtasks.map { SubtaskItem(title = it) }
            )
        }
        onDismiss()
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                val clauses = NaturalLanguageTaskParser.splitMultiTaskInput(spoken)
                title = if (clauses.size > 1) clauses.joinToString("\n") else spoken
            }
        }
    }
    fun startVoice() {
        runCatching {
            voiceLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a task (or a few)")
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (autoStartVoice) startVoice() else runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = null,
        scrimColor = colors.scrim
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fluidGlass(level = GlassLevel.THICK, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
        ) {
            // Title + mic
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).padding(top = 8.dp)) {
                    if (title.isEmpty()) {
                        Text("What needs doing?", fontSize = 20.sp, color = colors.textTertiary, fontWeight = FontWeight.SemiBold)
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
                IconButton(onClick = { startVoice() }) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Dictate", tint = colors.accent)
                }
            }

            // What the smart text understood
            val understood = buildList {
                if (isBatch) add("${lines.size} tasks")
                if (due is DueChoice.FromText) TaskSections.dueLabel(parsed.dueDateEpochDay, today)?.let { add("📅 $it") }
                if (repeat == RepeatChoice.FROM_TEXT) parsed.recurrenceRule?.let { add("🔁 ${it.toDisplayLabel()}") }
                if (flaggedOverride == null && parsed.isFlagged) add("🚩 Flagged")
                if (!isBatch && parsed.extractedSubtasks.isNotEmpty()) add("☑ ${parsed.extractedSubtasks.size} subtasks")
            }
            if (understood.isNotEmpty()) {
                Text(understood.joinToString("   "), fontSize = 12.sp, color = colors.accent, fontWeight = FontWeight.SemiBold)
            }

            if (showNotes && !isBatch) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.textTertiary.copy(alpha = 0.08f), FluidShapes.md)
                        .padding(12.dp)
                ) {
                    if (notes.isEmpty()) Text("Notes", fontSize = 14.sp, color = colors.textTertiary)
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Due chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val explicit = (due as? DueChoice.Explicit)?.epochDay
                val isExplicit = due is DueChoice.Explicit
                FluidFilterChip("Today", isExplicit && explicit == todayEpoch, { due = DueChoice.Explicit(todayEpoch) })
                FluidFilterChip("Tomorrow", isExplicit && explicit == todayEpoch + 1, { due = DueChoice.Explicit(todayEpoch + 1) })
                FluidFilterChip("Next week", isExplicit && explicit == todayEpoch + 7, { due = DueChoice.Explicit(todayEpoch + 7) })
                val custom = isExplicit && explicit != null && explicit !in listOf(todayEpoch, todayEpoch + 1, todayEpoch + 7)
                FluidFilterChip(
                    if (custom) "📅 ${TaskSections.dueLabel(explicit, today)}" else "Pick date…",
                    custom,
                    { showDatePicker = true }
                )
                FluidFilterChip("No date", isExplicit && explicit == null, { due = DueChoice.Explicit(null) })
            }

            // Repeat / flag / notes
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluidFilterChip(
                    label = if (effectiveRepeat != null) "🔁 ${effectiveRepeat.toDisplayLabel()}" else "🔁 Repeat",
                    selected = effectiveRepeat != null,
                    onClick = {
                        repeat = when (repeat) {
                            RepeatChoice.FROM_TEXT, RepeatChoice.NONE -> RepeatChoice.DAILY
                            RepeatChoice.DAILY -> RepeatChoice.WEEKLY
                            RepeatChoice.WEEKLY -> RepeatChoice.NONE
                        }
                    }
                )
                FluidFilterChip(
                    label = "Flag",
                    selected = effectiveFlag,
                    onClick = { flaggedOverride = !effectiveFlag },
                    leadingIcon = Icons.Rounded.Flag,
                    accentColor = colors.warning
                )
                if (!isBatch) {
                    FluidFilterChip(
                        label = "Notes",
                        selected = showNotes,
                        onClick = { showNotes = !showNotes },
                        leadingIcon = Icons.Rounded.Notes
                    )
                }
            }

            Spacer(Modifier.height(FluidSpacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = colors.textSecondary)
                }
                Spacer(Modifier.width(FluidSpacing.sm))
                FluidPillButton(
                    text = if (isBatch) "Add ${lines.size} tasks" else "Add task",
                    onClick = { save() },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.size(4.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(effectiveDue ?: todayEpoch)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        due = DueChoice.Explicit(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    showDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

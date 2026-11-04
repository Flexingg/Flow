package com.flow.todo.ui.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.coach.CoachPersona
import com.flow.todo.coach.CoachPersonas
import com.flow.todo.coach.CoachPrompt
import com.flow.todo.coach.CoachSettings
import com.flow.todo.coach.CoachTrigger
import com.flow.todo.coach.LlmProvider
import com.flow.todo.coach.data.CoachController
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.components.FluidStretchySwitch
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoachSettingsSheet(
    controller: CoachController,
    healthSnapshot: HealthDailySnapshot,
    onDismiss: () -> Unit
) {
    val settings by controller.settings.collectAsStateWithLifecycle()
    val configured by controller.configuredProviders.collectAsStateWithLifecycle()
    val customPersonas by controller.customPersonas.collectAsStateWithLifecycle(initialValue = emptyList())
    val memoryCount by controller.memoryCount.collectAsStateWithLifecycle(initialValue = 0)
    val colors = FluidTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var preview by remember { mutableStateOf<CoachPrompt?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<CoachPersona?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = colors.scrim
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fluidGlass(level = GlassLevel.THICK, shape = FluidShapes.xl)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
            ) {
                // ---- Header ----
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI Coach", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text(
                            "Bring your own key. A persona that tells you what's going well and what to do next.",
                            fontSize = 12.sp, color = colors.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                FluidInsetCard(Modifier.fillMaxWidth()) {
                    ToggleRow(
                        title = "Enable AI coach",
                        subtitle = "Nothing is ever sent until this is on and you've saved a key",
                        checked = settings.enabled,
                        onChange = { on -> controller.updateSettings { it.copy(enabled = on) } }
                    )
                }

                // ---- Provider & key ----
                SectionLabel("MODEL & API KEY")
                FluidInsetCard(Modifier.fillMaxWidth()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LlmProvider.values().forEach { p ->
                            FluidFilterChip(
                                label = p.label,
                                selected = settings.provider == p,
                                onClick = { testResult = null; controller.updateSettings { it.copy(provider = p) } }
                            )
                        }
                    }
                    Spacer(Modifier.height(FluidSpacing.md))
                    ProviderFields(controller, settings, configured, onTest = {
                        testing = true; testResult = "Testing…"
                        scope.launch { testResult = controller.testConnection(); testing = false }
                    }, testing = testing, testResult = testResult)
                }

                // ---- Personas ----
                SectionLabel("PERSONA")
                FluidInsetCard(Modifier.fillMaxWidth()) {
                    (CoachPersonas.builtIns + customPersonas).forEach { persona ->
                        PersonaRow(
                            persona = persona,
                            selected = settings.selectedPersonaId == persona.id,
                            onSelect = { controller.selectPersona(persona.id) },
                            onEdit = if (persona.builtIn) null else ({ editingPersona = persona }),
                            onDelete = if (persona.builtIn) null else ({ scope.launch { controller.deleteCustomPersona(persona.id) } })
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    val draft = editingPersona
                    if (draft != null) {
                        PersonaEditor(
                            initial = draft,
                            onCancel = { editingPersona = null },
                            onSave = { saved ->
                                scope.launch {
                                    controller.saveCustomPersona(saved)
                                    controller.selectPersona(saved.id)
                                    editingPersona = null
                                }
                            }
                        )
                    } else {
                        FluidPillButton(
                            text = "Create your own persona",
                            leadingIcon = Icons.Rounded.Add,
                            isPrimary = false,
                            onClick = {
                                editingPersona = CoachPersonas.customPersona(CoachPersonas.customId(), "", "", "", 50)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ---- Dynamics ----
                SectionLabel("MOOD & VARIETY")
                FluidInsetCard(Modifier.fillMaxWidth()) {
                    SliderRow(
                        title = "Adapts to your day",
                        subtitle = "0% = always the persona's baseline. Higher = a harsh coach eases off when you're crushing it, " +
                            "and a soft one rallies harder on rough days.",
                        value = settings.adaptivityPercent, range = 0..100, label = { "$it%" },
                        onCommit = { v -> controller.updateSettings { it.copy(adaptivityPercent = v) } }
                    )
                    Divider()
                    SliderRow(
                        title = "Random pick-me-ups",
                        subtitle = "Chance a message ignores the stats and just gives you a lift.",
                        value = settings.pickMeUpPercent, range = 0..50, label = { "$it%" },
                        onCommit = { v -> controller.updateSettings { it.copy(pickMeUpPercent = v) } }
                    )
                    Divider()
                    SliderRow(
                        title = "Message length",
                        subtitle = "Maximum characters (the widget shows about 100-160 comfortably).",
                        value = settings.maxChars, range = 80..280, label = { "$it" },
                        onCommit = { v -> controller.updateSettings { it.copy(maxChars = v) } }
                    )
                }

                // ---- When ----
                SectionLabel("HOW OFTEN")
                FluidInsetCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Tapping the card or widget always works. Turn on any of these for automatic messages:",
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                    Spacer(Modifier.height(FluidSpacing.sm))
                    listOf(CoachTrigger.APP_OPEN, CoachTrigger.SCREEN_OPEN, CoachTrigger.INTERVAL, CoachTrigger.RANDOM).forEach { t ->
                        ToggleRow(
                            title = t.label, subtitle = t.description, checked = t in settings.triggers,
                            onChange = { on ->
                                controller.updateSettings { s ->
                                    s.copy(triggers = if (on) s.triggers + t else s.triggers - t)
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (CoachTrigger.INTERVAL in settings.triggers) {
                        StepperRow("Interval", settings.intervalHours, 1..24, " h") { v ->
                            controller.updateSettings { it.copy(intervalHours = v) }
                        }
                    }
                    if (CoachTrigger.RANDOM in settings.triggers) {
                        StepperRow("Random messages per day", settings.randomPerDay, 1..12, "") { v ->
                            controller.updateSettings { it.copy(randomPerDay = v) }
                        }
                    }
                    Divider()
                    StepperRow("Minimum gap between messages", settings.minGapMinutes, 5..480, " min", step = 15) { v ->
                        controller.updateSettings { it.copy(minGapMinutes = v) }
                    }
                    StepperRow("Daily cap (protects your API bill)", settings.dailyCap, 1..60, "") { v ->
                        controller.updateSettings { it.copy(dailyCap = v) }
                    }
                    StepperRow("Quiet hours start", settings.quietStartHour, 0..23, ":00") { v ->
                        controller.updateSettings { it.copy(quietStartHour = v) }
                    }
                    StepperRow("Quiet hours end", settings.quietEndHour, 0..23, ":00") { v ->
                        controller.updateSettings { it.copy(quietEndHour = v) }
                    }
                    Text(
                        "Background messages read Health Connect live if you granted background access, otherwise they reuse the numbers from the last time you opened Flow " +
                            "(and skip health talk if they're over 8 hours old).",
                        fontSize = 11.sp, color = colors.textTertiary
                    )
                }

                // ---- Data ----
                SectionLabel("WHAT THE COACH CAN SEE")
                FluidInsetCard(Modifier.fillMaxWidth()) {
                    Text(
                        "When a message is generated, only the items below are sent to ${settings.provider.label} with your key. " +
                            "Task and event titles only. Never notes or descriptions. Body weight and heart rate readings are never included.",
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                    Spacer(Modifier.height(FluidSpacing.sm))
                    val src = settings.sources
                    ToggleRow("Today's tasks", "Due today, overdue, and what you finished", src.todayTasks) { on ->
                        controller.updateSettings { it.copy(sources = it.sources.copy(todayTasks = on)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Backlog", "Next actions and inbox items that have been waiting", src.backlogTasks) { on ->
                        controller.updateSettings { it.copy(sources = it.sources.copy(backlogTasks = on)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Health Connect & goals", "Steps, calories, sleep and your daily health goals", src.health) { on ->
                        controller.updateSettings { it.copy(sources = it.sources.copy(health = on)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Calendar", "Upcoming and unchecked events today", src.calendar) { on ->
                        controller.updateSettings { it.copy(sources = it.sources.copy(calendar = on)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Streak & momentum", "Your streak and today's momentum score", src.streakAndMomentum) { on ->
                        controller.updateSettings { it.copy(sources = it.sources.copy(streakAndMomentum = on)) }
                    }
                }

                // ---- Memory & preview ----
                SectionLabel("MEMORY")
                FluidInsetCard(Modifier.fillMaxWidth()) {
                    Text(
                        "To avoid repeating itself, the coach logs which topics it commented on (steps, a task, calories…), " +
                            "not what it said. $memoryCount topic mentions remembered (kept for 14 days).",
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                    Spacer(Modifier.height(FluidSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(FluidSpacing.sm)) {
                        FluidPillButton(
                            text = "Preview prompt", isPrimary = false, modifier = Modifier.weight(1f),
                            onClick = { scope.launch { preview = controller.previewPrompt(healthSnapshot) } }
                        )
                        FluidPillButton(
                            text = "Forget", isPrimary = false, modifier = Modifier.weight(1f),
                            onClick = { controller.clearMemory() }
                        )
                    }
                }
                Spacer(Modifier.height(FluidSpacing.lg))
            }
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            title = { Text("Exactly what would be sent") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("SYSTEM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(p.system, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(10.dp))
                    Text("USER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(p.user, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

// ============================ pieces ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderFields(
    controller: CoachController,
    settings: CoachSettings,
    configured: Set<LlmProvider>,
    onTest: () -> Unit,
    testing: Boolean,
    testResult: String?
) {
    val colors = FluidTheme.colors
    val provider = settings.provider
    val hasKey = provider in configured
    var keyInput by remember(provider) { mutableStateOf("") }
    var keyError by remember(provider) { mutableStateOf<String?>(null) }
    var modelText by remember(provider) { mutableStateOf(settings.activeModel) }
    var baseUrlText by remember(provider) { mutableStateOf(settings.customBaseUrl) }

    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it; keyError = null },
        label = { Text(if (hasKey) "API key saved. Enter a new one to replace" else "API key (${provider.keyHint})") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(FluidSpacing.sm)) {
        FluidPillButton(
            text = "Save key", enabled = keyInput.isNotBlank(), modifier = Modifier.weight(1f),
            onClick = {
                if (controller.saveApiKey(provider, keyInput)) {
                    keyInput = ""
                } else {
                    keyError = "Couldn't store the key securely on this device"
                }
            }
        )
        if (hasKey) {
            FluidPillButton(
                text = "Remove", isPrimary = false, modifier = Modifier.weight(1f),
                onClick = { controller.removeApiKey(provider) }
            )
        }
    }
    keyError?.let { Text(it, fontSize = 12.sp, color = colors.danger) }
    Text(
        "Stored encrypted with the Android Keystore; sent only to ${provider.label}.",
        fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(Modifier.height(FluidSpacing.sm))
    if (provider.requiresBaseUrl) {
        OutlinedTextField(
            value = baseUrlText,
            onValueChange = { baseUrlText = it; controller.updateSettings { s -> s.copy(customBaseUrl = it) } },
            label = { Text("Base URL (https://…/v1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
    }
    OutlinedTextField(
        value = modelText,
        onValueChange = { text ->
            modelText = text
            controller.updateSettings { s -> s.copy(modelOverrides = s.modelOverrides + (s.provider to text)) }
        },
        label = { Text("Model") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (provider.suggestedModels.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            provider.suggestedModels.forEach { m ->
                FluidFilterChip(label = m, selected = modelText == m, onClick = {
                    modelText = m
                    controller.updateSettings { s -> s.copy(modelOverrides = s.modelOverrides + (s.provider to m)) }
                })
            }
        }
    }
    Spacer(Modifier.height(FluidSpacing.sm))
    FluidPillButton(
        text = if (testing) "Testing…" else "Test connection", isPrimary = false, enabled = !testing && hasKey,
        onClick = onTest, modifier = Modifier.fillMaxWidth()
    )
    testResult?.let {
        Text(it, fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PersonaRow(
    persona: CoachPersona,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val colors = FluidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FluidShapes.md)
            .then(
                if (selected) Modifier.border(BorderStroke(1.5.dp, colors.accent), FluidShapes.md)
                else Modifier.border(BorderStroke(1.dp, colors.divider), FluidShapes.md)
            )
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(persona.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(persona.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(persona.tagline, fontSize = 12.sp, color = colors.textSecondary)
            Text(edgeLabel(persona.edge), fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.SemiBold)
        }
        onEdit?.let {
            IconButton(onClick = it, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit persona", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        onDelete?.let {
            IconButton(onClick = it, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete persona", tint = colors.danger, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PersonaEditor(initial: CoachPersona, onCancel: () -> Unit, onSave: (CoachPersona) -> Unit) {
    val colors = FluidTheme.colors
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var emoji by remember(initial.id) { mutableStateOf(initial.emoji) }
    var prompt by remember(initial.id) { mutableStateOf(initial.prompt) }
    var edge by remember(initial.id) { mutableFloatStateOf(initial.edge.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Custom persona", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Emoji") },
                singleLine = true, modifier = Modifier.width(84.dp)
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(CoachPersonas.MAX_CUSTOM_PROMPT_CHARS) },
            label = { Text("Personality") },
            placeholder = { Text("e.g. A pirate captain who treats my to-do list like treasure to plunder…") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${prompt.length}/${CoachPersonas.MAX_CUSTOM_PROMPT_CHARS}. Go as mean as you like.") }
        )
        Text("Baseline edge: ${edgeLabel(edge.toInt())}", fontSize = 12.sp, color = colors.textSecondary)
        Slider(value = edge, onValueChange = { edge = it }, valueRange = 0f..100f)
        Row(horizontalArrangement = Arrangement.spacedBy(FluidSpacing.sm)) {
            FluidPillButton(text = "Cancel", isPrimary = false, onClick = onCancel, modifier = Modifier.weight(1f))
            FluidPillButton(
                text = "Save", enabled = name.isNotBlank() && prompt.isNotBlank(), modifier = Modifier.weight(1f),
                onClick = { onSave(CoachPersonas.customPersona(initial.id, name, emoji, prompt, edge.toInt())) }
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = FluidTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = FluidSpacing.md)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        FluidStretchySwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    label: (Int) -> String,
    onCommit: (Int) -> Unit
) {
    val colors = FluidTheme.colors
    var live by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(label(live.toInt()), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent)
        }
        Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onCommit(live.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    val colors = FluidTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range.first, range.last)) }, enabled = value > range.first) { Text("−", fontSize = 18.sp) }
        Text("$value$suffix", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent, modifier = Modifier.width(64.dp))
        TextButton(onClick = { onChange((value + step).coerceIn(range.first, range.last)) }, enabled = value < range.last) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        color = FluidTheme.colors.textTertiary, modifier = Modifier.padding(top = FluidSpacing.sm)
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(thickness = 1.dp, color = FluidTheme.colors.divider, modifier = Modifier.padding(vertical = FluidSpacing.md))
}

private fun edgeLabel(edge: Int): String = when {
    edge <= 20 -> "Edge $edge · warm"
    edge <= 40 -> "Edge $edge · supportive"
    edge <= 60 -> "Edge $edge · balanced"
    edge <= 80 -> "Edge $edge · blunt"
    else -> "Edge $edge · tough love"
}

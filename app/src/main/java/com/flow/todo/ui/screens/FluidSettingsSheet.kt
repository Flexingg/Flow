package com.flow.todo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.DEFAULT_MOMENTUM_STOPS_HEX
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.MOMENTUM_COLOR_SWATCHES
import com.flow.todo.domain.model.MOMENTUM_PALETTE_PRESETS
import com.flow.todo.domain.model.ShizukuStatus
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.components.FluidStretchySwitch
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidSettingsSheet(
    preferences: AccessibilityPreferences,
    onUpdatePreferences: ((AccessibilityPreferences) -> AccessibilityPreferences) -> Unit,
    onDismiss: () -> Unit,
    dailyProgress: Float = 0f,
    shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    lastSyncedSystemColorHex: String? = null,
    healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
    onRequestShizukuPermission: () -> Unit = {},
    onSyncSystemColorsNow: () -> Unit = {},
    onRestoreSystemPalette: () -> Unit = {},
    onRequestHealthPermissions: () -> Unit = {},
    onRefreshHealthStats: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    val currentStopHexes = preferences.momentumStopHexes.takeIf { it.size == 5 } ?: DEFAULT_MOMENTUM_STOPS_HEX
    var selectedStageIndex by remember { mutableStateOf(0) }
    var customHexInput by remember(selectedStageIndex, currentStopHexes[selectedStageIndex]) {
        mutableStateOf(String.format(java.util.Locale.US, "#%06X", currentStopHexes[selectedStageIndex] and 0xFFFFFFL))
    }

    // Bouncy entrance animation for sheet container
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sheetVisible = true
    }

    val sheetEntranceScale by animateFloatAsState(
        targetValue = if (sheetVisible || isReducedMotion) 1.0f else 0.92f,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "settingsSheetEntranceScale"
    )

    val sheetEntranceAlpha by animateFloatAsState(
        targetValue = if (sheetVisible || isReducedMotion) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "settingsSheetEntranceAlpha"
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

                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Settings & Preferences",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluidTheme.colors.textPrimary
                        )
                        Text(
                            text = "ColorOS 17 Living Flow & accessibility guardrails",
                            fontSize = 12.sp,
                            color = FluidTheme.colors.textSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = FluidTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.lg))

                // Living Flow & Accessibility Guardrails Card
                Text(
                    text = "LIVING FLOW & GUARDRAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = FluidTheme.colors.textTertiary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(FluidSpacing.xs))

                FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                    // Reduce Transparency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = FluidSpacing.md)) {
                            Text(
                                text = "Reduce Transparency",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Replace frosted glass with calm opaque surfaces",
                                fontSize = 13.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                        }

                        FluidStretchySwitch(
                            checked = preferences.reduceTransparency,
                            onCheckedChange = { checked ->
                                onUpdatePreferences { it.copy(reduceTransparency = checked) }
                            }
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = FluidTheme.colors.divider,
                        modifier = Modifier.padding(vertical = FluidSpacing.md)
                    )

                    // Reduce Motion
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = FluidSpacing.md)) {
                            Text(
                                text = "Reduce Motion",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Disable elastic springs, stretch, and overshoot",
                                fontSize = 13.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                        }

                        FluidStretchySwitch(
                            checked = preferences.reduceMotion,
                            onCheckedChange = { checked ->
                                onUpdatePreferences { it.copy(reduceMotion = checked) }
                            }
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = FluidTheme.colors.divider,
                        modifier = Modifier.padding(vertical = FluidSpacing.md)
                    )

                    // Increased Contrast
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = FluidSpacing.md)) {
                            Text(
                                text = "Increased Contrast",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Strengthen contour strokes (1.5dp) and surface opacity",
                                fontSize = 13.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                        }

                        FluidStretchySwitch(
                            checked = preferences.increasedContrast,
                            onCheckedChange = { checked ->
                                onUpdatePreferences { it.copy(increasedContrast = checked) }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.lg))

                // Momentum Spectrum & Shizuku System UI Section
                Text(
                    text = "MOMENTUM SPECTRUM & SHIZUKU SYSTEM UI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = FluidTheme.colors.textTertiary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(FluidSpacing.xs))

                FluidInsetCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentGlowColor = MomentumSpectrum.colorForProgress(dailyProgress).copy(alpha = 0.30f)
                ) {
                    // Toggle 1: Daily Momentum Color Spectrum
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = FluidSpacing.md)) {
                            Text(
                                text = "Daily Momentum Color Spectrum",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Morph app colors from Red → Purple → Blue → Yellow → Green as daily tasks & health goals progress",
                                fontSize = 13.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                        }

                        FluidStretchySwitch(
                            checked = preferences.useMomentumColorSpectrum,
                            onCheckedChange = { checked ->
                                onUpdatePreferences { it.copy(useMomentumColorSpectrum = checked) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // Visual 5-stage spectrum bar showing custom or default stops with active stage highlighted
                    val stages = currentStopHexes.map { hex ->
                        Triple(
                            MomentumSpectrum.colorNameForHex(hex),
                            Color(hex),
                            String.format(java.util.Locale.US, "#%06X", hex and 0xFFFFFFL)
                        )
                    }
                    val activeIndex = when {
                        dailyProgress >= 0.80f -> 4
                        dailyProgress >= 0.60f -> 3
                        dailyProgress >= 0.40f -> 2
                        dailyProgress >= 0.20f -> 1
                        else -> 0
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stages.forEachIndexed { index, (name, color, hex) ->
                            val isActive = index == activeIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(FluidShapes.md)
                                    .background(
                                        if (isActive) color.copy(alpha = if (FluidTheme.colors.isDark) 0.32f else 0.22f)
                                        else if (FluidTheme.colors.isDark) Color.White.copy(alpha = 0.05f)
                                        else Color.Black.copy(alpha = 0.03f)
                                    )
                                    .border(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = if (isActive) color else FluidTheme.colors.divider,
                                        shape = FluidShapes.md
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isActive) 12.dp else 8.dp)
                                            .then(
                                                if (isActive) {
                                                    Modifier.shadow(elevation = 6.dp, shape = CircleShape, spotColor = color)
                                                } else Modifier
                                            )
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) color else FluidTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = hex,
                                        fontSize = 9.sp,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // 5-STAGE PROGRESS COLOR PICKER
                    // Header with Reset Default
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CUSTOMIZE PROGRESS COLORS (TAP A STAGE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = FluidTheme.colors.textTertiary
                        )

                        Text(
                            text = "Reset Default",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluidTheme.colors.accent,
                            modifier = Modifier
                                .clip(FluidShapes.xs)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdatePreferences { it.copy(momentumStopHexes = DEFAULT_MOMENTUM_STOPS_HEX) }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // 5 Interactive Stage Selector Pills/Orbs (0% Start, 25% Spark, 50% Flow, 75% Surge, 100% Complete)
                    val stageNames = listOf("Start", "Spark", "Flow", "Surge", "Complete")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (0..4).forEach { idx ->
                            val isSelected = selectedStageIndex == idx
                            val stageColor = Color(currentStopHexes[idx])
                            val stagePercent = "${idx * 25}%"

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(FluidShapes.md)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedStageIndex = idx
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 36.dp else 28.dp)
                                        .then(
                                            if (isSelected) Modifier.shadow(8.dp, CircleShape, spotColor = stageColor)
                                            else Modifier
                                        )
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) stageColor else FluidTheme.colors.divider,
                                            shape = CircleShape
                                        )
                                        .padding(if (isSelected) 3.dp else 2.dp)
                                        .clip(CircleShape)
                                        .background(stageColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = stagePercent,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) stageColor else FluidTheme.colors.textSecondary
                                )
                                Text(
                                    text = stageNames[idx],
                                    fontSize = 9.sp,
                                    color = if (isSelected) FluidTheme.colors.textPrimary else FluidTheme.colors.textTertiary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // 1-Tap Spectrum Theme Presets Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MOMENTUM_PALETTE_PRESETS.forEach { preset ->
                            val isPresetActive = currentStopHexes == preset.stopsHex
                            FluidFilterChip(
                                label = preset.name,
                                selected = isPresetActive,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onUpdatePreferences { it.copy(momentumStopHexes = preset.stopsHex) }
                                },
                                accentColor = Color(preset.stopsHex.first())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // 16-Swatch Color Grid for Selected Stage
                    Text(
                        text = "Pick color for Stage ${selectedStageIndex + 1} (${selectedStageIndex * 25}%): ${MomentumSpectrum.colorNameForHex(currentStopHexes[selectedStageIndex])}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = FluidTheme.colors.textSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MOMENTUM_COLOR_SWATCHES.chunked(8).forEach { swatchRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                swatchRow.forEach { (name, swatchHex) ->
                                    val swatchColor = Color(swatchHex)
                                    val isSelected = (currentStopHexes[selectedStageIndex] and 0xFFFFFFL) == (swatchHex and 0xFFFFFFL)

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .then(
                                                if (isSelected) Modifier.shadow(6.dp, CircleShape, spotColor = swatchColor)
                                                else Modifier
                                            )
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else FluidTheme.colors.divider.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val updatedList = currentStopHexes.toMutableList().apply {
                                                    this[selectedStageIndex] = swatchHex
                                                }
                                                onUpdatePreferences { it.copy(momentumStopHexes = updatedList) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = name,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Custom Hex Code Input (#RRGGBB)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val parsedHex = customHexInput.removePrefix("#").trim().toLongOrNull(16)?.let {
                            0xFF000000L or (it and 0xFFFFFFL)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(FluidShapes.sm)
                                .background(if (FluidTheme.colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                                .border(1.dp, FluidTheme.colors.divider, FluidShapes.sm)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (parsedHex != null && customHexInput.removePrefix("#").trim().length == 6) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(parsedHex))
                                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    if (customHexInput.isEmpty()) {
                                        Text(
                                            text = "#RRGGBB",
                                            fontSize = 13.sp,
                                            color = FluidTheme.colors.textTertiary
                                        )
                                    }
                                    BasicTextField(
                                        value = customHexInput,
                                        onValueChange = { input ->
                                            if (input.length <= 7) {
                                                customHexInput = input.uppercase()
                                            }
                                        },
                                        textStyle = TextStyle(
                                            color = FluidTheme.colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        FluidFilterChip(
                            label = "Apply Hex",
                            selected = false,
                            onClick = {
                                if (parsedHex != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val updatedList = currentStopHexes.toMutableList().apply {
                                        this[selectedStageIndex] = parsedHex
                                    }
                                    onUpdatePreferences { it.copy(momentumStopHexes = updatedList) }
                                }
                            },
                            accentColor = if (parsedHex != null) Color(parsedHex) else FluidTheme.colors.accent
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = FluidTheme.colors.divider,
                        modifier = Modifier.padding(vertical = FluidSpacing.md)
                    )

                    // Toggle 2: Sync Android System UI Colors (Shizuku)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = FluidSpacing.md)) {
                            Text(
                                text = "Sync Android System UI Colors (Shizuku)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Set system-wide Material You palette via Shizuku / WRITE_SECURE_SETTINGS",
                                fontSize = 13.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                        }

                        FluidStretchySwitch(
                            checked = preferences.syncSystemColorsWithShizuku,
                            onCheckedChange = { checked ->
                                onUpdatePreferences { it.copy(syncSystemColorsWithShizuku = checked) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Shizuku status banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fluidGlass(GlassLevel.THIN, FluidShapes.md)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (shizukuStatus.isReady) FluidTheme.colors.success
                                            else FluidTheme.colors.warning
                                        )
                                )
                                Text(
                                    text = shizukuStatus.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (shizukuStatus.isReady) FluidTheme.colors.success else FluidTheme.colors.textPrimary
                                )
                            }
                            Text(
                                text = shizukuStatus.description,
                                fontSize = 12.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                            if (lastSyncedSystemColorHex != null) {
                                Text(
                                    text = "Last Synced Palette: #$lastSyncedSystemColorHex",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FluidTheme.colors.accent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!shizukuStatus.isReady) {
                            FluidPillButton(
                                text = "Authorize Shizuku",
                                onClick = onRequestShizukuPermission,
                                isPrimary = true,
                                leadingIcon = Icons.Rounded.Security,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            FluidPillButton(
                                text = "Sync System UI Now",
                                onClick = onSyncSystemColorsNow,
                                isPrimary = true,
                                leadingIcon = Icons.Rounded.Palette,
                                modifier = Modifier.weight(1f)
                            )
                            FluidPillButton(
                                text = "Restore Wallpaper Palette",
                                onClick = onRestoreSystemPalette,
                                isPrimary = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.lg))

                // Health Connect Integration Section
                Text(
                    text = "HEALTH CONNECT INTEGRATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = FluidTheme.colors.textTertiary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(FluidSpacing.xs))

                FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FluidTheme.colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = FluidTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Health Connect Permissions",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${healthSnapshot.grantedPermissionCount} / ${healthSnapshot.totalPermissionCount} Health Connect permissions granted",
                                fontSize = 13.sp,
                                color = if (healthSnapshot.hasAllPermissions) FluidTheme.colors.success else FluidTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FluidPillButton(
                            text = "Grant All Health Permissions",
                            onClick = onRequestHealthPermissions,
                            isPrimary = !healthSnapshot.hasAllPermissions,
                            leadingIcon = Icons.Rounded.Favorite,
                            modifier = Modifier.weight(1f)
                        )
                        FluidPillButton(
                            text = "Refresh Biometrics",
                            onClick = onRefreshHealthStats,
                            isPrimary = false,
                            leadingIcon = Icons.Rounded.Refresh,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Appearance Theme Section
                Text(
                    text = "APPEARANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = FluidTheme.colors.textTertiary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(FluidSpacing.xs))

                FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                    // Material You Dynamic Color Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = FluidSpacing.md)) {
                            Text(
                                text = "Material You Dynamic Color",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Adapt Living Flow glass and accents to your wallpaper palette (Android 12+)",
                                fontSize = 13.sp,
                                color = FluidTheme.colors.textSecondary
                            )
                        }

                        FluidStretchySwitch(
                            checked = preferences.useDynamicColor,
                            onCheckedChange = { checked ->
                                onUpdatePreferences { it.copy(useDynamicColor = checked) }
                            }
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = FluidTheme.colors.divider,
                        modifier = Modifier.padding(vertical = FluidSpacing.md)
                    )

                    Text(
                        text = "Lighting & Aesthetics",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluidTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Adapt to system lighting or lock to light flow / dark OLED",
                        fontSize = 13.sp,
                        color = FluidTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                    ) {
                        FluidFilterChip(
                            label = "System Default",
                            selected = preferences.darkThemeOverride == null,
                            onClick = {
                                onUpdatePreferences { it.copy(darkThemeOverride = null) }
                            }
                        )
                        FluidFilterChip(
                            label = "Light Flow",
                            selected = preferences.darkThemeOverride == false,
                            onClick = {
                                onUpdatePreferences { it.copy(darkThemeOverride = false) }
                            }
                        )
                        FluidFilterChip(
                            label = "Dark OLED",
                            selected = preferences.darkThemeOverride == true,
                            onClick = {
                                onUpdatePreferences { it.copy(darkThemeOverride = true) }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.xl))

                FluidPillButton(
                    text = "Done",
                    onClick = onDismiss,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(FluidSpacing.sm))
            }
        }
    }
}

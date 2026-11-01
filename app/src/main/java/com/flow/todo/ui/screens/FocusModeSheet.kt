package com.flow.todo.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.AmbientSoundscape
import com.flow.todo.domain.model.FocusSessionState
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeSheet(
    focusState: FocusSessionState,
    dailyProgress: Float,
    onTogglePlayPause: () -> Unit,
    onResetTimer: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onSelectSoundscape: (AmbientSoundscape) -> Unit,
    onCompleteTask: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val accentColor = MomentumSpectrum.colorForProgress(dailyProgress)

    val infiniteTransition = rememberInfiniteTransition(label = "focus_waves")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

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
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEEP FLOW FOCUS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = accentColor
                    )
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

            Spacer(modifier = Modifier.height(FluidSpacing.lg))

            // Optional Active Target Task Card
            if (!focusState.targetTaskTitle.isNullOrBlank()) {
                FluidInsetCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentGlowColor = accentColor
                ) {
                    Text(
                        text = "FOCUSING ON",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluidTheme.colors.textTertiary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = focusState.targetTaskTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluidTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (focusState.targetTaskId != null) {
                        Spacer(modifier = Modifier.height(FluidSpacing.sm))
                        Row(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCompleteTask(focusState.targetTaskId)
                                }
                                .background(FluidTheme.colors.success.copy(alpha = 0.15f))
                                .border(1.dp, FluidTheme.colors.success.copy(alpha = 0.40f), FluidShapes.full)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Complete",
                                tint = FluidTheme.colors.success,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mark Task Complete",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.success
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(FluidSpacing.xl))
            }

            // Circular Liquid Timer with Animated Visualizer
            Box(
                modifier = Modifier
                    .size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Wave Canvas background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.minDimension / 2f) - 16.dp.toPx()

                    // Background track
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius,
                        style = Stroke(width = 12.dp.toPx())
                    )

                    // Luminous Progress Arc
                    val sweepAngle = focusState.progress * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                accentColor.copy(alpha = 0.70f),
                                accentColor,
                                Color.White.copy(alpha = 0.95f)
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Internal liquid ripples when audio is active
                    if (focusState.isPlayingAudio && focusState.soundscape != AmbientSoundscape.OFF) {
                        val path = Path()
                        val waveRadius = radius * 0.75f
                        val steps = 60
                        for (s in 0..steps) {
                            val angle = (s.toFloat() / steps.toFloat()) * (2f * Math.PI.toFloat())
                            val modulation = sin(angle * 3f + wavePhase) * 6.dp.toPx()
                            val r = waveRadius + modulation
                            val x = center.x + r * kotlin.math.cos(angle)
                            val y = center.y + r * kotlin.math.sin(angle)
                            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(
                            path = path,
                            color = accentColor.copy(alpha = 0.12f)
                        )
                    }
                }

                // Countdown Typography & Controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = focusState.formattedRemainingTime,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        color = FluidTheme.colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (focusState.isPaused) "PAUSED" else if (focusState.isActive) "IN FLOW" else "READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = if (focusState.isPaused) FluidTheme.colors.warning else accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.xl))

            // Primary Control Buttons: Play/Pause, Reset, +5m
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FluidTheme.colors.surfaceSolid.copy(alpha = 0.8f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResetTimer()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset",
                        tint = FluidTheme.colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(FluidSpacing.lg))

                // Big Play / Pause button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .fluidInteractive(
                            pressScale = 0.90f,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePlayPause()
                            }
                        )
                        .fluidGlass(
                            level = GlassLevel.THICK,
                            shape = CircleShape,
                            isSelected = true,
                            accentColor = accentColor
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (focusState.isActive && !focusState.isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play / Pause",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(FluidSpacing.lg))

                // +5 min button
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(FluidShapes.full)
                        .background(FluidTheme.colors.surfaceSolid.copy(alpha = 0.8f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAddFiveMinutes()
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+5m",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluidTheme.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(FluidSpacing.xxl))

            // Ambient Soundscapes Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROCEDURAL AMBIENT SOUNDSCAPE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = FluidTheme.colors.textTertiary
                    )
                    Text(
                        text = "100% Offline • 0MB",
                        fontSize = 10.sp,
                        color = FluidTheme.colors.textTertiary
                    )
                }

                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(FluidSpacing.xs)
                ) {
                    AmbientSoundscape.entries.forEach { soundscape ->
                        val isSelected = focusState.soundscape == soundscape
                        FluidFilterChip(
                            label = soundscape.label,
                            selected = isSelected,
                            accentColor = accentColor,
                            leadingIcon = soundscape.toIcon(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectSoundscape(soundscape)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = focusState.soundscape.description,
                    fontSize = 12.sp,
                    color = FluidTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(FluidSpacing.xxl))

            FluidPillButton(
                text = "Close & Run in Background",
                onClick = onDismiss,
                isPrimary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun AmbientSoundscape.toIcon(): ImageVector = when (this) {
    AmbientSoundscape.OFF -> Icons.Rounded.VolumeOff
    AmbientSoundscape.RAIN_SHOWER -> Icons.Rounded.WaterDrop
    AmbientSoundscape.FOREST_STREAM -> Icons.Rounded.Waves
    AmbientSoundscape.BROWN_NOISE -> Icons.Rounded.GraphicEq
    AmbientSoundscape.BINAURAL_ALPHA -> Icons.Rounded.SelfImprovement
}

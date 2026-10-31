package com.flow.todo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.components.FluidStretchySwitch
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidSettingsSheet(
    preferences: AccessibilityPreferences,
    onUpdatePreferences: ((AccessibilityPreferences) -> AccessibilityPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isReducedMotion = FluidTheme.isReducedMotion

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

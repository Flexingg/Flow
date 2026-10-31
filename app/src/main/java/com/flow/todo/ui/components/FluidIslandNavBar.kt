package com.flow.todo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.FlowTab
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ColorOS 17 Floating Island Navigation Bar:
 * - Horizontally inset 20.dp, floating 16.dp above navigation bar insets
 * - Left pill island displaying FlowTab items with sliding spring-morphing indicator and swipe support
 * - Bubbly tab icon scaling and bounce offset
 * - Pop-in/pop-out Inbox badge count pill
 * - Detached 60.dp circular glass '+' Quick-Capture button with breathing halo glow and bubbly press physics
 */
@Composable
fun FluidIslandNavBar(
    selectedTab: FlowTab,
    inboxBadgeCount: Int,
    onTabSelected: (FlowTab) -> Unit,
    onQuickAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current
    val tabs = remember { FlowTab.entries.toTypedArray() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main floating pill island
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .fluidGlass(
                    level = GlassLevel.THIN,
                    shape = FluidShapes.full
                )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val islandWidthPx = constraints.maxWidth.toFloat()
                val tabCount = tabs.size
                val tabWidth = (maxWidth - 8.dp) / tabCount

                var lastHapticIndex by remember { mutableIntStateOf(selectedTab.ordinal) }
                var accumulatedDrag by remember { mutableFloatStateOf(0f) }

                // Animated tab index for smooth morphing indicator
                val targetIndex = selectedTab.ordinal.toFloat()
                val animatedIndex by animateFloatAsState(
                    targetValue = targetIndex,
                    animationSpec = FluidSprings.release(isReducedMotion),
                    label = "islandTabIndex"
                )

                // Stretch effect during transitions: stretches horizontally and squishes vertically
                val transitionDelta = abs(targetIndex - animatedIndex)
                val stretchBonusDp = (transitionDelta * 20f).dp
                val indicatorSquishY = 1f - (transitionDelta * 0.16f).coerceIn(0f, 0.22f)

                // Sliding inner pill indicator
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .offset(x = tabWidth * animatedIndex - (stretchBonusDp / 2f))
                        .width(tabWidth + stretchBonusDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleY = indicatorSquishY
                        }
                        .shadow(
                            elevation = 4.dp,
                            shape = FluidShapes.full,
                            spotColor = colors.accent.copy(alpha = if (colors.isDark) 0.35f else 0.25f)
                        )
                        .clip(FluidShapes.full)
                        .background(
                            colors.accent.copy(alpha = if (colors.isDark) 0.24f else 0.16f)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    colors.accent.copy(alpha = if (colors.isDark) 0.70f else 0.50f),
                                    colors.accent.copy(alpha = if (colors.isDark) 0.25f else 0.15f)
                                )
                            ),
                            shape = FluidShapes.full
                        )
                )

                // Tab items & gesture handling
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .pointerInput(tabCount, islandWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    accumulatedDrag = 0f
                                    val index = (offset.x / (islandWidthPx / tabCount))
                                        .toInt()
                                        .coerceIn(0, tabCount - 1)
                                    lastHapticIndex = index
                                },
                                onDragEnd = {
                                    val finalIndex = (lastHapticIndex).coerceIn(0, tabCount - 1)
                                    if (finalIndex != selectedTab.ordinal) {
                                        onTabSelected(tabs[finalIndex])
                                    }
                                },
                                onDragCancel = {
                                    accumulatedDrag = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                    val currentX = change.position.x
                                    val rawIndex = (currentX / (islandWidthPx / tabCount))
                                        .coerceIn(0f, (tabCount - 1).toFloat())
                                    val newIndex = rawIndex.roundToInt().coerceIn(0, tabCount - 1)

                                    if (newIndex != lastHapticIndex) {
                                        lastHapticIndex = newIndex
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTabSelected(tabs[newIndex])
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = tab == selectedTab
                        val tabInteractionSource = remember { MutableInteractionSource() }

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.18f else 0.92f,
                            animationSpec = FluidSprings.bubbly(isReducedMotion),
                            label = "tabIconScale_${tab.name}"
                        )
                        val iconOffsetY by animateDpAsState(
                            targetValue = if (isSelected) (-2).dp else 0.dp,
                            animationSpec = FluidSprings.bubbly(isReducedMotion),
                            label = "tabIconOffset_${tab.name}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = tabInteractionSource,
                                    indication = null
                                ) {
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTabSelected(tab)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    val icon = when (tab) {
                                        FlowTab.INBOX -> Icons.Rounded.Inbox
                                        FlowTab.TODAY_NEXT -> Icons.Rounded.CalendarToday
                                        FlowTab.PROJECTS -> Icons.Rounded.Folder
                                    }

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.label,
                                        tint = if (isSelected) colors.accent else colors.textSecondary,
                                        modifier = Modifier
                                            .offset(y = iconOffsetY)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                            .size(20.dp)
                                    )

                                    if (tab == FlowTab.INBOX) {
                                        this@Column.AnimatedVisibility(
                                            visible = inboxBadgeCount > 0,
                                            enter = scaleIn(animationSpec = FluidSprings.bubbly(isReducedMotion)) + fadeIn(),
                                            exit = scaleOut() + fadeOut()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = 10.dp, y = (-5).dp)
                                                    .clip(FluidShapes.full)
                                                    .background(colors.accent)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = if (inboxBadgeCount > 99) "99+" else inboxBadgeCount.toString(),
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 10.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = when (tab) {
                                        FlowTab.INBOX -> "Inbox"
                                        FlowTab.TODAY_NEXT -> "Today"
                                        FlowTab.PROJECTS -> "Projects"
                                    },
                                    color = if (isSelected) colors.accent else colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Circular Glass Quick-Capture Button with breathing halo glow and bubbly press scale
        val haloTransition = rememberInfiniteTransition(label = "quickAddHaloTransition")
        val haloAlpha by haloTransition.animateFloat(
            initialValue = if (colors.isDark) 0.20f else 0.28f,
            targetValue = if (colors.isDark) 0.50f else 0.58f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "quickAddHaloAlpha"
        )
        val haloScale by haloTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "quickAddHaloScale"
        )

        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isReducedMotion) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = 0.50f),
                                        colors.accent.copy(alpha = 0.15f),
                                        Color.Transparent
                                    ),
                                    radius = size.width * 0.70f
                                ),
                                radius = size.width * 0.70f
                            )
                        }
                )
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .fluidGlass(
                        level = GlassLevel.REGULAR,
                        shape = CircleShape,
                        isSelected = true,
                        accentColor = colors.accent
                    )
                    .fluidInteractive(
                        pressScale = 0.86f,
                        onClick = onQuickAddClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Quick Add",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

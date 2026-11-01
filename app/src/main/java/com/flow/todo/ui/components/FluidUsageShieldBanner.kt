package com.flow.todo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.AppUsageShieldAlert
import com.flow.todo.domain.model.ShieldSeverity
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive

@Composable
fun FluidUsageShieldBanner(
    alert: AppUsageShieldAlert?,
    onDismiss: (String) -> Unit,
    onOpenGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion

    // Remember the last non-null alert so exit animations render smoothly
    val lastAlert = remember(alert) { alert }

    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(
            animationSpec = FluidSprings.bubbly(isReducedMotion),
            initialOffsetY = { -it }
        ) + scaleIn(
            animationSpec = FluidSprings.bubbly(isReducedMotion),
            initialScale = 0.88f
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            animationSpec = tween(200),
            targetOffsetY = { -it / 2 }
        ) + scaleOut(
            animationSpec = tween(200),
            targetScale = 0.92f
        ) + fadeOut(animationSpec = tween(160)),
        modifier = modifier
    ) {
        val current = alert ?: lastAlert ?: return@AnimatedVisibility
        val isLimitReached = current.severity == ShieldSeverity.LIMIT_100
        val shieldColor = if (isLimitReached) MomentumSpectrum.Red else colors.warning

        val infiniteTransition = rememberInfiniteTransition(label = "shieldPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shieldIconPulse"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fluidGlass(
                    level = GlassLevel.THICK,
                    shape = FluidShapes.xl,
                    isSelected = true,
                    accentColor = shieldColor
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing Liquid-Glass Shield Orb
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            val s = if (isReducedMotion) 1f else pulseScale
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(shieldColor.copy(alpha = 0.20f))
                        .border(1.dp, shieldColor.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = "App Usage Shield",
                        tint = shieldColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Badge Pill
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(shieldColor.copy(alpha = 0.18f))
                            .border(0.75.dp, shieldColor.copy(alpha = 0.45f), FluidShapes.full)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isLimitReached) {
                                "100% LIMIT REACHED — SHIELD ACTIVE"
                            } else {
                                "80% GENTLE USAGE NUDGE"
                            },
                            color = shieldColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${current.appName} • ${current.formattedStatus}",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Quick Dismiss Icon
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .fluidInteractive(
                            pressScale = 0.88f,
                            onClick = { onDismiss(current.goalId) }
                        )
                        .clip(CircleShape)
                        .background(colors.textTertiary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss Shield Alert",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FluidGlowingProgressBar(
                progress = current.usageRatio.coerceIn(0f, 1f),
                accentColor = shieldColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLimitReached) {
                        "Daily screen-time budget exhausted. Take a mindful break."
                    } else {
                        "Approaching your daily limit. Wrap up your session soon."
                    },
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Snooze 15m Glass Pill
                    Box(
                        modifier = Modifier
                            .fluidInteractive(
                                pressScale = 0.92f,
                                onClick = { onDismiss(current.goalId) }
                            )
                            .clip(FluidShapes.full)
                            .background(
                                if (colors.isDark) Color.White.copy(alpha = 0.10f)
                                else Color.Black.copy(alpha = 0.06f)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Snooze 15m",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Review Limits Glass Pill
                    Row(
                        modifier = Modifier
                            .fluidInteractive(
                                pressScale = 0.92f,
                                onClick = onOpenGoals
                            )
                            .clip(FluidShapes.full)
                            .background(shieldColor.copy(alpha = 0.20f))
                            .border(0.75.dp, shieldColor.copy(alpha = 0.50f), FluidShapes.full)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = shieldColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Review Limits",
                            color = shieldColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

package com.flow.todo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive

/**
 * ColorOS 17 Fluid Top Bar:
 * - Transparent at rest with large expressive 28.sp Medium title and optional subtitle
 * - Morphs smoothly into a regular glass bar with progressive blur bottom edge and compact title when scrolled
 * - 42.dp circular glass Settings/Accessibility action button on trailing side
 */
@Composable
fun FluidTopBar(
    title: String,
    subtitle: String? = null,
    isScrolled: Boolean = false,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val spec = FluidTheme.glassSpec(GlassLevel.REGULAR)

    val scrollFraction by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "topBarScrollFraction"
    )

    val verticalPadding by animateDpAsState(
        targetValue = if (isScrolled) 10.dp else 16.dp,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "topBarVerticalPadding"
    )

    val titleFontSize = (28f - (10f * scrollFraction)).sp
    val titleFontWeight = if (scrollFraction > 0.5f) FontWeight.SemiBold else FontWeight.Medium

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = (6f * scrollFraction).dp,
                spotColor = if (colors.isDark) Color(0x80000000) else Color(0x1F000000)
            )
            .drawBehind {
                if (scrollFraction > 0.005f) {
                    // Glass background fill
                    val fillAlpha = spec.fillColor.alpha * scrollFraction
                    drawRect(color = spec.fillColor.copy(alpha = fillAlpha))

                    // Sheen and bottom contour glow
                    val strokeAlpha = spec.strokeBottom.alpha * scrollFraction
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = spec.strokeBottom.copy(alpha = strokeAlpha),
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = strokeWidth
                    )

                    // Progressive scrim ramp below the bottom edge
                    val rampHeight = 16.dp.toPx() * scrollFraction
                    val rampBrush = Brush.verticalGradient(
                        0f to colors.bgBase.copy(alpha = 0.55f * scrollFraction),
                        1f to Color.Transparent,
                        startY = size.height,
                        endY = size.height + rampHeight
                    )
                    drawRect(
                        brush = rampBrush,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height),
                        size = androidx.compose.ui.geometry.Size(size.width, rampHeight)
                    )
                }
            }
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = verticalPadding, bottom = verticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    fontWeight = titleFontWeight,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedVisibility(
                    visible = !isScrolled && !subtitle.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // 42.dp Circular Glass Settings Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .fluidGlass(
                        level = GlassLevel.THIN,
                        shape = CircleShape
                    )
                    .fluidInteractive(
                        pressScale = 0.92f,
                        onClick = onSettingsClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings and Accessibility",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

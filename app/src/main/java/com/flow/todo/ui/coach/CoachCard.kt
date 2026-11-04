package com.flow.todo.ui.coach

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.coach.data.CoachController
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import kotlinx.coroutines.delay

/**
 * Home-screen style AI coach card shown at the top of the Today screen.
 * Shows the latest persona message, a refresh button and a settings shortcut.
 */
@Composable
fun CoachCard(
    controller: CoachController,
    healthSnapshot: HealthDailySnapshot,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by controller.settings.collectAsStateWithLifecycle()
    val latest by controller.latest.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val configured by controller.configuredProviders.collectAsStateWithLifecycle()
    val colors = FluidTheme.colors

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val ready = settings.enabled && settings.provider in configured

    FluidInsetCard(modifier = modifier.fillMaxWidth(), accentGlowColor = if (ready) colors.accent else null) {
        if (!ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(FluidSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("Meet your AI coach", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        "Bring your own Claude, Gemini or DeepSeek key and pick a personality, from cheerleader to drill sergeant.",
                        fontSize = 13.sp, color = colors.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(FluidSpacing.md))
            FluidPillButton(text = "Set up coach", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
            return@FluidInsetCard
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(latest?.personaEmoji ?: "🤖", fontSize = 20.sp)
            Spacer(Modifier.width(FluidSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    latest?.personaName ?: "AI Coach",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary
                )
                latest?.let {
                    Text(it.tone.label, fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(
                onClick = { controller.refreshNow(healthSnapshot) },
                enabled = !status.isGenerating,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh, contentDescription = "Get a new coach message",
                    tint = if (status.isGenerating) colors.textTertiary else colors.accent
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Coach settings", tint = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(FluidSpacing.sm))

        val body = when {
            status.isGenerating -> "Thinking…"
            latest != null -> latest!!.text
            else -> "Tap refresh for your first message."
        }
        Text(
            body,
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp,
            color = if (status.isGenerating) colors.textTertiary else colors.textPrimary
        )

        status.error?.let { error ->
            Spacer(Modifier.height(FluidSpacing.sm))
            Text(error, fontSize = 12.sp, color = colors.danger)
        }
        status.info?.let { info ->
            Spacer(Modifier.height(FluidSpacing.sm))
            Text(info, fontSize = 12.sp, color = colors.textTertiary)
        }

        latest?.let { msg ->
            if (!status.isGenerating) {
                Spacer(Modifier.height(FluidSpacing.sm))
                val about = if (msg.topicLabels.isEmpty()) "" else " · ${msg.topicLabels.joinToString(", ")}"
                Text(
                    timeAgo(now - msg.timeMillis) + about,
                    fontSize = 11.sp, color = colors.textTertiary, maxLines = 1
                )
            }
        }
    }
}

internal fun timeAgo(deltaMillis: Long): String {
    val m = (deltaMillis / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}

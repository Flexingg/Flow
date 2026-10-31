package com.flow.todo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.GoalPeriod
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.InstalledAppInfo
import com.flow.todo.domain.model.Project
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidGlass
import com.flow.todo.ui.theme.fluidInteractive

private val POPULAR_APP_PRESETS = listOf(
    InstalledAppInfo("com.instagram.android", "Instagram"),
    InstalledAppInfo("com.zhiliaoapp.musically", "TikTok"),
    InstalledAppInfo("com.google.android.youtube", "YouTube"),
    InstalledAppInfo("com.twitter.android", "X / Twitter"),
    InstalledAppInfo("com.reddit.frontpage", "Reddit"),
    InstalledAppInfo("com.duolingo", "Duolingo"),
    InstalledAppInfo("com.whatsapp", "WhatsApp"),
    InstalledAppInfo("com.android.chrome", "Chrome"),
    InstalledAppInfo("com.spotify.music", "Spotify"),
    InstalledAppInfo("com.netflix.mediaclient", "Netflix")
)

/**
 * Living Flow Goal Creator Sheet:
 * Dedicated glass bottom sheet for creating Task Milestones, Health Connect biometric goals,
 * and App Usage screen-time/open limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCreatorSheet(
    projects: List<Project>,
    installedApps: List<InstalledAppInfo>,
    hasUsagePermission: Boolean,
    hasHealthPermissions: Boolean,
    onRequestUsagePermission: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onDismiss: () -> Unit,
    onSaveGoal: (
        title: String,
        category: GoalCategory,
        comparison: GoalComparison,
        targetValue: Double,
        period: GoalPeriod,
        projectId: String?,
        healthMetric: HealthMetricType?,
        targetPackageName: String?,
        targetAppName: String?,
        appUsageMetric: AppUsageMetricType?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = FluidTheme.colors
    val isDark = colors.isDark
    val isReducedMotion = FluidTheme.isReducedMotion
    val haptic = LocalHapticFeedback.current

    // Category selection
    var selectedCategory by remember { mutableStateOf(GoalCategory.TASK_COMPLETION) }

    // Task Milestone configuration
    var taskPeriod by remember { mutableStateOf(GoalPeriod.DAILY) }
    var taskProjectId by remember { mutableStateOf<String?>(null) }
    var taskComparison by remember { mutableStateOf(GoalComparison.AT_LEAST) }
    var taskTargetValue by remember { mutableDoubleStateOf(3.0) }
    var taskCustomTargetText by remember { mutableStateOf("3") }

    // Health Connect configuration
    var selectedHealthMetric by remember { mutableStateOf(HealthMetricType.STEPS) }
    var healthComparison by remember { mutableStateOf(GoalComparison.AT_LEAST) }
    var healthTargetValue by remember { mutableDoubleStateOf(HealthMetricType.STEPS.defaultTarget) }
    var healthCustomTargetText by remember { mutableStateOf("10000") }

    // App Usage configuration
    val effectiveAppList = remember(installedApps) {
        if (installedApps.isNotEmpty()) installedApps else POPULAR_APP_PRESETS
    }
    var selectedPackageName by remember {
        mutableStateOf(effectiveAppList.firstOrNull()?.packageName ?: "com.instagram.android")
    }
    var selectedAppName by remember {
        mutableStateOf(effectiveAppList.firstOrNull()?.appName ?: "Instagram")
    }
    var appUsageMetric by remember { mutableStateOf(AppUsageMetricType.ACTIVE_MINUTES) }
    var appComparison by remember { mutableStateOf(GoalComparison.AT_MOST) }
    var appTargetValue by remember { mutableDoubleStateOf(30.0) }
    var appCustomTargetText by remember { mutableStateOf("30") }
    var appSearchFilter by remember { mutableStateOf("") }

    // Custom or auto-generated title
    var customTitle by remember { mutableStateOf("") }
    var isUserEditedTitle by remember { mutableStateOf(false) }

    // Auto-generate title based on active configuration
    val autoGeneratedTitle = remember(
        selectedCategory,
        taskPeriod,
        taskProjectId,
        taskComparison,
        taskTargetValue,
        selectedHealthMetric,
        healthComparison,
        healthTargetValue,
        selectedAppName,
        appUsageMetric,
        appComparison,
        appTargetValue
    ) {
        when (selectedCategory) {
            GoalCategory.TASK_COMPLETION -> {
                val projectName = projects.firstOrNull { it.id == taskProjectId }?.name ?: "All"
                val compSym = taskComparison.symbol
                val periodStr = taskPeriod.label
                "Complete $compSym ${taskTargetValue.toInt()} $projectName Tasks ($periodStr)"
            }
            GoalCategory.HEALTH_CONNECT -> {
                val metric = selectedHealthMetric
                val compSym = healthComparison.symbol
                "${metric.label} $compSym ${metric.formatValue(healthTargetValue)} ${metric.unit}"
            }
            GoalCategory.APP_USAGE -> {
                val compSym = appComparison.symbol
                val formattedVal = appUsageMetric.formatValue(appTargetValue)
                val unitStr = if (appUsageMetric == AppUsageMetricType.ACTIVE_MINUTES) "min/day" else "opens/day"
                "$selectedAppName $compSym $formattedVal $unitStr"
            }
        }
    }

    val finalTitle = if (isUserEditedTitle && customTitle.isNotBlank()) customTitle.trim() else autoGeneratedTitle

    // Bouncy entrance animation
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sheetVisible = true
    }

    val sheetEntranceScale by animateFloatAsState(
        targetValue = if (sheetVisible || isReducedMotion) 1.0f else 0.94f,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "goalCreatorEntranceScale"
    )

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
                .graphicsLayer {
                    scaleX = sheetEntranceScale
                    scaleY = sheetEntranceScale
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
                // Top Grabber Pill
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(FluidShapes.full)
                        .background(colors.textTertiary.copy(alpha = 0.35f))
                )

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "New Living Flow Goal",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Drives your daily Red → Green color spectrum",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 1. GOAL TYPE SELECTOR (Category)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "GOAL CATEGORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FluidFilterChip(
                            label = "Task Milestone",
                            selected = selectedCategory == GoalCategory.TASK_COMPLETION,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategory = GoalCategory.TASK_COMPLETION
                            },
                            leadingIcon = Icons.Rounded.TaskAlt,
                            accentColor = MomentumSpectrum.Blue
                        )
                        FluidFilterChip(
                            label = "Health Connect",
                            selected = selectedCategory == GoalCategory.HEALTH_CONNECT,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategory = GoalCategory.HEALTH_CONNECT
                            },
                            leadingIcon = Icons.Rounded.Favorite,
                            accentColor = MomentumSpectrum.Green
                        )
                        FluidFilterChip(
                            label = "App Usage Limit",
                            selected = selectedCategory == GoalCategory.APP_USAGE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategory = GoalCategory.APP_USAGE
                            },
                            leadingIcon = Icons.Rounded.Timer,
                            accentColor = MomentumSpectrum.Purple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 2. DYNAMIC CONFIGURATION BY CATEGORY
                when (selectedCategory) {
                    GoalCategory.TASK_COMPLETION -> {
                        // TASK MILESTONE CONFIGURATION
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
                        ) {
                            // Period: Daily vs Weekly
                            Text(
                                text = "PERIOD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoalPeriod.entries.forEach { period ->
                                    FluidFilterChip(
                                        label = period.label,
                                        selected = taskPeriod == period,
                                        onClick = { taskPeriod = period }
                                    )
                                }
                            }

                            // Project Scope: All Tasks vs Specific Project
                            Text(
                                text = "PROJECT SCOPE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FluidFilterChip(
                                    label = "All Tasks (General)",
                                    selected = taskProjectId == null,
                                    onClick = { taskProjectId = null }
                                )
                                projects.forEach { project ->
                                    val projectColor = Color(project.colorHex)
                                    FluidFilterChip(
                                        label = project.name,
                                        selected = taskProjectId == project.id,
                                        onClick = { taskProjectId = project.id },
                                        accentColor = projectColor,
                                        leadingIcon = Icons.Rounded.Folder
                                    )
                                }
                            }

                            // Condition: At Least (>=) vs At Most (<=)
                            Text(
                                text = "CONDITION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoalComparison.entries.forEach { comp ->
                                    FluidFilterChip(
                                        label = comp.label,
                                        selected = taskComparison == comp,
                                        onClick = { taskComparison = comp }
                                    )
                                }
                            }

                            // Target Tasks Count: Presets + Custom
                            Text(
                                text = "TARGET TASKS COUNT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(1.0, 3.0, 5.0, 10.0, 15.0, 25.0).forEach { preset ->
                                    FluidFilterChip(
                                        label = "${preset.toInt()} tasks",
                                        selected = taskTargetValue == preset,
                                        onClick = {
                                            taskTargetValue = preset
                                            taskCustomTargetText = preset.toInt().toString()
                                        }
                                    )
                                }

                                // Custom numeric input
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(FluidShapes.full)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                        .border(1.dp, colors.divider, FluidShapes.full)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = "Custom:", fontSize = 12.sp, color = colors.textSecondary)
                                        BasicTextField(
                                            value = taskCustomTargetText,
                                            onValueChange = { newText ->
                                                val filtered = newText.filter { it.isDigit() }
                                                taskCustomTargetText = filtered
                                                filtered.toDoubleOrNull()?.let {
                                                    if (it > 0.0) taskTargetValue = it
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            ),
                                            cursorBrush = SolidColor(colors.accent),
                                            modifier = Modifier.widthIn(min = 36.dp, max = 64.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    GoalCategory.HEALTH_CONNECT -> {
                        // HEALTH CONNECT BIOMETRIC CONFIGURATION
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
                        ) {
                            if (!hasHealthPermissions) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(FluidShapes.md)
                                        .background(MomentumSpectrum.Green.copy(alpha = 0.12f))
                                        .border(1.dp, MomentumSpectrum.Green.copy(alpha = 0.30f), FluidShapes.md)
                                        .fluidInteractive(
                                            pressScale = 0.95f,
                                            onClick = onRequestHealthPermissions
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = null,
                                            tint = MomentumSpectrum.Green,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Grant Health Connect Access",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "Tap to authorize Android Health Connect sync",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Health Metric Types
                            Text(
                                text = "HEALTH METRIC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HealthMetricType.entries.forEach { metric ->
                                    FluidFilterChip(
                                        label = metric.label,
                                        selected = selectedHealthMetric == metric,
                                        onClick = {
                                            selectedHealthMetric = metric
                                            healthTargetValue = metric.defaultTarget
                                            healthCustomTargetText = if (metric == HealthMetricType.SLEEP_HOURS ||
                                                metric == HealthMetricType.DISTANCE_KM ||
                                                metric == HealthMetricType.HYDRATION_LITERS ||
                                                metric == HealthMetricType.WEIGHT_KG
                                            ) {
                                                metric.defaultTarget.toString()
                                            } else {
                                                metric.defaultTarget.toLong().toString()
                                            }
                                        },
                                        leadingIcon = Icons.Rounded.DirectionsRun
                                    )
                                }
                            }

                            // Condition: At Least (>=) vs At Most (<=)
                            Text(
                                text = "CONDITION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoalComparison.entries.forEach { comp ->
                                    FluidFilterChip(
                                        label = comp.label,
                                        selected = healthComparison == comp,
                                        onClick = { healthComparison = comp }
                                    )
                                }
                            }

                            // Target Value: Presets + Custom
                            Text(
                                text = "TARGET (${selectedHealthMetric.unit.uppercase()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                selectedHealthMetric.presetTargets.forEach { preset ->
                                    FluidFilterChip(
                                        label = "${selectedHealthMetric.formatValue(preset)} ${selectedHealthMetric.unit}",
                                        selected = healthTargetValue == preset,
                                        onClick = {
                                            healthTargetValue = preset
                                            healthCustomTargetText = if (selectedHealthMetric == HealthMetricType.SLEEP_HOURS ||
                                                selectedHealthMetric == HealthMetricType.DISTANCE_KM ||
                                                selectedHealthMetric == HealthMetricType.HYDRATION_LITERS ||
                                                selectedHealthMetric == HealthMetricType.WEIGHT_KG
                                            ) {
                                                preset.toString()
                                            } else {
                                                preset.toLong().toString()
                                            }
                                        }
                                    )
                                }

                                // Custom numeric input
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(FluidShapes.full)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                        .border(1.dp, colors.divider, FluidShapes.full)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = "Custom:", fontSize = 12.sp, color = colors.textSecondary)
                                        BasicTextField(
                                            value = healthCustomTargetText,
                                            onValueChange = { newText ->
                                                val filtered = newText.filter { it.isDigit() || it == '.' }
                                                healthCustomTargetText = filtered
                                                filtered.toDoubleOrNull()?.let {
                                                    if (it > 0.0) healthTargetValue = it
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            ),
                                            cursorBrush = SolidColor(colors.accent),
                                            modifier = Modifier.widthIn(min = 40.dp, max = 80.dp)
                                        )
                                        Text(text = selectedHealthMetric.unit, fontSize = 12.sp, color = colors.textTertiary)
                                    }
                                }
                            }
                        }
                    }

                    GoalCategory.APP_USAGE -> {
                        // APP USAGE LIMIT CONFIGURATION
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
                        ) {
                            if (!hasUsagePermission) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(FluidShapes.md)
                                        .background(MomentumSpectrum.Purple.copy(alpha = 0.12f))
                                        .border(1.dp, MomentumSpectrum.Purple.copy(alpha = 0.30f), FluidShapes.md)
                                        .fluidInteractive(
                                            pressScale = 0.95f,
                                            onClick = onRequestUsagePermission
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = null,
                                            tint = MomentumSpectrum.Purple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Grant App Usage Access",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "Tap to grant PACKAGE_USAGE_STATS permission to track screen time & opens",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Select App (Installed / Popular Apps)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TARGET APP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = colors.textTertiary
                                )
                                Text(
                                    text = selectedAppName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accent
                                )
                            }

                            // Horizontal scroll of app chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                effectiveAppList.forEach { appInfo ->
                                    val isSelected = selectedPackageName == appInfo.packageName
                                    FluidFilterChip(
                                        label = appInfo.appName,
                                        selected = isSelected,
                                        onClick = {
                                            selectedPackageName = appInfo.packageName
                                            selectedAppName = appInfo.appName
                                        },
                                        leadingIcon = Icons.Rounded.PhoneAndroid
                                    )
                                }
                            }

                            // Usage Metric: Active Screen Time vs Times Opened
                            Text(
                                text = "USAGE METRIC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppUsageMetricType.entries.forEach { metric ->
                                    FluidFilterChip(
                                        label = "${metric.label} (${metric.unit})",
                                        selected = appUsageMetric == metric,
                                        onClick = {
                                            appUsageMetric = metric
                                            val defaultVal = metric.defaultLimit
                                            appTargetValue = defaultVal
                                            appCustomTargetText = defaultVal.toLong().toString()
                                        }
                                    )
                                }
                            }

                            // Condition: At Most (<= limit) vs At Least (>= productivity)
                            Text(
                                text = "CONDITION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FluidFilterChip(
                                    label = "Limit (≤ At Most)",
                                    selected = appComparison == GoalComparison.AT_MOST,
                                    onClick = { appComparison = GoalComparison.AT_MOST }
                                )
                                FluidFilterChip(
                                    label = "Target (≥ At Least)",
                                    selected = appComparison == GoalComparison.AT_LEAST,
                                    onClick = { appComparison = GoalComparison.AT_LEAST }
                                )
                            }

                            // Target Value: Presets + Custom
                            Text(
                                text = "DAILY TARGET / LIMIT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = colors.textTertiary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val presets = if (appUsageMetric == AppUsageMetricType.ACTIVE_MINUTES) {
                                    listOf(15.0, 30.0, 45.0, 60.0, 90.0, 120.0)
                                } else {
                                    listOf(3.0, 5.0, 10.0, 15.0, 20.0, 30.0)
                                }
                                presets.forEach { preset ->
                                    val unitLabel = if (appUsageMetric == AppUsageMetricType.ACTIVE_MINUTES) "min" else "opens"
                                    FluidFilterChip(
                                        label = "${preset.toInt()} $unitLabel",
                                        selected = appTargetValue == preset,
                                        onClick = {
                                            appTargetValue = preset
                                            appCustomTargetText = preset.toInt().toString()
                                        }
                                    )
                                }

                                // Custom numeric input
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(FluidShapes.full)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                        .border(1.dp, colors.divider, FluidShapes.full)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = "Custom:", fontSize = 12.sp, color = colors.textSecondary)
                                        BasicTextField(
                                            value = appCustomTargetText,
                                            onValueChange = { newText ->
                                                val filtered = newText.filter { it.isDigit() }
                                                appCustomTargetText = filtered
                                                filtered.toDoubleOrNull()?.let {
                                                    if (it > 0.0) appTargetValue = it
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            ),
                                            cursorBrush = SolidColor(colors.accent),
                                            modifier = Modifier.widthIn(min = 36.dp, max = 64.dp)
                                        )
                                        Text(
                                            text = if (appUsageMetric == AppUsageMetricType.ACTIVE_MINUTES) "min" else "opens",
                                            fontSize = 12.sp,
                                            color = colors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.md))

                // 3. GOAL TITLE FIELD
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GOAL TITLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = colors.textTertiary
                        )
                        if (isUserEditedTitle) {
                            Text(
                                text = "Reset to auto",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                                modifier = Modifier.clickable {
                                    isUserEditedTitle = false
                                    customTitle = ""
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FluidShapes.md)
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                            .border(1.dp, colors.divider, FluidShapes.md)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = if (isUserEditedTitle) customTitle else autoGeneratedTitle,
                            onValueChange = {
                                customTitle = it
                                isUserEditedTitle = true
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.xl))

                // 4. SAVE BUTTON
                FluidPillButton(
                    text = "Add to Goal Bar",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (selectedCategory) {
                            GoalCategory.TASK_COMPLETION -> {
                                onSaveGoal(
                                    finalTitle,
                                    GoalCategory.TASK_COMPLETION,
                                    taskComparison,
                                    taskTargetValue,
                                    taskPeriod,
                                    taskProjectId,
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            }
                            GoalCategory.HEALTH_CONNECT -> {
                                onSaveGoal(
                                    finalTitle,
                                    GoalCategory.HEALTH_CONNECT,
                                    healthComparison,
                                    healthTargetValue,
                                    GoalPeriod.DAILY,
                                    null,
                                    selectedHealthMetric,
                                    null,
                                    null,
                                    null
                                )
                            }
                            GoalCategory.APP_USAGE -> {
                                onSaveGoal(
                                    finalTitle,
                                    GoalCategory.APP_USAGE,
                                    appComparison,
                                    appTargetValue,
                                    GoalPeriod.DAILY,
                                    null,
                                    null,
                                    selectedPackageName,
                                    selectedAppName,
                                    appUsageMetric
                                )
                            }
                        }
                        onDismiss()
                    },
                    isPrimary = true,
                    leadingIcon = Icons.Rounded.Check,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(FluidSpacing.sm))
            }
        }
    }
}

package com.flow.todo.data.usage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.flow.todo.MainActivity
import com.flow.todo.domain.model.AppUsageShieldAlert
import com.flow.todo.domain.model.EvaluatedGoal
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.ShieldSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Locale

class AppUsageShieldMonitor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dismissedTimestampByGoalId = mutableMapOf<String, Long>()
    private var testCycleCounter = 0

    private val _alerts = MutableStateFlow<List<AppUsageShieldAlert>>(emptyList())
    val alerts: StateFlow<List<AppUsageShieldAlert>> = _alerts.asStateFlow()

    private val _activeBannerAlert = MutableStateFlow<AppUsageShieldAlert?>(null)
    val activeBannerAlert: StateFlow<AppUsageShieldAlert?> = _activeBannerAlert.asStateFlow()

    init {
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flow App Usage Shield",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle 80% usage nudges and 100% daily limit alerts for App Usage Goals"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun evaluateGoals(evaluatedGoals: List<EvaluatedGoal>, shieldEnabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        val todayEpoch = LocalDate.now().toEpochDay()

        val limitGoals = evaluatedGoals.filter {
            it.goal.category == GoalCategory.APP_USAGE &&
                it.goal.comparison == GoalComparison.AT_MOST &&
                it.goal.targetValue > 0.0
        }

        val currentAlerts = limitGoals.mapNotNull { evaluated ->
            val ratio = (evaluated.currentValue / evaluated.targetValue).toFloat()
            val appName = evaluated.goal.targetAppName?.takeIf { it.isNotBlank() } ?: evaluated.goal.title
            val pkg = evaluated.goal.targetPackageName ?: ""
            val pct = (ratio * 100f).toInt()

            when {
                ratio >= 1.0f -> AppUsageShieldAlert(
                    goalId = evaluated.goal.id,
                    appName = appName,
                    packageName = pkg,
                    severity = ShieldSeverity.LIMIT_100,
                    usageRatio = ratio,
                    formattedStatus = "${evaluated.formattedProgressText} ($pct%)",
                    timestampMillis = nowMillis
                )
                ratio >= 0.80f -> AppUsageShieldAlert(
                    goalId = evaluated.goal.id,
                    appName = appName,
                    packageName = pkg,
                    severity = ShieldSeverity.WARNING_80,
                    usageRatio = ratio,
                    formattedStatus = "${evaluated.formattedProgressText} ($pct%)",
                    timestampMillis = nowMillis
                )
                else -> null
            }
        }.sortedByDescending { it.usageRatio }

        _alerts.value = currentAlerts

        if (!shieldEnabled) {
            _activeBannerAlert.value = null
            return
        }

        // Fire OS notification once per day per goal threshold
        currentAlerts.forEach { alert ->
            val notifKey = "${todayEpoch}_${alert.goalId}_${alert.severity.name}"
            if (!prefs.getBoolean(notifKey, false)) {
                prefs.edit().putBoolean(notifKey, true).apply()
                postSystemNotification(alert)
            }
        }

        val topUndismissed = currentAlerts.firstOrNull { alert ->
            val dismissedAt = dismissedTimestampByGoalId[alert.goalId] ?: 0L
            (nowMillis - dismissedAt) > SNOOZE_DURATION_MS
        }

        _activeBannerAlert.value = topUndismissed
    }

    fun triggerTestShieldNudge(evaluatedGoals: List<EvaluatedGoal>) {
        val nowMillis = System.currentTimeMillis()
        val appGoal = evaluatedGoals.firstOrNull { it.goal.category == GoalCategory.APP_USAGE }
        val isLimitReached = (testCycleCounter % 2) == 1
        testCycleCounter++

        val alert = if (appGoal != null) {
            val appName = appGoal.goal.targetAppName?.takeIf { it.isNotBlank() } ?: appGoal.goal.title
            val pkg = appGoal.goal.targetPackageName ?: "com.instagram.android"
            val ratio = if (isLimitReached) 1.04f else 0.86f
            val simulatedCurrent = (appGoal.targetValue * ratio).toInt()
            val targetInt = appGoal.targetValue.toInt().coerceAtLeast(1)
            val unit = appGoal.goal.appUsageMetric?.unit ?: "min"
            val pct = (ratio * 100f).toInt()
            AppUsageShieldAlert(
                goalId = appGoal.goal.id,
                appName = appName,
                packageName = pkg,
                severity = if (isLimitReached) ShieldSeverity.LIMIT_100 else ShieldSeverity.WARNING_80,
                usageRatio = ratio,
                formattedStatus = String.format(
                    Locale.US,
                    "%d / ≤ %d %s (%d%%)",
                    simulatedCurrent,
                    targetInt,
                    unit,
                    pct
                ),
                timestampMillis = nowMillis
            )
        } else {
            if (isLimitReached) {
                AppUsageShieldAlert(
                    goalId = "test_shield_goal_100",
                    appName = "Instagram",
                    packageName = "com.instagram.android",
                    severity = ShieldSeverity.LIMIT_100,
                    usageRatio = 1.0f,
                    formattedStatus = "30 / ≤ 30 min active (100%)",
                    timestampMillis = nowMillis
                )
            } else {
                AppUsageShieldAlert(
                    goalId = "test_shield_goal_80",
                    appName = "Instagram",
                    packageName = "com.instagram.android",
                    severity = ShieldSeverity.WARNING_80,
                    usageRatio = 0.87f,
                    formattedStatus = "26 / ≤ 30 min active (87%)",
                    timestampMillis = nowMillis
                )
            }
        }

        dismissedTimestampByGoalId.remove(alert.goalId)
        _activeBannerAlert.value = alert
        postSystemNotification(alert)
    }

    fun dismissBannerAlert(goalId: String) {
        dismissedTimestampByGoalId[goalId] = System.currentTimeMillis()
        if (_activeBannerAlert.value?.goalId == goalId) {
            _activeBannerAlert.value = null
        }
    }

    private fun postSystemNotification(alert: AppUsageShieldAlert) {
        runCatching {
            ensureNotificationChannel()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                alert.goalId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when (alert.severity) {
                ShieldSeverity.LIMIT_100 -> "🛡️ ${alert.appName}: Daily Limit Reached"
                ShieldSeverity.WARNING_80 -> "⏳ ${alert.appName}: 80% Gentle Usage Nudge"
            }
            val body = when (alert.severity) {
                ShieldSeverity.LIMIT_100 ->
                    "You've reached ${alert.formattedStatus}. Time to step back and stay in the Flow."
                ShieldSeverity.WARNING_80 ->
                    "Heads up — you're at ${alert.formattedStatus} for today."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(alert.goalId.hashCode(), notification)
        }
    }

    companion object {
        private const val PREFS_NAME = "flow_usage_shield_prefs"
        private const val CHANNEL_ID = "flow_usage_shield_channel"
        private const val SNOOZE_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }
}

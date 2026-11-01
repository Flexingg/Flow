package com.flow.todo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import com.flow.todo.MainActivity
import com.flow.todo.R
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.domain.model.DEFAULT_MOMENTUM_STOPS_HEX
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.system.FlowQuickSettingsTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive Home Screen & Lock Screen Glass Widget rendering:
 * - Living 5-Stage Flow Goal Bar with glowing comet tip
 * - Mini 12-week x 7-day GitHub-style contribution heatmap
 * - Voice Capture & Quick Capture glass pill buttons
 * - Today/Next top task with 1-tap interactive completion
 */
class FlowGlassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COMPLETE_TOP_TASK) {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID)
            if (taskId.isNullOrBlank()) {
                val openTodayIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_TODAY
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                runCatching { context.startActivity(openTodayIntent) }
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = FlowDatabase.getInstance(context).flowDao()
                    val allTasks = dao.getAllTasks()
                    val target = allTasks.firstOrNull { it.id == taskId }
                    if (target != null && !target.isCompleted) {
                        val now = System.currentTimeMillis()
                        dao.upsertTask(
                            target.copy(
                                isCompleted = true,
                                completedAt = now
                            )
                        )
                    }

                    val updatedTasks = dao.getAllTasks()
                    val nextCandidate = updatedTasks
                        .filter { !it.isCompleted }
                        .sortedWith(
                            compareByDescending<com.flow.todo.data.local.TaskEntity> {
                                it.bucketName == GtdBucket.SCHEDULED.name || it.bucketName == GtdBucket.NEXT_ACTION.name
                            }
                                .thenByDescending { it.isFlagged }
                                .thenBy { it.dueDateEpochDay ?: Long.MAX_VALUE }
                                .thenByDescending { it.createdAt }
                        )
                        .firstOrNull()

                    val prefs = context.getSharedPreferences(WIDGET_CACHE_PREFS, Context.MODE_PRIVATE)
                    val prevCompletedToday = prefs.getInt(KEY_COMPLETED_TODAY, 0)
                    val prevTotalToday = prefs.getInt(KEY_TOTAL_TODAY, 0)
                    val newCompletedToday = prevCompletedToday + 1
                    val newTotalToday = maxOf(prevTotalToday, newCompletedToday)

                    prefs.edit()
                        .putInt(KEY_COMPLETED_TODAY, newCompletedToday)
                        .putInt(KEY_TOTAL_TODAY, newTotalToday)
                        .putString(KEY_TOP_TASK_ID, nextCandidate?.id)
                        .putString(KEY_TOP_TASK_TITLE, nextCandidate?.title)
                        .apply()

                    requestWidgetUpdate(context)
                    FlowQuickSettingsTileService.requestTileUpdate(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.flow.todo.ACTION_QUICK_CAPTURE"
        const val ACTION_VOICE_CAPTURE = "com.flow.todo.ACTION_VOICE_CAPTURE"
        const val ACTION_OPEN_GOALS = "com.flow.todo.ACTION_OPEN_GOALS"
        const val ACTION_OPEN_TODAY = "com.flow.todo.ACTION_OPEN_TODAY"
        const val ACTION_COMPLETE_TOP_TASK = "com.flow.todo.ACTION_WIDGET_COMPLETE_TOP_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val WIDGET_CACHE_PREFS = "flow_widget_cache_prefs"

        const val KEY_MOMENTUM_PROGRESS = "momentum_progress"
        const val KEY_COMPLETED_TODAY = "completed_today"
        const val KEY_TOTAL_TODAY = "total_today"
        const val KEY_COMPLETED_GOALS = "completed_goals"
        const val KEY_TOTAL_GOALS = "total_goals"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_TOP_TASK_ID = "top_task_id"
        const val KEY_TOP_TASK_TITLE = "top_task_title"
        const val KEY_HEATMAP_LEVELS = "heatmap_levels"
        const val KEY_STOP_HEXES = "stop_hexes"

        fun saveWidgetSnapshotAndRefresh(
            context: Context,
            momentumProgress: Float,
            completedToday: Int,
            totalToday: Int,
            completedGoals: Int,
            totalGoals: Int,
            streakDays: Int,
            topTaskId: String?,
            topTaskTitle: String?,
            recentHeatmapLevels: List<Int>,
            stopHexes: List<Long>
        ) {
            val prefs = context.getSharedPreferences(WIDGET_CACHE_PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_MOMENTUM_PROGRESS, momentumProgress.coerceIn(0f, 1f))
                .putInt(KEY_COMPLETED_TODAY, completedToday)
                .putInt(KEY_TOTAL_TODAY, totalToday)
                .putInt(KEY_COMPLETED_GOALS, completedGoals)
                .putInt(KEY_TOTAL_GOALS, totalGoals)
                .putInt(KEY_STREAK_DAYS, streakDays)
                .putString(KEY_TOP_TASK_ID, topTaskId)
                .putString(KEY_TOP_TASK_TITLE, topTaskTitle)
                .putString(KEY_HEATMAP_LEVELS, recentHeatmapLevels.takeLast(84).joinToString(","))
                .putString(KEY_STOP_HEXES, stopHexes.joinToString(","))
                .apply()

            requestWidgetUpdate(context)
            FlowQuickSettingsTileService.requestTileUpdate(context)
        }

        fun requestWidgetUpdate(context: Context) {
            runCatching {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, FlowGlassWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    updateWidgets(context, appWidgetManager, ids)
                }
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val prefs = context.getSharedPreferences(WIDGET_CACHE_PREFS, Context.MODE_PRIVATE)
            val momentumProgress = prefs.getFloat(KEY_MOMENTUM_PROGRESS, 0.42f).coerceIn(0f, 1f)
            val completedToday = prefs.getInt(KEY_COMPLETED_TODAY, 0)
            val totalToday = prefs.getInt(KEY_TOTAL_TODAY, 0)
            val completedGoals = prefs.getInt(KEY_COMPLETED_GOALS, 0)
            val totalGoals = prefs.getInt(KEY_TOTAL_GOALS, 0)
            val streakDays = prefs.getInt(KEY_STREAK_DAYS, 0)
            val topTaskId = prefs.getString(KEY_TOP_TASK_ID, null)
            val topTaskTitle = prefs.getString(KEY_TOP_TASK_TITLE, null)

            val rawLevels = prefs.getString(KEY_HEATMAP_LEVELS, null)
            val recentHeatmapLevels = rawLevels
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 4) }
                ?.takeIf { it.isNotEmpty() }
                ?: List(84) { 0 }

            val rawHexes = prefs.getString(KEY_STOP_HEXES, null)
            val stopHexes = rawHexes
                ?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.takeIf { it.size >= 2 }
                ?: DEFAULT_MOMENTUM_STOPS_HEX

            val bitmap = renderGlassWidgetBitmap(
                momentumProgress = momentumProgress,
                completedToday = completedToday,
                totalToday = totalToday,
                completedGoals = completedGoals,
                totalGoals = totalGoals,
                streakDays = streakDays,
                topTaskTitle = topTaskTitle,
                recentHeatmapLevels = recentHeatmapLevels,
                stopHexes = stopHexes
            )

            val openGoalsPending = buildActivityPendingIntent(context, ACTION_OPEN_GOALS, 201)
            val voiceCapturePending = buildActivityPendingIntent(context, ACTION_VOICE_CAPTURE, 202)
            val quickCapturePending = buildActivityPendingIntent(context, ACTION_QUICK_CAPTURE, 203)
            val openTodayPending = buildActivityPendingIntent(context, ACTION_OPEN_TODAY, 204)

            val completeTaskPending = if (!topTaskId.isNullOrBlank()) {
                val completeIntent = Intent(context, FlowGlassWidgetProvider::class.java).apply {
                    action = ACTION_COMPLETE_TOP_TASK
                    putExtra(EXTRA_TASK_ID, topTaskId)
                }
                PendingIntent.getBroadcast(
                    context,
                    205,
                    completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                quickCapturePending
            }

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_flow_glass)
                views.setImageViewBitmap(R.id.widget_canvas_image, bitmap)
                views.setOnClickPendingIntent(R.id.widget_btn_open_goals, openGoalsPending)
                views.setOnClickPendingIntent(R.id.widget_btn_voice_capture, voiceCapturePending)
                views.setOnClickPendingIntent(R.id.widget_btn_quick_capture, quickCapturePending)
                views.setOnClickPendingIntent(R.id.widget_btn_complete_task, completeTaskPending)
                views.setOnClickPendingIntent(R.id.widget_btn_open_today, openTodayPending)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun buildActivityPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun interpolateSpectrumColor(progress: Float, stopHexes: List<Long>): Int {
            val hexes = if (stopHexes.size >= 2) stopHexes else DEFAULT_MOMENTUM_STOPS_HEX
            val clamped = progress.coerceIn(0f, 1f)
            val scaled = clamped * (hexes.size - 1)
            val idx = scaled.toInt().coerceIn(0, hexes.size - 2)
            val frac = (scaled - idx).coerceIn(0f, 1f)
            val c1 = hexes[idx].toInt()
            val c2 = hexes[idx + 1].toInt()
            return blendColors(c1, c2, frac)
        }

        private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
            val inv = 1f - ratio
            val a = (Color.alpha(c1) * inv + Color.alpha(c2) * ratio).roundToInt().coerceIn(0, 255)
            val r = (Color.red(c1) * inv + Color.red(c2) * ratio).roundToInt().coerceIn(0, 255)
            val g = (Color.green(c1) * inv + Color.green(c2) * ratio).roundToInt().coerceIn(0, 255)
            val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).roundToInt().coerceIn(0, 255)
            return Color.argb(a, r, g, b)
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return Color.argb(
                alpha.coerceIn(0, 255),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
        }

        private fun renderGlassWidgetBitmap(
            momentumProgress: Float,
            completedToday: Int,
            totalToday: Int,
            completedGoals: Int,
            totalGoals: Int,
            streakDays: Int,
            topTaskTitle: String?,
            recentHeatmapLevels: List<Int>,
            stopHexes: List<Long>
        ): Bitmap {
            val width = 800
            val height = 420
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val clampedProgress = momentumProgress.coerceIn(0f, 1f)
            val activeMomentumColor = interpolateSpectrumColor(clampedProgress, stopHexes)

            val cardRect = RectF(6f, 6f, width - 6f, height - 6f)
            val cornerRadius = 52f

            // 1. Deep frosted glass card background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    Color.parseColor("#E614161C"),
                    Color.parseColor("#F20C0D12"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Radial ambient glow at top-left in active momentum color
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    160f,
                    90f,
                    440f,
                    intArrayOf(
                        withAlpha(activeMomentumColor, 76),
                        withAlpha(activeMomentumColor, 22),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, glowPaint)

            // 3. Crisp 2.5f px rounded contour glow border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(
                        withAlpha(Color.WHITE, 120),
                        withAlpha(activeMomentumColor, 140),
                        withAlpha(Color.WHITE, 36)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

            // 4. Top Header (Left: FLOW GOAL BAR label, percentage, goals & streak)
            val headerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = activeMomentumColor
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.12f
            }
            canvas.drawText("FLOW GOAL BAR", 40f, 52f, headerLabelPaint)

            val pctText = "${(clampedProgress * 100f).toInt()}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(pctText, 40f, 102f, pctPaint)

            val pctWidth = pctPaint.measureText(pctText)
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B8FFFFFF")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val subtitleText = "$completedGoals/$totalGoals goals • 🔥 ${streakDays}d streak"
            canvas.drawText(subtitleText, 40f + pctWidth + 18f, 96f, subtitlePaint)

            // 5. Top-right sleek glass pill buttons (Voice Capture 🎙️ and Quick Capture +)
            val voiceBtnRect = RectF(width - 208f, 28f, width - 124f, 96f)
            val quickBtnRect = RectF(width - 112f, 28f, width - 28f, 96f)

            val pillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#24FFFFFF")
            }
            val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
                color = Color.parseColor("#45FFFFFF")
            }
            canvas.drawRoundRect(voiceBtnRect, 34f, 34f, pillFillPaint)
            canvas.drawRoundRect(voiceBtnRect, 34f, 34f, pillStrokePaint)

            val quickFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(activeMomentumColor, 68)
            }
            val quickStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = withAlpha(activeMomentumColor, 190)
            }
            canvas.drawRoundRect(quickBtnRect, 34f, 34f, quickFillPaint)
            canvas.drawRoundRect(quickBtnRect, 34f, 34f, quickStrokePaint)

            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 30f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("🎙️", voiceBtnRect.centerX(), voiceBtnRect.centerY() + 10f, iconPaint)

            val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 40f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("+", quickBtnRect.centerX(), quickBtnRect.centerY() + 14f, plusPaint)

            // 6. Middle Left: Luminous 5-Stage Goal Momentum Bar
            val barLeft = 40f
            val barRight = 455f
            val barTop = 158f
            val barBottom = 190f
            val barRadius = 16f
            val trackRect = RectF(barLeft, barTop, barRight, barBottom)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#22FFFFFF")
            }
            canvas.drawRoundRect(trackRect, barRadius, barRadius, trackPaint)

            val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = Color.parseColor("#33FFFFFF")
            }
            canvas.drawRoundRect(trackRect, barRadius, barRadius, trackBorderPaint)

            val fillFraction = clampedProgress.coerceAtLeast(0.04f)
            val fillRight = barLeft + (barRight - barLeft) * fillFraction
            val fillRect = RectF(barLeft, barTop, fillRight, barBottom)

            val spectrumColors = (if (stopHexes.size >= 2) stopHexes else DEFAULT_MOMENTUM_STOPS_HEX)
                .map { it.toInt() }
                .toIntArray()
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    barLeft,
                    barTop,
                    barRight,
                    barBottom,
                    spectrumColors,
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(fillRect, barRadius, barRadius, fillPaint)

            // Glowing white-tinted comet circle at the tip
            val cometX = fillRight.coerceIn(barLeft + barRadius, barRight - barRadius)
            val cometY = (barTop + barBottom) / 2f
            val cometHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cometX,
                    cometY,
                    28f,
                    intArrayOf(
                        withAlpha(Color.WHITE, 210),
                        withAlpha(activeMomentumColor, 140),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cometX, cometY, 28f, cometHaloPaint)

            val cometCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            canvas.drawCircle(cometX, cometY, 8.5f, cometCorePaint)

            // Caption below bar
            val todayMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#99FFFFFF")
                textSize = 20f
            }
            val todaySummary = if (totalToday > 0) {
                "Today: $completedToday/$totalToday tasks completed"
            } else {
                "5-Stage Spectrum • Tap to inspect goals"
            }
            canvas.drawText(todaySummary, barLeft, 232f, todayMetaPaint)

            // 7. Middle Right: Mini 12-Week x 7-Day GitHub Contribution Heatmap
            val cols = 12
            val rows = 7
            val cellSize = 16f
            val cellGap = 5f
            val gridWidth = cols * cellSize + (cols - 1) * cellGap
            val gridLeft = width - 36f - gridWidth
            val gridTop = 122f

            val paddedLevels = if (recentHeatmapLevels.size >= 84) {
                recentHeatmapLevels.takeLast(84)
            } else {
                List(84 - recentHeatmapLevels.size) { 0 } + recentHeatmapLevels
            }

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val stopList = if (stopHexes.size >= 5) stopHexes else DEFAULT_MOMENTUM_STOPS_HEX

            for (col in 0 until cols) {
                for (row in 0 until rows) {
                    val index = col * rows + row
                    val level = paddedLevels.getOrElse(index) { 0 }.coerceIn(0, 4)
                    cellPaint.color = when (level) {
                        0 -> Color.parseColor("#1FFFFFFF")
                        1 -> withAlpha(stopList[1].toInt(), 155)
                        2 -> withAlpha(stopList[2].toInt(), 190)
                        3 -> withAlpha(stopList[3].toInt(), 220)
                        else -> stopList[4].toInt()
                    }
                    val cx = gridLeft + col * (cellSize + cellGap)
                    val cy = gridTop + row * (cellSize + cellGap)
                    canvas.drawRoundRect(
                        RectF(cx, cy, cx + cellSize, cy + cellSize),
                        4.5f,
                        4.5f,
                        cellPaint
                    )
                }
            }

            // 8. Bottom Row: Next Action / Today Task Strip (height = 86f px)
            val stripHeight = 86f
            val stripTop = height - 24f - stripHeight
            val stripRect = RectF(28f, stripTop, width - 28f, stripTop + stripHeight)

            val stripFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#24FFFFFF")
            }
            val stripBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
                color = Color.parseColor("#38FFFFFF")
            }
            canvas.drawRoundRect(stripRect, 32f, 32f, stripFillPaint)
            canvas.drawRoundRect(stripRect, 32f, 32f, stripBorderPaint)

            val checkCx = stripRect.left + 44f
            val checkCy = stripRect.centerY()
            val hasTask = !topTaskTitle.isNullOrBlank()

            if (hasTask) {
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.2f
                    color = activeMomentumColor
                }
                val ringFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(activeMomentumColor, 44)
                }
                canvas.drawCircle(checkCx, checkCy, 19f, ringFillPaint)
                canvas.drawCircle(checkCx, checkCy, 19f, ringPaint)
            } else {
                val doneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(activeMomentumColor, 180)
                }
                canvas.drawCircle(checkCx, checkCy, 19f, doneFillPaint)
                val checkMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("✓", checkCx, checkCy + 8f, checkMarkPaint)
            }

            val rawTaskLine = if (hasTask) {
                "Tap circle to complete • ${topTaskTitle!!.trim()}"
            } else {
                "All tasks caught up today ✨ Tap + to capture"
            }
            val displayTaskLine = if (rawTaskLine.length > 52) {
                rawTaskLine.take(50) + "…"
            } else {
                rawTaskLine
            }

            val taskTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(displayTaskLine, stripRect.left + 84f, checkCy + 8f, taskTextPaint)

            return bitmap
        }
    }
}

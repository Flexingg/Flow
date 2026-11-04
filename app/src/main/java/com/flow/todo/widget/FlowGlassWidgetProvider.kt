package com.flow.todo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resizable, System-Color-Adaptive Living Flow Glass Widget:
 * - Freely resizable from compact 2x1 pill up to 4x4 expanded dashboard
 * - Automatically changes layout format (COMPACT_PILL, SHORT_WIDE, STANDARD_GLASS, EXPANDED_DASHBOARD)
 *   based on current widget dimensions (onAppWidgetOptionsChanged)
 * - Always adapts its frosted liquid-glass tint, contour border, radial glow, and accents to the
 *   live Android System Color (Material You Dynamic Color + Shizuku System Palette)
 * - Clean single '+' Quick Capture action (no microphone button) + 1-tap Top Task completion
 */
class FlowGlassWidgetProvider : AppWidgetProvider() {

    private enum class WidgetSizeFormat {
        COMPACT_PILL,
        SHORT_WIDE,
        STANDARD_GLASS,
        EXPANDED_DASHBOARD
    }

    internal data class SystemGlassPalette(
        val systemAccent: Int,
        val glassBgTop: Int,
        val glassBgBottom: Int,
        val glassSheenTop: Int,
        val surfacePillFill: Int,
        val surfacePillStroke: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textTertiary: Int,
        val isDark: Boolean
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_WALLPAPER_CHANGED -> {
                requestWidgetUpdate(context)
            }

            ACTION_COMPLETE_TOP_TASK -> {
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
                .putString(KEY_HEATMAP_LEVELS, recentHeatmapLevels.takeLast(140).joinToString(","))
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

        /**
         * Resolves the live Android System Color (from Shizuku/Secure Settings overlay if active,
         * or Android 12+ Material You dynamic system_accent1_*, with fallback to the active momentum color)
         * and constructs a frosted ColorOS 17 liquid-glass palette infused with the system color.
         */
        internal fun resolveSystemGlassPalette(
            context: Context,
            fallbackMomentumColor: Int
        ): SystemGlassPalette {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isDark = uiMode != Configuration.UI_MODE_NIGHT_NO

            // 1. Check if theme_customization_overlay_packages has a custom system accent hex (e.g. set via Shizuku)
            val customOverlayAccent: Int? = runCatching {
                val overlayJson = Settings.Secure.getString(
                    context.contentResolver,
                    "theme_customization_overlay_packages"
                )
                if (!overlayJson.isNullOrBlank()) {
                    val obj = JSONObject(overlayJson)
                    val hex = obj.optString("android.theme.customization.accent_color")
                        .ifBlank { obj.optString("android.theme.customization.system_palette") }
                        .removePrefix("#")
                        .trim()
                    if (hex.length == 6) {
                        Color.parseColor("#FF$hex")
                    } else if (hex.length == 8) {
                        Color.parseColor("#$hex")
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.getOrNull()

            // 2. Query Android 12+ Material You Dynamic System Accent & Neutral colors
            val dynamicSystemAccent: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    if (isDark) {
                        context.getColor(android.R.color.system_accent1_300)
                    } else {
                        context.getColor(android.R.color.system_accent1_600)
                    }
                }.getOrNull()
            } else {
                null
            }

            val dynamicNeutralSurface: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    if (isDark) {
                        context.getColor(android.R.color.system_neutral1_900)
                    } else {
                        context.getColor(android.R.color.system_neutral1_50)
                    }
                }.getOrNull()
            } else {
                null
            }

            val systemAccent = customOverlayAccent ?: dynamicSystemAccent ?: fallbackMomentumColor

            return if (isDark) {
                val baseTop = dynamicNeutralSurface?.let { blendColors(Color.parseColor("#FF14161E"), it, 0.55f) }
                    ?: Color.parseColor("#FF14161E")
                val baseBottom = dynamicNeutralSurface?.let { blendColors(Color.parseColor("#FF0B0D13"), it, 0.45f) }
                    ?: Color.parseColor("#FF0B0D13")

                // Infuse the frosted glass surface with the live system color while keeping deep glass translucency
                val tintedTop = withAlpha(blendColors(baseTop, systemAccent, 0.24f), 228)
                val tintedBottom = withAlpha(blendColors(baseBottom, systemAccent, 0.14f), 240)

                SystemGlassPalette(
                    systemAccent = systemAccent,
                    glassBgTop = tintedTop,
                    glassBgBottom = tintedBottom,
                    glassSheenTop = withAlpha(blendColors(Color.WHITE, systemAccent, 0.35f), 42),
                    surfacePillFill = withAlpha(blendColors(Color.WHITE, systemAccent, 0.30f), 38),
                    surfacePillStroke = withAlpha(blendColors(Color.WHITE, systemAccent, 0.45f), 76),
                    textPrimary = Color.WHITE,
                    textSecondary = Color.parseColor("#CCFFFFFF"),
                    textTertiary = Color.parseColor("#99FFFFFF"),
                    isDark = true
                )
            } else {
                val baseTop = dynamicNeutralSurface?.let { blendColors(Color.parseColor("#FFF7F9FC"), it, 0.65f) }
                    ?: Color.parseColor("#FFF7F9FC")
                val baseBottom = dynamicNeutralSurface?.let { blendColors(Color.parseColor("#FFEDF1F8"), it, 0.55f) }
                    ?: Color.parseColor("#FFEDF1F8")

                val tintedTop = withAlpha(blendColors(baseTop, systemAccent, 0.16f), 232)
                val tintedBottom = withAlpha(blendColors(baseBottom, systemAccent, 0.10f), 242)

                SystemGlassPalette(
                    systemAccent = systemAccent,
                    glassBgTop = tintedTop,
                    glassBgBottom = tintedBottom,
                    glassSheenTop = withAlpha(Color.WHITE, 140),
                    surfacePillFill = withAlpha(blendColors(Color.WHITE, systemAccent, 0.22f), 155),
                    surfacePillStroke = withAlpha(blendColors(Color.WHITE, systemAccent, 0.40f), 175),
                    textPrimary = Color.parseColor("#EB11141C"),
                    textSecondary = Color.parseColor("#A611141C"),
                    textTertiary = Color.parseColor("#7811141C"),
                    isDark = false
                )
            }
        }

        private fun classifyWidgetFormat(widthDp: Int, heightDp: Int): WidgetSizeFormat {
            return when {
                heightDp < 105 || widthDp < 185 -> WidgetSizeFormat.COMPACT_PILL
                heightDp < 155 -> WidgetSizeFormat.SHORT_WIDE
                heightDp <= 235 -> WidgetSizeFormat.STANDARD_GLASS
                else -> WidgetSizeFormat.EXPANDED_DASHBOARD
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
                ?: List(112) { 0 }

            val rawHexes = prefs.getString(KEY_STOP_HEXES, null)
            val stopHexes = rawHexes
                ?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.takeIf { it.size >= 2 }
                ?: DEFAULT_MOMENTUM_STOPS_HEX

            val fallbackMomentumColor = interpolateSpectrumColor(momentumProgress, stopHexes)
            val systemPalette = resolveSystemGlassPalette(context, fallbackMomentumColor)

            val openGoalsPending = buildActivityPendingIntent(context, ACTION_OPEN_GOALS, 201)
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
                val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }.getOrNull()
                val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)?.takeIf { it > 0 } ?: 280
                val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 165)?.takeIf { it > 0 } ?: 165

                val widthDp = minW.coerceIn(110, 600)
                val heightDp = minH.coerceIn(56, 520)
                val format = classifyWidgetFormat(widthDp, heightDp)

                val bitmap = renderResponsiveGlassWidgetBitmap(
                    widthDp = widthDp,
                    heightDp = heightDp,
                    format = format,
                    palette = systemPalette,
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

                val views = RemoteViews(context.packageName, R.layout.widget_flow_glass)
                views.setImageViewBitmap(R.id.widget_canvas_image, bitmap)
                views.setOnClickPendingIntent(R.id.widget_btn_open_goals, openGoalsPending)
                views.setOnClickPendingIntent(R.id.widget_btn_quick_capture, quickCapturePending)
                views.setOnClickPendingIntent(R.id.widget_btn_complete_task, completeTaskPending)
                views.setOnClickPendingIntent(R.id.widget_btn_open_today, openTodayPending)

                // Hide the bottom task hit-target row when in COMPACT_PILL format so top targets get full height
                views.setViewVisibility(
                    R.id.widget_bottom_task_row,
                    if (format == WidgetSizeFormat.COMPACT_PILL) View.GONE else View.VISIBLE
                )

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

        internal fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
            val rRatio = ratio.coerceIn(0f, 1f)
            val inv = 1f - rRatio
            val a = (Color.alpha(c1) * inv + Color.alpha(c2) * rRatio).roundToInt().coerceIn(0, 255)
            val r = (Color.red(c1) * inv + Color.red(c2) * rRatio).roundToInt().coerceIn(0, 255)
            val g = (Color.green(c1) * inv + Color.green(c2) * rRatio).roundToInt().coerceIn(0, 255)
            val b = (Color.blue(c1) * inv + Color.blue(c2) * rRatio).roundToInt().coerceIn(0, 255)
            return Color.argb(a, r, g, b)
        }

        internal fun withAlpha(color: Int, alpha: Int): Int {
            return Color.argb(
                alpha.coerceIn(0, 255),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
        }

        private fun renderResponsiveGlassWidgetBitmap(
            widthDp: Int,
            heightDp: Int,
            format: WidgetSizeFormat,
            palette: SystemGlassPalette,
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
            // Render at 2.4x virtual density matching exact widget aspect ratio
            val s = 2.4f
            val width = (widthDp * s).roundToInt().coerceIn(260, 1440)
            val height = (heightDp * s).roundToInt().coerceIn(134, 1248)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val clampedProgress = momentumProgress.coerceIn(0f, 1f)
            val momentumColor = interpolateSpectrumColor(clampedProgress, stopHexes)
            // Blend system accent with momentum color so the widget always reflects the system color
            val primaryAccent = palette.systemAccent
            val luminousAccent = blendColors(primaryAccent, momentumColor, 0.30f)

            val inset = 4f * s
            val cardRect = RectF(inset, inset, width - inset, height - inset)
            val cornerRadius = min(26f * s, min(cardRect.width(), cardRect.height()) * 0.42f)

            // 1. Frosted Liquid-Glass Base tinted with the live System Color
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    palette.glassBgTop,
                    palette.glassBgBottom,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Top-left radial ambient bloom in System Accent
            val glowRadius = maxOf(width, height) * 0.72f
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width * 0.22f,
                    height * 0.18f,
                    glowRadius,
                    intArrayOf(
                        withAlpha(primaryAccent, if (palette.isDark) 96 else 68),
                        withAlpha(luminousAccent, if (palette.isDark) 34 else 22),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, glowPaint)

            // 3. Top specular glass reflection sheen (ColorOS 17 Living Flow signature)
            val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    cardRect.top,
                    0f,
                    cardRect.top + cardRect.height() * 0.45f,
                    palette.glassSheenTop,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, sheenPaint)

            // 4. Crisp Contour Glow Border infused with System Accent
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.25f * s
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(
                        withAlpha(Color.WHITE, if (palette.isDark) 145 else 210),
                        withAlpha(primaryAccent, 185),
                        withAlpha(primaryAccent, 75)
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

            // Dispatch to the size-specific layout renderer
            when (format) {
                WidgetSizeFormat.COMPACT_PILL -> renderCompactPillLayout(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    s = s,
                    palette = palette,
                    primaryAccent = primaryAccent,
                    clampedProgress = clampedProgress,
                    completedGoals = completedGoals,
                    totalGoals = totalGoals,
                    topTaskTitle = topTaskTitle,
                    stopHexes = stopHexes
                )

                WidgetSizeFormat.SHORT_WIDE -> renderShortWideLayout(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    s = s,
                    palette = palette,
                    primaryAccent = primaryAccent,
                    clampedProgress = clampedProgress,
                    completedToday = completedToday,
                    totalToday = totalToday,
                    completedGoals = completedGoals,
                    totalGoals = totalGoals,
                    streakDays = streakDays,
                    topTaskTitle = topTaskTitle,
                    stopHexes = stopHexes
                )

                WidgetSizeFormat.STANDARD_GLASS -> renderStandardGlassLayout(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    widthDp = widthDp,
                    s = s,
                    palette = palette,
                    primaryAccent = primaryAccent,
                    clampedProgress = clampedProgress,
                    completedToday = completedToday,
                    totalToday = totalToday,
                    completedGoals = completedGoals,
                    totalGoals = totalGoals,
                    streakDays = streakDays,
                    topTaskTitle = topTaskTitle,
                    recentHeatmapLevels = recentHeatmapLevels,
                    stopHexes = stopHexes
                )

                WidgetSizeFormat.EXPANDED_DASHBOARD -> renderExpandedDashboardLayout(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    widthDp = widthDp,
                    s = s,
                    palette = palette,
                    primaryAccent = primaryAccent,
                    clampedProgress = clampedProgress,
                    completedToday = completedToday,
                    totalToday = totalToday,
                    completedGoals = completedGoals,
                    totalGoals = totalGoals,
                    streakDays = streakDays,
                    topTaskTitle = topTaskTitle,
                    recentHeatmapLevels = recentHeatmapLevels,
                    stopHexes = stopHexes
                )
            }

            return bitmap
        }

        /**
         * FORMAT 1: COMPACT PILL (2x1 or narrow 2x2)
         * Single sleek horizontal liquid-glass layout with Percentage, Momentum Bar, and '+' button.
         */
        private fun renderCompactPillLayout(
            canvas: Canvas,
            width: Float,
            height: Float,
            s: Float,
            palette: SystemGlassPalette,
            primaryAccent: Int,
            clampedProgress: Float,
            completedGoals: Int,
            totalGoals: Int,
            topTaskTitle: String?,
            stopHexes: List<Long>
        ) {
            val padX = 16f * s
            val centerY = height * 0.5f

            // Right '+' Quick Capture Glass Button
            val btnSize = min(height - 20f * s, 38f * s).coerceAtLeast(26f * s)
            val quickBtnRect = RectF(
                width - padX - btnSize,
                centerY - btnSize * 0.5f,
                width - padX,
                centerY + btnSize * 0.5f
            )
            drawQuickAddButton(canvas, quickBtnRect, s, primaryAccent)

            // Left Percentage & FLOW label
            val pctText = "${(clampedProgress * 100f).roundToInt()}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 20f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryAccent
                textSize = 9.5f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
            }

            val topLabel = if (!topTaskTitle.isNullOrBlank() && width > 210f * s) {
                ellipsizeText(topTaskTitle, labelPaint, quickBtnRect.left - padX - 16f * s)
            } else {
                "FLOW • $completedGoals/$totalGoals GOALS"
            }

            canvas.drawText(topLabel, padX, centerY - 6f * s, labelPaint)
            canvas.drawText(pctText, padX, centerY + 14f * s, pctPaint)

            val pctWidth = pctPaint.measureText(pctText)
            val barLeft = padX + pctWidth + 10f * s
            val barRight = quickBtnRect.left - 12f * s

            if (barRight - barLeft >= 32f * s) {
                val barHeight = 8.5f * s
                val barTop = centerY + 4f * s
                drawMomentumSpectrumBar(
                    canvas = canvas,
                    barLeft = barLeft,
                    barTop = barTop,
                    barRight = barRight,
                    barBottom = barTop + barHeight,
                    s = s,
                    clampedProgress = clampedProgress,
                    primaryAccent = primaryAccent,
                    stopHexes = stopHexes,
                    palette = palette
                )
            }
        }

        /**
         * FORMAT 2: SHORT WIDE (3x1 / 4x1 or short 3x2)
         * Header + Full-width Momentum Bar + Bottom 1-Tap Task Strip (no heatmap crowding).
         */
        private fun renderShortWideLayout(
            canvas: Canvas,
            width: Float,
            height: Float,
            s: Float,
            palette: SystemGlassPalette,
            primaryAccent: Int,
            clampedProgress: Float,
            completedToday: Int,
            totalToday: Int,
            completedGoals: Int,
            totalGoals: Int,
            streakDays: Int,
            topTaskTitle: String?,
            stopHexes: List<Long>
        ) {
            val padX = 16f * s
            val topY = 14f * s

            // Top-right '+' button
            val btnSize = 32f * s
            val quickBtnRect = RectF(width - padX - btnSize, topY, width - padX, topY + btnSize)
            drawQuickAddButton(canvas, quickBtnRect, s, primaryAccent)

            // Header row: Percentage + summary
            val pctText = "${(clampedProgress * 100f).roundToInt()}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 19f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(pctText, padX, topY + 21f * s, pctPaint)

            val pctWidth = pctPaint.measureText(pctText)
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textSecondary
                textSize = 10.5f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaText = "FLOW • $completedGoals/$totalGoals goals • $completedToday/$totalToday today • 🔥${streakDays}d"
            val maxMetaW = (quickBtnRect.left - (padX + pctWidth + 10f * s) - 8f * s).coerceAtLeast(40f * s)
            canvas.drawText(
                ellipsizeText(metaText, metaPaint, maxMetaW),
                padX + pctWidth + 8f * s,
                topY + 19f * s,
                metaPaint
            )

            // Middle: Full-width Momentum Bar
            val barTop = topY + btnSize + 7f * s
            val barHeight = 9f * s
            drawMomentumSpectrumBar(
                canvas = canvas,
                barLeft = padX,
                barTop = barTop,
                barRight = width - padX,
                barBottom = barTop + barHeight,
                s = s,
                clampedProgress = clampedProgress,
                primaryAccent = primaryAccent,
                stopHexes = stopHexes,
                palette = palette
            )

            // Bottom: Task strip
            val stripHeight = min(34f * s, height - (barTop + barHeight + 16f * s)).coerceAtLeast(26f * s)
            val stripTop = height - 10f * s - stripHeight
            drawBottomTaskStrip(
                canvas = canvas,
                stripRect = RectF(padX - 2f * s, stripTop, width - padX + 2f * s, stripTop + stripHeight),
                s = s,
                palette = palette,
                primaryAccent = primaryAccent,
                topTaskTitle = topTaskTitle
            )
        }

        /**
         * FORMAT 3: STANDARD GLASS (3x2 / 4x2)
         * Header + '+' Quick Capture button, Momentum Bar (and auto-sized Heatmap when width allows),
         * and Bottom 1-Tap Task Completion Strip.
         */
        private fun renderStandardGlassLayout(
            canvas: Canvas,
            width: Float,
            height: Float,
            widthDp: Int,
            s: Float,
            palette: SystemGlassPalette,
            primaryAccent: Int,
            clampedProgress: Float,
            completedToday: Int,
            totalToday: Int,
            completedGoals: Int,
            totalGoals: Int,
            streakDays: Int,
            topTaskTitle: String?,
            recentHeatmapLevels: List<Int>,
            stopHexes: List<Long>
        ) {
            val padX = 18f * s
            val topY = 16f * s

            // Top-right '+' Quick Capture Glass Pill Button (no mic button!)
            val btnW = 38f * s
            val btnH = 34f * s
            val quickBtnRect = RectF(width - padX - btnW, topY, width - padX, topY + btnH)
            drawQuickAddButton(canvas, quickBtnRect, s, primaryAccent)

            // Header Title & Percentage
            val headerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryAccent
                textSize = 9.5f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.12f
            }
            canvas.drawText("FLOW GOAL BAR", padX, topY + 10f * s, headerLabelPaint)

            val pctText = "${(clampedProgress * 100f).roundToInt()}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 23f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(pctText, padX, topY + 34f * s, pctPaint)

            val pctWidth = pctPaint.measureText(pctText)
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textSecondary
                textSize = 11f * s
            }
            val subtitleText = "$completedGoals/$totalGoals goals • 🔥 ${streakDays}d streak"
            val maxSubW = (quickBtnRect.left - (padX + pctWidth + 10f * s) - 8f * s).coerceAtLeast(40f * s)
            canvas.drawText(
                ellipsizeText(subtitleText, subtitlePaint, maxSubW),
                padX + pctWidth + 8f * s,
                topY + 31f * s,
                subtitlePaint
            )

            // Bottom Task Strip dimensions reserved first so middle zone never collides
            val stripHeight = 38f * s
            val stripTop = height - 13f * s - stripHeight
            val stripRect = RectF(padX - 4f * s, stripTop, width - padX + 4f * s, stripTop + stripHeight)

            // Middle Zone: Show side-by-side Momentum Bar + Mini Heatmap if widthDp >= 255
            val showHeatmap = widthDp >= 255
            val middleTop = topY + 46f * s
            val middleBottom = stripTop - 10f * s
            val middleCenterY = (middleTop + middleBottom) * 0.5f

            if (showHeatmap) {
                val cols = ((widthDp - 165) / 10).coerceIn(7, 14)
                val rows = 7
                val availH = (middleBottom - middleTop).coerceAtLeast(36f * s)
                val cellGap = 2.0f * s
                val cellSize = ((availH - (rows - 1) * cellGap) / rows).coerceIn(4.5f * s, 7.2f * s)
                val gridWidth = cols * cellSize + (cols - 1) * cellGap
                val gridHeight = rows * cellSize + (rows - 1) * cellGap
                val gridLeft = width - padX - gridWidth
                val gridTop = middleCenterY - gridHeight * 0.5f

                drawHeatmapGrid(
                    canvas = canvas,
                    gridLeft = gridLeft,
                    gridTop = gridTop,
                    cols = cols,
                    rows = rows,
                    cellSize = cellSize,
                    cellGap = cellGap,
                    s = s,
                    palette = palette,
                    primaryAccent = primaryAccent,
                    recentHeatmapLevels = recentHeatmapLevels,
                    stopHexes = stopHexes
                )

                val barRight = (gridLeft - 14f * s).coerceAtLeast(padX + 60f * s)
                val barHeight = 11.5f * s
                val barTop = middleCenterY - barHeight - 2f * s
                drawMomentumSpectrumBar(
                    canvas = canvas,
                    barLeft = padX,
                    barTop = barTop,
                    barRight = barRight,
                    barBottom = barTop + barHeight,
                    s = s,
                    clampedProgress = clampedProgress,
                    primaryAccent = primaryAccent,
                    stopHexes = stopHexes,
                    palette = palette
                )

                val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textTertiary
                    textSize = 9.5f * s
                }
                val todaySummary = if (totalToday > 0) {
                    "Today: $completedToday/$totalToday tasks done"
                } else {
                    "System Glass • Tap to view goals"
                }
                canvas.drawText(
                    ellipsizeText(todaySummary, metaPaint, barRight - padX),
                    padX,
                    barTop + barHeight + 14f * s,
                    metaPaint
                )
            } else {
                // Full-width Momentum Bar for narrower 3x2 size
                val barHeight = 12f * s
                val barTop = middleCenterY - barHeight - 2f * s
                drawMomentumSpectrumBar(
                    canvas = canvas,
                    barLeft = padX,
                    barTop = barTop,
                    barRight = width - padX,
                    barBottom = barTop + barHeight,
                    s = s,
                    clampedProgress = clampedProgress,
                    primaryAccent = primaryAccent,
                    stopHexes = stopHexes,
                    palette = palette
                )

                val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textTertiary
                    textSize = 10f * s
                }
                val todaySummary = "Today: $completedToday/$totalToday tasks • $completedGoals/$totalGoals goals"
                canvas.drawText(
                    ellipsizeText(todaySummary, metaPaint, width - padX * 2f),
                    padX,
                    barTop + barHeight + 14f * s,
                    metaPaint
                )
            }

            // Bottom Task Strip
            drawBottomTaskStrip(
                canvas = canvas,
                stripRect = stripRect,
                s = s,
                palette = palette,
                primaryAccent = primaryAccent,
                topTaskTitle = topTaskTitle
            )
        }

        /**
         * FORMAT 4: EXPANDED DASHBOARD (4x3 / 4x4 tall widget)
         * Full-width Momentum Bar + Dedicated Contribution Heatmap Section + Interactive Task Card.
         */
        private fun renderExpandedDashboardLayout(
            canvas: Canvas,
            width: Float,
            height: Float,
            widthDp: Int,
            s: Float,
            palette: SystemGlassPalette,
            primaryAccent: Int,
            clampedProgress: Float,
            completedToday: Int,
            totalToday: Int,
            completedGoals: Int,
            totalGoals: Int,
            streakDays: Int,
            topTaskTitle: String?,
            recentHeatmapLevels: List<Int>,
            stopHexes: List<Long>
        ) {
            val padX = 20f * s
            val topY = 18f * s

            // 1. Top-right '+' Quick Capture Button
            val btnW = 42f * s
            val btnH = 38f * s
            val quickBtnRect = RectF(width - padX - btnW, topY, width - padX, topY + btnH)
            drawQuickAddButton(canvas, quickBtnRect, s, primaryAccent)

            // 2. Expressive Header
            val headerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryAccent
                textSize = 10f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.14f
            }
            canvas.drawText("LIVING FLOW DASHBOARD", padX, topY + 11f * s, headerLabelPaint)

            val pctText = "${(clampedProgress * 100f).roundToInt()}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 27f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(pctText, padX, topY + 38f * s, pctPaint)

            val pctWidth = pctPaint.measureText(pctText)
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textSecondary
                textSize = 11.5f * s
            }
            val subtitleText = "$completedGoals/$totalGoals goals • $completedToday/$totalToday tasks • 🔥 ${streakDays}d"
            val maxSubW = (quickBtnRect.left - (padX + pctWidth + 10f * s) - 8f * s).coerceAtLeast(40f * s)
            canvas.drawText(
                ellipsizeText(subtitleText, subtitlePaint, maxSubW),
                padX + pctWidth + 10f * s,
                topY + 34f * s,
                subtitlePaint
            )

            // 3. Full-width Luminous Momentum Bar
            val barTop = topY + 50f * s
            val barHeight = 13f * s
            drawMomentumSpectrumBar(
                canvas = canvas,
                barLeft = padX,
                barTop = barTop,
                barRight = width - padX,
                barBottom = barTop + barHeight,
                s = s,
                clampedProgress = clampedProgress,
                primaryAccent = primaryAccent,
                stopHexes = stopHexes,
                palette = palette
            )

            // 4. Bottom Task Strip reserved
            val stripHeight = 44f * s
            val stripTop = height - 16f * s - stripHeight
            val stripRect = RectF(padX - 4f * s, stripTop, width - padX + 4f * s, stripTop + stripHeight)

            // 5. Middle Frosted Glass Contribution Heatmap Card
            val heatmapCardTop = barTop + barHeight + 14f * s
            val heatmapCardBottom = stripTop - 12f * s
            if (heatmapCardBottom - heatmapCardTop >= 54f * s) {
                val heatmapCardRect = RectF(padX - 4f * s, heatmapCardTop, width - padX + 4f * s, heatmapCardBottom)
                val subCardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.surfacePillFill
                }
                val subCardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f * s
                    color = palette.surfacePillStroke
                }
                canvas.drawRoundRect(heatmapCardRect, 14f * s, 14f * s, subCardFill)
                canvas.drawRoundRect(heatmapCardRect, 14f * s, 14f * s, subCardStroke)

                val cardHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textSecondary
                    textSize = 9.5f * s
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    letterSpacing = 0.08f
                }
                canvas.drawText(
                    "CONTRIBUTION HEATMAP • LAST WEEKS",
                    heatmapCardRect.left + 12f * s,
                    heatmapCardRect.top + 15f * s,
                    cardHeaderPaint
                )

                val gridAreaTop = heatmapCardRect.top + 22f * s
                val gridAreaBottom = heatmapCardRect.bottom - 8f * s
                val gridAreaWidth = heatmapCardRect.width() - 24f * s
                val gridAreaHeight = (gridAreaBottom - gridAreaTop).coerceAtLeast(28f * s)

                val rows = 7
                val cellGap = 2.2f * s
                val cellSize = ((gridAreaHeight - (rows - 1) * cellGap) / rows).coerceIn(4.5f * s, 9.5f * s)
                val cols = ((gridAreaWidth + cellGap) / (cellSize + cellGap)).toInt().coerceIn(8, 20)
                val totalGridW = cols * cellSize + (cols - 1) * cellGap
                val totalGridH = rows * cellSize + (rows - 1) * cellGap
                val gridLeft = heatmapCardRect.centerX() - totalGridW * 0.5f
                val gridTop = gridAreaTop + (gridAreaHeight - totalGridH) * 0.5f

                drawHeatmapGrid(
                    canvas = canvas,
                    gridLeft = gridLeft,
                    gridTop = gridTop,
                    cols = cols,
                    rows = rows,
                    cellSize = cellSize,
                    cellGap = cellGap,
                    s = s,
                    palette = palette,
                    primaryAccent = primaryAccent,
                    recentHeatmapLevels = recentHeatmapLevels,
                    stopHexes = stopHexes
                )
            }

            // 6. Bottom Interactive Task Strip
            drawBottomTaskStrip(
                canvas = canvas,
                stripRect = stripRect,
                s = s,
                palette = palette,
                primaryAccent = primaryAccent,
                topTaskTitle = topTaskTitle
            )
        }

        private fun drawQuickAddButton(
            canvas: Canvas,
            rect: RectF,
            s: Float,
            primaryAccent: Int
        ) {
            val radius = min(rect.width(), rect.height()) * 0.5f
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(primaryAccent, 78)
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.15f * s
                color = withAlpha(primaryAccent, 215)
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)

            val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f * s
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("+", rect.centerX(), rect.centerY() + 6.2f * s, plusPaint)
        }

        private fun drawMomentumSpectrumBar(
            canvas: Canvas,
            barLeft: Float,
            barTop: Float,
            barRight: Float,
            barBottom: Float,
            s: Float,
            clampedProgress: Float,
            primaryAccent: Int,
            stopHexes: List<Long>,
            palette: SystemGlassPalette
        ) {
            val barRadius = (barBottom - barTop) * 0.5f
            val trackRect = RectF(barLeft, barTop, barRight, barBottom)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.surfacePillFill
            }
            val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.8f * s
                color = palette.surfacePillStroke
            }
            canvas.drawRoundRect(trackRect, barRadius, barRadius, trackPaint)
            canvas.drawRoundRect(trackRect, barRadius, barRadius, trackBorderPaint)

            val fillFraction = clampedProgress.coerceAtLeast(0.05f)
            val fillRight = barLeft + (barRight - barLeft) * fillFraction
            val fillRect = RectF(barLeft, barTop, fillRight, barBottom)

            // Blend the user's spectrum stops with the system accent so it harmonizes with System UI
            val spectrumColors = (if (stopHexes.size >= 2) stopHexes else DEFAULT_MOMENTUM_STOPS_HEX)
                .map { blendColors(it.toInt(), primaryAccent, 0.25f) }
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

            // Glowing Comet Tip
            val cometX = fillRight.coerceIn(barLeft + barRadius, barRight - barRadius)
            val cometY = (barTop + barBottom) * 0.5f
            val haloRadius = (barBottom - barTop) * 1.15f
            val cometHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cometX,
                    cometY,
                    haloRadius,
                    intArrayOf(
                        withAlpha(Color.WHITE, 220),
                        withAlpha(primaryAccent, 145),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cometX, cometY, haloRadius, cometHaloPaint)

            val cometCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            canvas.drawCircle(cometX, cometY, (barBottom - barTop) * 0.30f, cometCorePaint)
        }

        private fun drawHeatmapGrid(
            canvas: Canvas,
            gridLeft: Float,
            gridTop: Float,
            cols: Int,
            rows: Int,
            cellSize: Float,
            cellGap: Float,
            s: Float,
            palette: SystemGlassPalette,
            primaryAccent: Int,
            recentHeatmapLevels: List<Int>,
            stopHexes: List<Long>
        ) {
            val totalCells = cols * rows
            val paddedLevels = if (recentHeatmapLevels.size >= totalCells) {
                recentHeatmapLevels.takeLast(totalCells)
            } else {
                List(totalCells - recentHeatmapLevels.size) { 0 } + recentHeatmapLevels
            }

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val stopList = if (stopHexes.size >= 5) stopHexes else DEFAULT_MOMENTUM_STOPS_HEX
            val corner = (cellSize * 0.28f).coerceAtLeast(1.5f * s)

            for (col in 0 until cols) {
                for (row in 0 until rows) {
                    val index = col * rows + row
                    val level = paddedLevels.getOrElse(index) { 0 }.coerceIn(0, 4)
                    cellPaint.color = when (level) {
                        0 -> if (palette.isDark) Color.parseColor("#22FFFFFF") else Color.parseColor("#1F000000")
                        1 -> withAlpha(blendColors(stopList[1].toInt(), primaryAccent, 0.35f), 150)
                        2 -> withAlpha(blendColors(stopList[2].toInt(), primaryAccent, 0.35f), 185)
                        3 -> withAlpha(blendColors(stopList[3].toInt(), primaryAccent, 0.30f), 220)
                        else -> blendColors(stopList[4].toInt(), primaryAccent, 0.25f)
                    }
                    val cx = gridLeft + col * (cellSize + cellGap)
                    val cy = gridTop + row * (cellSize + cellGap)
                    canvas.drawRoundRect(
                        RectF(cx, cy, cx + cellSize, cy + cellSize),
                        corner,
                        corner,
                        cellPaint
                    )
                }
            }
        }

        private fun drawBottomTaskStrip(
            canvas: Canvas,
            stripRect: RectF,
            s: Float,
            palette: SystemGlassPalette,
            primaryAccent: Int,
            topTaskTitle: String?
        ) {
            val radius = stripRect.height() * 0.46f
            val stripFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.surfacePillFill
            }
            val stripBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.9f * s
                color = palette.surfacePillStroke
            }
            canvas.drawRoundRect(stripRect, radius, radius, stripFillPaint)
            canvas.drawRoundRect(stripRect, radius, radius, stripBorderPaint)

            val checkCx = stripRect.left + 20f * s
            val checkCy = stripRect.centerY()
            val circleRadius = min(9.5f * s, stripRect.height() * 0.30f)
            val hasTask = !topTaskTitle.isNullOrBlank()

            if (hasTask) {
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.6f * s
                    color = primaryAccent
                }
                val ringFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(primaryAccent, 52)
                }
                canvas.drawCircle(checkCx, checkCy, circleRadius, ringFillPaint)
                canvas.drawCircle(checkCx, checkCy, circleRadius, ringPaint)
            } else {
                val doneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(primaryAccent, 200)
                }
                canvas.drawCircle(checkCx, checkCy, circleRadius, doneFillPaint)
                val checkMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 11f * s
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("✓", checkCx, checkCy + 3.8f * s, checkMarkPaint)
            }

            val rawTaskLine = if (hasTask) {
                topTaskTitle!!.trim()
            } else {
                "All tasks caught up today ✨ Tap + to capture"
            }

            val taskTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 11.5f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textStartX = checkCx + circleRadius + 10f * s
            val maxTextWidth = (stripRect.right - textStartX - 12f * s).coerceAtLeast(40f * s)
            val displayTaskLine = ellipsizeText(rawTaskLine, taskTextPaint, maxTextWidth)

            canvas.drawText(displayTaskLine, textStartX, checkCy + 4f * s, taskTextPaint)
        }

        internal fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val ellipsis = "…"
            var end = text.length
            while (end > 1 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) {
                end--
            }
            return text.substring(0, end).trimEnd() + ellipsis
        }
    }
}

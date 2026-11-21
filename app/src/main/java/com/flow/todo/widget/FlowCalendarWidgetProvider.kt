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
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flow.todo.MainActivity
import com.flow.todo.R
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.data.local.toDomain
import com.flow.todo.domain.nlp.NaturalLanguageTaskParser
import com.flow.todo.domain.timeline.DayTimeline
import com.flow.todo.domain.timeline.DayTimelineModel
import com.flow.todo.domain.timeline.TimelineKind
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.ellipsizeText
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.resolveSystemGlassPalette
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.withAlpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mini day timeline widget: today's events and time-blocked tasks around "now", free gaps, a live
 * now line, all-day chips and the count of to-dos still to schedule. Tap opens the Day screen;
 * "+" opens Quick Capture. A 15-minute background refresh keeps the now line moving.
 */
class FlowCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { renderAll(context, appWidgetManager, appWidgetIds) } finally { pending.finish() }
        }
        scheduleRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        scope.launch {
            try { renderAll(context, appWidgetManager, intArrayOf(appWidgetId)) } finally { pending.finish() }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(REFRESH_WORK)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED -> requestUpdate(context)
        }
    }

    /** Re-renders the widget so the now line keeps up with the clock. */
    class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val ids = widgetIds(applicationContext)
            if (ids.isNotEmpty()) renderAll(applicationContext, AppWidgetManager.getInstance(applicationContext), ids)
            return Result.success()
        }
    }

    companion object {
        const val ACTION_OPEN_CALENDAR = "com.flow.todo.ACTION_OPEN_CALENDAR"
        private const val REFRESH_WORK = "flow_calendar_widget_refresh"
        private const val HEADER_DP = 56f
        private const val HOUR_DP = 34f
        private const val LABEL_DP = 34f

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            scope.launch {
                runCatching {
                    val ids = widgetIds(app)
                    if (ids.isNotEmpty()) renderAll(app, AppWidgetManager.getInstance(app), ids)
                }
            }
        }

        private fun scheduleRefresh(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    REFRESH_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
                )
            }
        }

        private fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, FlowCalendarWidgetProvider::class.java))

        private suspend fun loadModel(context: Context): DayTimelineModel {
            val dao = FlowDatabase.getInstance(context).flowDao()
            return DayTimeline.build(
                epochDay = LocalDate.now().toEpochDay(),
                events = dao.getAllCalendarEvents().map { it.toDomain() },
                tasks = dao.getAllTasks().map { it.toDomain() },
                nowMillis = System.currentTimeMillis(),
                zone = ZoneId.systemDefault()
            )
        }

        private suspend fun renderAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val palette = resolveSystemGlassPalette(context, Color.parseColor("#FF2F6BFF"))
            val model = loadModel(context)
            val openDay = activityIntent(context, ACTION_OPEN_CALENDAR, 401)
            val quickCapture = activityIntent(context, FlowGlassWidgetProvider.ACTION_QUICK_CAPTURE, 402)

            for (id in ids) {
                val opts = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
                // In portrait the launcher gives width = MIN_WIDTH and height = MAX_HEIGHT.
                val widthDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: 250).coerceIn(150, 640)
                val heightDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: 180).coerceIn(90, 560)

                val views = RemoteViews(context.packageName, R.layout.widget_calendar)
                views.setImageViewBitmap(R.id.cal_canvas_image, draw(widthDp, heightDp, palette, model))
                views.setOnClickPendingIntent(R.id.cal_btn_open_header, openDay)
                views.setOnClickPendingIntent(R.id.cal_btn_open_footer, openDay)
                views.setOnClickPendingIntent(R.id.cal_btn_quick_capture, quickCapture)
                mgr.updateAppWidget(id, views)
            }
        }

        private fun activityIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                Intent(context, MainActivity::class.java).apply {
                    this.action = action
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        // ------------------------------------------------------------------ drawing

        private fun draw(
            widthDp: Int,
            heightDp: Int,
            palette: FlowGlassWidgetProvider.SystemGlassPalette,
            model: DayTimelineModel
        ): Bitmap {
            val s = 2.4f
            val width = (widthDp * s).roundToInt()
            val height = (heightDp * s).roundToInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val accent = palette.systemAccent
            val danger = Color.parseColor("#FFFF3B5C")

            // Glass card
            val inset = 4f * s
            val card = RectF(inset, inset, width - inset, height - inset)
            val radius = min(26f * s, min(card.width(), card.height()) * 0.42f)
            canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), palette.glassBgTop, palette.glassBgBottom, Shader.TileMode.CLAMP)
            })
            canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width * 0.85f, height * 0.1f, maxOf(width, height) * 0.7f,
                    intArrayOf(withAlpha(accent, if (palette.isDark) 80 else 56), withAlpha(accent, 16), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            })
            canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.25f * s
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(withAlpha(Color.WHITE, if (palette.isDark) 145 else 210), withAlpha(accent, 185), withAlpha(accent, 75)),
                    floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
                )
            })

            val padX = 14f * s

            // ---- Header: date + summary + "+" ----
            val plusSize = 30f * s
            val plusRect = RectF(width - padX - plusSize, 13f * s, width - padX, 13f * s + plusSize)
            canvas.drawCircle(plusRect.centerX(), plusRect.centerY(), plusSize / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 78) })
            val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textPrimary; strokeWidth = 2f * s; strokeCap = Paint.Cap.ROUND }
            val arm = plusSize * 0.22f
            canvas.drawLine(plusRect.centerX() - arm, plusRect.centerY(), plusRect.centerX() + arm, plusRect.centerY(), plusPaint)
            canvas.drawLine(plusRect.centerX(), plusRect.centerY() - arm, plusRect.centerX(), plusRect.centerY() + arm, plusPaint)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; textSize = 9.5f * s; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
            }
            canvas.drawText("TODAY", padX, 22f * s, labelPaint)
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary; textSize = 16f * s; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dateLabel = LocalDate.ofEpochDay(model.epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            canvas.drawText(dateLabel, padX, 41f * s, datePaint)
            val free = model.gaps.sumOf { it.durationMinutes }
            val meta = buildList {
                if (model.unscheduledTasks.isNotEmpty()) add("${model.unscheduledTasks.size} to schedule")
                if (free > 0) add(DayTimeline.formatGap(free))
            }.joinToString(" · ")
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textSecondary; textSize = 10f * s }
            val metaX = padX + datePaint.measureText(dateLabel) + 8f * s
            if (meta.isNotEmpty() && plusRect.left - metaX > 30f * s) {
                canvas.drawText(ellipsizeText(meta, metaPaint, plusRect.left - metaX - 6f * s), metaX, 40f * s, metaPaint)
            }

            var top = HEADER_DP * s

            // ---- All-day chips ----
            if (model.allDayEvents.isNotEmpty()) {
                var x = padX
                val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * s; color = palette.textPrimary }
                for (e in model.allDayEvents) {
                    val text = ellipsizeText(e.title, chipPaint, 110f * s)
                    val w = chipPaint.measureText(text) + 16f * s
                    if (x + w > width - padX) break
                    val c = (e.calendarColorHex and 0xFFFFFFFFL).toInt()
                    canvas.drawRoundRect(RectF(x, top, x + w, top + 18f * s), 9f * s, 9f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(c, 60) })
                    canvas.drawText(text, x + 8f * s, top + 12.5f * s, chipPaint)
                    x += w + 4f * s
                }
                top += 22f * s
            }

            // ---- Timeline window around now ----
            val bottom = height - inset - 8f * s
            val hourPx = HOUR_DP * s
            val hours = ((bottom - top) / hourPx).toInt().coerceIn(1, 14)
            val now = model.nowMinute ?: (9 * 60)
            var startHour = max(0, now / 60 - 1)
            if (startHour + hours > 24) startHour = max(0, 24 - hours)
            val windowStart = startHour * 60
            val windowEnd = min(24 * 60, windowStart + hours * 60)
            val pxPerMin = hourPx / 60f
            val trackLeft = padX + LABEL_DP * s
            val trackRight = width - padX
            fun yOf(minute: Int) = top + (minute - windowStart) * pxPerMin

            val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textTertiary; textSize = 9f * s }
            val gridPaint = Paint().apply { color = withAlpha(palette.textTertiary, 45); strokeWidth = 1f * s }
            for (h in startHour..(windowEnd / 60)) {
                val y = yOf(h * 60)
                if (y > bottom) break
                canvas.drawText(NaturalLanguageTaskParser.formatClock(h * 60).replace(":00", ""), padX, y + 3.5f * s, hourPaint)
                canvas.drawLine(trackLeft, y, trackRight, y, gridPaint)
            }

            canvas.save()
            canvas.clipRect(trackLeft, top, trackRight, bottom)

            // Free gaps
            val gapStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f * s; color = withAlpha(accent, 90)
                pathEffect = DashPathEffect(floatArrayOf(5f * s, 4f * s), 0f)
            }
            val gapText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accent, 200); textSize = 9.5f * s; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            for (gap in model.gaps) {
                if (gap.endMinute <= windowStart || gap.startMinute >= windowEnd) continue
                val r = RectF(trackLeft + 2f * s, yOf(gap.startMinute) + 2f * s, trackRight - 2f * s, yOf(gap.endMinute) - 2f * s)
                canvas.drawRoundRect(r, 8f * s, 8f * s, gapStroke)
                if (r.height() > 14f * s) canvas.drawText(DayTimeline.formatGap(gap.durationMinutes), r.centerX(), r.centerY() + 3.5f * s, gapText)
            }

            // Blocks
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10.5f * s; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            for (p in model.timed) {
                val item = p.item
                if (item.endMinute <= windowStart || item.startMinute >= windowEnd) continue
                val laneW = (trackRight - trackLeft) / p.laneCount
                val left = trackLeft + laneW * p.lane + 2f * s
                val rect = RectF(left, yOf(item.startMinute) + 1f * s, left + laneW - 4f * s, max(yOf(item.endMinute) - 1f * s, yOf(item.startMinute) + 14f * s))
                val c = if (item.kind == TimelineKind.TASK) accent else (item.colorArgb and 0xFFFFFFFFL).toInt()
                canvas.drawRoundRect(rect, 7f * s, 7f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(c, if (item.isDone) 30 else 70) })
                canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + 3f * s, rect.bottom), 2f * s, 2f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c })
                titlePaint.color = if (item.isDone) palette.textTertiary else palette.textPrimary
                titlePaint.isStrikeThruText = item.isDone
                val prefix = if (item.kind == TimelineKind.TASK) (if (item.isDone) "✓ " else "○ ") else ""
                canvas.drawText(
                    ellipsizeText(prefix + item.title, titlePaint, rect.width() - 10f * s),
                    rect.left + 7f * s, min(rect.top + 12f * s, rect.bottom - 3f * s), titlePaint
                )
            }

            // Now line
            model.nowMinute?.let { n ->
                if (n in windowStart..windowEnd) {
                    val y = yOf(n)
                    canvas.drawLine(trackLeft, y, trackRight, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = danger; strokeWidth = 2f * s })
                }
            }
            canvas.restore()
            model.nowMinute?.let { n ->
                if (n in windowStart..windowEnd) canvas.drawCircle(trackLeft, yOf(n), 4f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = danger })
            }

            if (model.timed.isEmpty() && model.gaps.isEmpty()) {
                canvas.drawText(
                    "Nothing planned", (trackLeft + trackRight) / 2, top + (bottom - top) / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textSecondary; textSize = 12f * s; textAlign = Paint.Align.CENTER }
                )
            }
            return bitmap
        }
    }
}

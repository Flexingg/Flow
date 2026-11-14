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
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.flow.todo.MainActivity
import com.flow.todo.R
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.ellipsizeText
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.resolveSystemGlassPalette
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.withAlpha
import com.flow.todo.widget.agenda.Agenda
import com.flow.todo.widget.agenda.AgendaEventInput
import com.flow.todo.widget.agenda.AgendaKind
import com.flow.todo.widget.agenda.AgendaRow
import com.flow.todo.widget.agenda.AgendaTaskInput
import com.flow.todo.widget.agenda.CalendarAgenda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Living Flow Calendar agenda widget: today's calendar events + due/overdue tasks on a
 * system-color glass card. Each row's check circle checks the item off right from the home
 * screen; anywhere else opens the Calendar tab. "+" opens Quick Capture.
 *
 * The bitmap is drawn on the same dp grid as the tap targets in widget_calendar.xml:
 * 56dp header, then 36dp rows.
 */
class FlowCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { renderAll(context, appWidgetManager, appWidgetIds) } finally { pending.finish() }
        }
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val kind = intent.getStringExtra(EXTRA_KIND) ?: return
                val pending = goAsync()
                scope.launch {
                    try {
                        toggle(context, kind, id)
                        renderAll(context, AppWidgetManager.getInstance(context), widgetIds(context))
                        FlowGlassWidgetProvider.requestWidgetUpdate(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED -> requestUpdate(context)
        }
    }

    private suspend fun toggle(context: Context, kind: String, id: String) {
        val dao = FlowDatabase.getInstance(context).flowDao()
        val now = System.currentTimeMillis()
        if (kind == AgendaKind.EVENT.name) {
            val event = dao.getCalendarEventById(id) ?: return
            val done = !event.isCompleted
            dao.updateCalendarEventCompletion(id, done, if (done) now else null)
        } else {
            val task = dao.getAllTasks().firstOrNull { it.id == id } ?: return
            if (!task.recurrenceRuleRaw.isNullOrBlank()) return // recurring: handled in-app (spawns next occurrence)
            val done = !task.isCompleted
            dao.upsertTask(task.copy(isCompleted = done, completedAt = if (done) now else null))
        }
    }

    companion object {
        const val ACTION_OPEN_CALENDAR = "com.flow.todo.ACTION_OPEN_CALENDAR"
        private const val ACTION_TOGGLE = "com.flow.todo.ACTION_CALENDAR_WIDGET_TOGGLE"
        private const val EXTRA_ID = "extra_item_id"
        private const val EXTRA_KIND = "extra_item_kind"

        private const val HEADER_DP = 56f
        private const val ROW_DP = 36f
        private const val MAX_ROWS = 6

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val ROW_IDS = intArrayOf(R.id.cal_row0, R.id.cal_row1, R.id.cal_row2, R.id.cal_row3, R.id.cal_row4, R.id.cal_row5)
        private val ROW_OPEN_IDS = intArrayOf(
            R.id.cal_row0_open, R.id.cal_row1_open, R.id.cal_row2_open,
            R.id.cal_row3_open, R.id.cal_row4_open, R.id.cal_row5_open
        )
        private val ROW_CHECK_IDS = intArrayOf(
            R.id.cal_row0_check, R.id.cal_row1_check, R.id.cal_row2_check,
            R.id.cal_row3_check, R.id.cal_row4_check, R.id.cal_row5_check
        )

        /** Re-renders all calendar widgets off the main thread. Safe to call often. */
        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            scope.launch {
                runCatching {
                    val ids = widgetIds(app)
                    if (ids.isNotEmpty()) renderAll(app, AppWidgetManager.getInstance(app), ids)
                }
            }
        }

        private fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, FlowCalendarWidgetProvider::class.java))

        private suspend fun loadAgenda(context: Context, maxRows: Int): Agenda {
            val dao = FlowDatabase.getInstance(context).flowDao()
            val events = dao.getAllCalendarEvents().map {
                AgendaEventInput(it.id, it.title, it.startEpochMillis, it.endEpochMillis, it.isAllDay, it.isCompleted, it.calendarColorHex)
            }
            val tasks = dao.getAllTasks().map {
                AgendaTaskInput(
                    id = it.id, title = it.title, dueEpochDay = it.dueDateEpochDay, isCompleted = it.isCompleted,
                    completedAtMillis = it.completedAt, isFlagged = it.isFlagged,
                    isRecurring = !it.recurrenceRuleRaw.isNullOrBlank()
                )
            }
            return CalendarAgenda.build(
                events, tasks, System.currentTimeMillis(), ZoneId.systemDefault(), maxRows,
                use24h = DateFormat.is24HourFormat(context)
            )
        }

        private suspend fun renderAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val palette = resolveSystemGlassPalette(context, Color.parseColor("#FF2F6BFF"))
            val openCalendar = activityIntent(context, ACTION_OPEN_CALENDAR, 401)
            val quickCapture = activityIntent(context, FlowGlassWidgetProvider.ACTION_QUICK_CAPTURE, 402)

            for (id in ids) {
                val opts = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
                // In portrait the launcher gives width = MIN_WIDTH and height = MAX_HEIGHT.
                val widthDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: 250).coerceIn(150, 640)
                val heightDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: 180).coerceIn(90, 560)
                val visibleRows = ((heightDp - 4 - HEADER_DP - 6) / ROW_DP).toInt().coerceIn(0, MAX_ROWS)

                val agenda = loadAgenda(context, visibleRows)
                val bitmap = draw(widthDp, heightDp, visibleRows, palette, agenda)

                val views = RemoteViews(context.packageName, R.layout.widget_calendar)
                views.setImageViewBitmap(R.id.cal_canvas_image, bitmap)
                views.setOnClickPendingIntent(R.id.cal_btn_open_header, openCalendar)
                views.setOnClickPendingIntent(R.id.cal_btn_open_footer, openCalendar)
                views.setOnClickPendingIntent(R.id.cal_btn_quick_capture, quickCapture)

                for (i in 0 until MAX_ROWS) {
                    if (i >= visibleRows) {
                        views.setViewVisibility(ROW_IDS[i], View.GONE)
                        continue
                    }
                    views.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                    views.setOnClickPendingIntent(ROW_OPEN_IDS[i], openCalendar)
                    val row = agenda.rows.getOrNull(i)
                    val checkIntent = if (row != null && row.canToggle) {
                        PendingIntent.getBroadcast(
                            context, 410 + i,
                            Intent(context, FlowCalendarWidgetProvider::class.java)
                                .setAction(ACTION_TOGGLE)
                                .putExtra(EXTRA_ID, row.id)
                                .putExtra(EXTRA_KIND, row.kind.name),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    } else {
                        openCalendar
                    }
                    views.setOnClickPendingIntent(ROW_CHECK_IDS[i], checkIntent)
                }
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
            visibleRows: Int,
            palette: FlowGlassWidgetProvider.SystemGlassPalette,
            agenda: Agenda
        ): Bitmap {
            val s = 2.4f
            val width = (widthDp * s).roundToInt()
            val height = (heightDp * s).roundToInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val accent = palette.systemAccent

            // Glass card (same recipe as the other Flow widgets)
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
                shader = LinearGradient(0f, card.top, 0f, card.top + card.height() * 0.4f, palette.glassSheenTop, Color.TRANSPARENT, Shader.TileMode.CLAMP)
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

            val padX = 16f * s

            // ---- Header ----
            val plusSize = 30f * s
            val plusRect = RectF(width - padX - plusSize, 13f * s, width - padX, 13f * s + plusSize)
            canvas.drawCircle(plusRect.centerX(), plusRect.centerY(), plusSize / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 78) })
            canvas.drawCircle(plusRect.centerX(), plusRect.centerY(), plusSize / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.1f * s; color = withAlpha(accent, 215)
            })
            val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textPrimary; strokeWidth = 2f * s; strokeCap = Paint.Cap.ROUND }
            val arm = plusSize * 0.22f
            canvas.drawLine(plusRect.centerX() - arm, plusRect.centerY(), plusRect.centerX() + arm, plusRect.centerY(), plusPaint)
            canvas.drawLine(plusRect.centerX(), plusRect.centerY() - arm, plusRect.centerX(), plusRect.centerY() + arm, plusPaint)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; textSize = 9.5f * s; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
            }
            canvas.drawText("TODAY", padX, 22f * s, labelPaint)
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary; textSize = 17f * s; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(agenda.dateLabel, padX, 42f * s, datePaint)
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textSecondary; textSize = 10.5f * s }
            val meta = buildString {
                append("${agenda.eventCount} event${if (agenda.eventCount == 1) "" else "s"}")
                append(" · ${agenda.openTaskCount} task${if (agenda.openTaskCount == 1) "" else "s"}")
            }
            val metaX = padX + datePaint.measureText(agenda.dateLabel) + 8f * s
            val metaMax = plusRect.left - metaX - 6f * s
            if (metaMax > 30f * s) canvas.drawText(ellipsizeText(meta, metaPaint, metaMax), metaX, 41f * s, metaPaint)

            // ---- Rows ----
            val rowsTop = HEADER_DP * s
            if (agenda.rows.isEmpty() && visibleRows > 0) {
                val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textSecondary; textSize = 12.5f * s; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val cy = rowsTop + min(visibleRows, 2) * ROW_DP * s / 2f
                canvas.drawText("Nothing scheduled. Enjoy the open space ✨", width / 2f, cy, emptyPaint)
            }

            agenda.rows.take(visibleRows).forEachIndexed { i, row ->
                drawRow(canvas, row, rowsTop + i * ROW_DP * s, width.toFloat(), padX, s, palette)
            }

            val footerY = rowsTop + visibleRows * ROW_DP * s + 12f * s
            if (agenda.hiddenCount > 0 && footerY < height - inset - 2f * s) {
                val morePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textTertiary; textSize = 10f * s }
                canvas.drawText("+${agenda.hiddenCount} more today · tap to open", padX, footerY, morePaint)
            }
            return bitmap
        }

        private fun drawRow(
            canvas: Canvas,
            row: AgendaRow,
            top: Float,
            width: Float,
            padX: Float,
            s: Float,
            palette: FlowGlassWidgetProvider.SystemGlassPalette
        ) {
            val h = ROW_DP * s
            val cy = top + h / 2f
            val accent = palette.systemAccent
            val rowColor = (row.colorArgb and 0xFFFFFFFFL).toInt()

            if (row.isNow) {
                canvas.drawRoundRect(
                    RectF(padX - 6f * s, top + 3f * s, width - padX + 6f * s, top + h - 3f * s), 12f * s, 12f * s,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, if (palette.isDark) 46 else 34) }
                )
            }

            // Calendar color bar
            canvas.drawRoundRect(
                RectF(padX, cy - 10f * s, padX + 3.5f * s, cy + 10f * s), 2f * s, 2f * s,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (row.isDone) withAlpha(rowColor, 90) else rowColor }
            )

            // Time column
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    row.isOverdue -> rowColor
                    row.isNow -> accent
                    else -> palette.textSecondary
                }
                textSize = 10.5f * s
                typeface = Typeface.create(Typeface.DEFAULT, if (row.isNow || row.isOverdue) Typeface.BOLD else Typeface.NORMAL)
            }
            val timeX = padX + 10f * s
            val timeW = 66f * s
            canvas.drawText(ellipsizeText(row.timeLabel, timePaint, timeW - 4f * s), timeX, cy + 3.8f * s, timePaint)

            // Check circle (right). Drawn inside the 56dp tap target.
            val checkCx = width - 4f * s - 28f * s
            val r = 9f * s
            when {
                row.isDone -> {
                    canvas.drawCircle(checkCx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 210) })
                    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.8f * s; strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(checkCx - 4f * s, cy, checkCx - 1f * s, cy + 3.2f * s, tick)
                    canvas.drawLine(checkCx - 1f * s, cy + 3.2f * s, checkCx + 4.5f * s, cy - 3.5f * s, tick)
                }
                row.canToggle -> canvas.drawCircle(checkCx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.5f * s
                    color = if (row.isOverdue) rowColor else withAlpha(accent, 200)
                })
                else -> canvas.drawCircle(checkCx, cy, 2.2f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textTertiary })
            }

            // Title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (row.isDone || row.isTomorrow) palette.textTertiary else palette.textPrimary
                textSize = 12.5f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isStrikeThruText = row.isDone
            }
            val titleX = timeX + timeW
            val titleMax = checkCx - r - 10f * s - titleX
            if (titleMax > 20f * s) {
                canvas.drawText(ellipsizeText(row.title, titlePaint, titleMax), titleX, cy + 4.3f * s, titlePaint)
            }
        }
    }
}

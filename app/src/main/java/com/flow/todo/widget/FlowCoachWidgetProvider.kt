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
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import com.flow.todo.MainActivity
import com.flow.todo.R
import com.flow.todo.coach.CoachMessage
import com.flow.todo.coach.data.CoachController
import com.flow.todo.coach.work.CoachScheduler
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.ellipsizeText
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.resolveSystemGlassPalette
import com.flow.todo.widget.FlowGlassWidgetProvider.Companion.withAlpha
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Home-screen / lock-screen AI Coach card. Shows the latest message from the user's chosen
 * persona. Tapping the refresh glyph asks the LLM for a fresh comment; tapping anywhere else
 * opens Flow (or the coach setup sheet if no key has been added yet).
 *
 * Reuses the system-color-adaptive glass palette of [FlowGlassWidgetProvider].
 */
class FlowCoachWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        render(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_COACH_REFRESH -> {
                CoachScheduler.enqueueManual(context)
                requestUpdate(context)
            }
            Intent.ACTION_CONFIGURATION_CHANGED, Intent.ACTION_WALLPAPER_CHANGED -> requestUpdate(context)
        }
    }

    companion object {
        const val ACTION_COACH_REFRESH = "com.flow.todo.ACTION_COACH_REFRESH"
        const val ACTION_OPEN_COACH = "com.flow.todo.ACTION_OPEN_COACH"

        fun requestUpdate(context: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, FlowCoachWidgetProvider::class.java))
                if (ids.isNotEmpty()) render(context, mgr, ids)
            }
        }

        private fun render(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val controller = CoachController.get(context)
            val settings = controller.settings.value
            val configured = settings.enabled && settings.provider in controller.configuredProviders.value
            val message = controller.latest.value
            val status = controller.status.value

            val palette = resolveSystemGlassPalette(context, Color.parseColor("#FF4F8BFF"))

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_COACH
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPending = PendingIntent.getActivity(
                context, 301, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshPending = if (configured) {
                PendingIntent.getBroadcast(
                    context, 302,
                    Intent(context, FlowCoachWidgetProvider::class.java).setAction(ACTION_COACH_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                openPending // nothing to refresh yet: send the user to setup
            }

            for (id in ids) {
                val opts = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
                val widthDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250).coerceIn(110, 600)
                val heightDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110).coerceIn(56, 520)

                val bitmap = drawCard(
                    widthDp, heightDp, palette, message, configured, status.isGenerating,
                    status.error, System.currentTimeMillis()
                )
                val views = RemoteViews(context.packageName, R.layout.widget_coach)
                views.setImageViewBitmap(R.id.coach_canvas_image, bitmap)
                views.setOnClickPendingIntent(R.id.coach_btn_open_top, openPending)
                views.setOnClickPendingIntent(R.id.coach_btn_open_body, openPending)
                views.setOnClickPendingIntent(R.id.coach_btn_refresh, refreshPending)
                mgr.updateAppWidget(id, views)
            }
        }

        private fun drawCard(
            widthDp: Int,
            heightDp: Int,
            palette: FlowGlassWidgetProvider.SystemGlassPalette,
            message: CoachMessage?,
            configured: Boolean,
            generating: Boolean,
            error: String?,
            nowMillis: Long
        ): Bitmap {
            val s = 2.4f
            val width = (widthDp * s).roundToInt().coerceIn(260, 1440)
            val height = (heightDp * s).roundToInt().coerceIn(134, 1248)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val accent = palette.systemAccent

            val inset = 4f * s
            val card = RectF(inset, inset, width - inset, height - inset)
            val radius = min(26f * s, min(card.width(), card.height()) * 0.42f)

            canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), palette.glassBgTop, palette.glassBgBottom, Shader.TileMode.CLAMP)
            })
            canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width * 0.2f, height * 0.15f, maxOf(width, height) * 0.7f,
                    intArrayOf(withAlpha(accent, if (palette.isDark) 90 else 62), withAlpha(accent, 20), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            })
            canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, card.top, 0f, card.top + card.height() * 0.45f, palette.glassSheenTop, Color.TRANSPARENT, Shader.TileMode.CLAMP)
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
            val topY = 13f * s
            val buttonSize = 30f * s
            val refreshRect = RectF(width - padX - buttonSize, topY, width - padX, topY + buttonSize)
            drawRefreshButton(canvas, refreshRect, s, accent, palette.textPrimary, generating)

            // Header: persona + tone
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textSize = 10f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.1f
            }
            val header = when {
                !configured -> "AI COACH"
                message != null -> "${message.personaEmoji} ${message.personaName.uppercase()} · ${message.tone.label.uppercase()}"
                else -> "AI COACH"
            }
            canvas.drawText(
                ellipsizeText(header, headerPaint, refreshRect.left - padX - 8f * s),
                padX, topY + 19f * s, headerPaint
            )

            // Body
            val bodyText = when {
                !configured -> "Meet your AI coach. Tap to add your API key and pick a personality."
                generating -> "Thinking…"
                message != null -> message.text
                !error.isNullOrBlank() -> error
                else -> "Tap ↻ for your first message."
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = (if (heightDp < 100) 12.5f else 14f) * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val showFooter = heightDp >= 110 && configured && message != null && !generating
            val bodyTop = topY + 30f * s
            val footerHeight = if (showFooter) 22f * s else 8f * s
            val bodyBottom = height - inset - footerHeight - 6f * s
            val bodyWidth = (width - 2 * padX).roundToInt()
            val lineHeight = bodyPaint.fontMetrics.let { it.descent - it.ascent } * 1.08f
            val maxLines = ((bodyBottom - bodyTop) / lineHeight).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, bodyPaint, bodyWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.08f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(padX, bodyTop)
            layout.draw(canvas)
            canvas.restore()

            if (showFooter && message != null) {
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textTertiary
                    textSize = 9.5f * s
                }
                val about = if (message.topicLabels.isEmpty()) "" else " · ${message.topicLabels.joinToString(", ")}"
                canvas.drawText(
                    ellipsizeText(timeAgo(nowMillis - message.timeMillis) + about, footerPaint, width - 2 * padX),
                    padX, height - inset - 10f * s, footerPaint
                )
            }
            return bitmap
        }

        /** Draws a circular-arrow glyph without depending on font support for U+21BB. */
        private fun drawRefreshButton(canvas: Canvas, rect: RectF, s: Float, accent: Int, glyph: Int, busy: Boolean) {
            val r = rect.width() / 2f
            canvas.drawCircle(rect.centerX(), rect.centerY(), r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, if (busy) 40 else 78) })
            canvas.drawCircle(rect.centerX(), rect.centerY(), r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.1f * s; color = withAlpha(accent, 215)
            })
            val arcRect = RectF(rect.centerX() - r * 0.42f, rect.centerY() - r * 0.42f, rect.centerX() + r * 0.42f, rect.centerY() + r * 0.42f)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.8f * s; strokeCap = Paint.Cap.ROUND; color = glyph
            }
            canvas.drawArc(arcRect, -60f, 270f, false, stroke)
            // Arrow head at the arc's start (top-right).
            val ax = arcRect.centerX() + arcRect.width() / 2f * 0.5f
            val ay = arcRect.centerY() - arcRect.height() / 2f * 0.866f
            val head = Path().apply {
                moveTo(ax - 3.2f * s, ay - 2.6f * s)
                lineTo(ax + 4.2f * s, ay - 0.4f * s)
                lineTo(ax - 0.6f * s, ay + 4.2f * s)
                close()
            }
            canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glyph })
        }

        private fun timeAgo(deltaMillis: Long): String {
            val m = (deltaMillis / 60_000L).coerceAtLeast(0)
            return when {
                m < 1 -> "just now"
                m < 60 -> "${m}m ago"
                m < 24 * 60 -> "${m / 60}h ago"
                else -> "${m / (24 * 60)}d ago"
            }
        }
    }
}

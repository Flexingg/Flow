package com.flow.todo.system

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flow.todo.MainActivity
import com.flow.todo.widget.FlowGlassWidgetProvider
import kotlin.math.roundToInt

/**
 * Android Quick Settings Tile displaying live Flow Goal Momentum percentage
 * and goals progress, with 1-tap Quick Capture sheet launch.
 */
class FlowQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = FlowGlassWidgetProvider.ACTION_QUICK_CAPTURE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(FlowGlassWidgetProvider.WIDGET_CACHE_PREFS, Context.MODE_PRIVATE)
        val momentumProgress = prefs.getFloat(FlowGlassWidgetProvider.KEY_MOMENTUM_PROGRESS, 0f).coerceIn(0f, 1f)
        val completedGoals = prefs.getInt(FlowGlassWidgetProvider.KEY_COMPLETED_GOALS, 0)
        val totalGoals = prefs.getInt(FlowGlassWidgetProvider.KEY_TOTAL_GOALS, 0)

        val pct = (momentumProgress * 100f).roundToInt()
        tile.label = "Flow • $pct%"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "$completedGoals/$totalGoals goals • Tap to capture"
        }
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, FlowQuickSettingsTileService::class.java)
                )
            }
        }
    }
}

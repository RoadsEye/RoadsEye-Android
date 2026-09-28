package com.roadseye.dashcam.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.roadseye.dashcam.MainActivity
import com.roadseye.dashcam.R
import com.roadseye.dashcam.settingssection.SPEED_UNITS_KEY
import com.roadseye.dashcam.settingssection.STAT_DISTANCE_METERS_KEY
import com.roadseye.dashcam.settingssection.STAT_DRIVES_KEY
import com.roadseye.dashcam.settingssection.STAT_RECORDING_TIME_MS_KEY
import com.roadseye.dashcam.settingssection.STAT_TOP_SPEED_MS_KEY
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home screen widget showing lifetime driving stats. */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                pushUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "StatsWidgetProvider"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /** Fire-and-forget refresh of every placed stats widget. */
        fun requestUpdate(context: Context) {
            scope.launch {
                try {
                    pushUpdate(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update stats widget", e)
                }
            }
        }

        private suspend fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val prefs = context.dataStore.data.first()
            val speedUnit = prefs[SPEED_UNITS_KEY] ?: "MPH"
            val distanceMeters = prefs[STAT_DISTANCE_METERS_KEY] ?: 0.0
            val recordingMs = prefs[STAT_RECORDING_TIME_MS_KEY] ?: 0L
            val topSpeedMs = prefs[STAT_TOP_SPEED_MS_KEY] ?: 0.0
            val drives = prefs[STAT_DRIVES_KEY] ?: 0

            val speedMultiplier = if (speedUnit == "MPH") 2.23694 else 3.6

            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            views.setTextViewText(R.id.widget_stat_distance, formatDistance(distanceMeters, speedUnit))
            views.setTextViewText(R.id.widget_stat_time, formatDuration(recordingMs))
            views.setTextViewText(R.id.widget_stat_top_speed, "%.0f %s".format(topSpeedMs * speedMultiplier, speedUnit))
            views.setTextViewText(R.id.widget_stat_drives, drives.toString())

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_stats_root, pendingIntent)

            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun formatDistance(meters: Double, speedUnit: String): String {
            return if (speedUnit == "MPH") {
                val miles = meters / 1609.344
                if (miles >= 100) "%,.0f mi".format(miles) else "%.1f mi".format(miles)
            } else {
                val km = meters / 1000.0
                if (km >= 100) "%,.0f km".format(km) else "%.1f km".format(km)
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalMinutes = ms / 60_000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                totalMinutes > 0 -> "${minutes}m"
                else -> "${(ms / 1000).coerceAtLeast(0)}s"
            }
        }
    }
}

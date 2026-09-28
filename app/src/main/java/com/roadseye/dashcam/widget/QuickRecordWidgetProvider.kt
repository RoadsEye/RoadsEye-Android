package com.roadseye.dashcam.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.roadseye.dashcam.MainActivity
import com.roadseye.dashcam.R
import com.roadseye.dashcam.RecordingManager

/** Home screen widget that opens the app and starts recording with one tap. */
class QuickRecordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val isRecording = RecordingManager.getInstance(context).isRecording.value
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, isRecording))
        }
    }

    companion object {
        const val ACTION_START_RECORDING = "com.roadseye.dashcam.action.WIDGET_START_RECORDING"

        /** Refreshes every placed widget; call when recording state changes. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickRecordWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val isRecording = RecordingManager.getInstance(context).isRecording.value
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, isRecording)) }
        }

        private fun buildViews(context: Context, isRecording: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_record)
            views.setTextViewText(
                R.id.widget_record_label,
                if (isRecording) "Recording…" else "Start Recording"
            )

            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_START_RECORDING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_quick_record_root, pendingIntent)
            return views
        }
    }
}

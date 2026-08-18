package com.miradio.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.miradio.app.MainActivity
import com.miradio.app.R
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.playback.PlaybackService
import com.miradio.app.util.DiagnosticsLog

/**
 * Widget de pantalla de inicio: nombre de la emisora actual, play/pause y
 * cambiar de emisora (siguiente favorita). Como un [AppWidgetProvider] no
 * vive todo el tiempo, las acciones se resuelven arrancando/hablando con
 * [PlaybackService], que es quien mantiene el estado real de reproducción.
 */
class RadioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DiagnosticsLog.log(context, "RadioWidgetProvider", "onUpdate(${appWidgetIds.size} widgets)")
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context, null, PlaybackStatus.STOPPED))
        }
    }

    companion object {
        fun updateAll(context: Context, stationName: String?, status: PlaybackStatus) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RadioWidgetProvider::class.java))
            if (ids.isEmpty()) return
            DiagnosticsLog.log(context, "RadioWidgetProvider", "updateAll(\"$stationName\", $status)")
            val views = buildRemoteViews(context, stationName, status)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildRemoteViews(context: Context, stationName: String?, status: PlaybackStatus): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_radio)

            views.setTextViewText(
                R.id.widget_station_name,
                stationName ?: context.getString(R.string.widget_no_station),
            )
            val statusRes = when (status) {
                PlaybackStatus.PLAYING -> R.string.player_status_playing
                PlaybackStatus.BUFFERING -> R.string.player_buffering
                PlaybackStatus.PAUSED -> R.string.player_status_paused
                PlaybackStatus.ERROR -> R.string.player_status_error
                else -> R.string.player_status_stopped
            }
            views.setTextViewText(R.id.widget_station_status, context.getString(statusRes))
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING) R.drawable.ic_pause else R.drawable.ic_play,
            )

            views.setOnClickPendingIntent(R.id.widget_play_pause, servicePendingIntent(context, PlaybackService.ACTION_TOGGLE_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, servicePendingIntent(context, PlaybackService.ACTION_NEXT_STATION, 2))

            val openAppIntent = PendingIntent.getActivity(
                context,
                3,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_logo, openAppIntent)

            return views
        }

        private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

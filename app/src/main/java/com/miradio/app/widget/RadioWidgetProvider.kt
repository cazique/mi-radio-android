package com.miradio.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import coil.Coil
import coil.request.ImageRequest
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
        /** [logoUrl] es opcional a propósito: quien llama sin conocerlo (p. ej.
         *  [onUpdate], al colocar el widget sin nada sonando aún) simplemente
         *  se queda con el logo de reserva del layout. */
        suspend fun updateAll(context: Context, stationName: String?, status: PlaybackStatus, logoUrl: String? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RadioWidgetProvider::class.java))
            if (ids.isEmpty()) return
            DiagnosticsLog.log(context, "RadioWidgetProvider", "updateAll(\"$stationName\", $status)")
            val logoBitmap = logoUrl?.let { loadLogoBitmap(context, it) }
            val views = buildRemoteViews(context, stationName, status, logoBitmap)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        /** Un widget no vive dentro del proceso de la app (RemoteViews cruza a
         *  otra vista de sistema): solo puede llevar un Bitmap ya decodificado,
         *  nunca una URL que Coil resuelva sobre la marcha como en el resto de
         *  la app. allowHardware(false) es imprescindible aquí: un bitmap
         *  "hardware" (el que usa Coil por defecto en Android 8+) no se puede
         *  mandar a otro proceso y RemoteViews lo rechaza. */
        private suspend fun loadLogoBitmap(context: Context, url: String): Bitmap? = runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(128, 128)
                .allowHardware(false)
                .build()
            (Coil.imageLoader(context).execute(request).drawable as? BitmapDrawable)?.bitmap
        }.onFailure {
            DiagnosticsLog.logThrowable(context, "RadioWidgetProvider", "No se pudo cargar el logo del widget", it)
        }.getOrNull()

        private fun buildRemoteViews(context: Context, stationName: String?, status: PlaybackStatus, logoBitmap: Bitmap? = null): RemoteViews {
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
            if (logoBitmap != null) {
                views.setImageViewBitmap(R.id.widget_logo, logoBitmap)
            } else {
                views.setImageViewResource(R.id.widget_logo, android.R.color.transparent)
            }

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

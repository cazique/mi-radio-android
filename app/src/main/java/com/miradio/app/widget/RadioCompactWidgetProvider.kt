package com.miradio.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.miradio.app.util.DiagnosticsLog

/**
 * Widget compacto: solo el logo grande de la emisora actual y un botón de
 * reproducir/pausar, pensado para el hueco justo de un icono en vez de una
 * fila entera de pantalla. Comparte toda la lógica de actualización con
 * [RadioWidgetProvider] (ver `updateAll`), que actualiza ambos widgets a la
 * vez sin que el resto de la app tenga que saber que existen dos.
 */
class RadioCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DiagnosticsLog.log(context, "RadioCompactWidgetProvider", "onUpdate(${appWidgetIds.size} widgets)")
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, RadioWidgetProvider.buildInitialCompactRemoteViews(context))
        }
    }
}

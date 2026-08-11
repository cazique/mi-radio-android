package com.miradio.app.ui.components

import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.miradio.app.R

private const val TAG = "CastButton"

/**
 * Botón de Cast estándar de Google: al pulsarlo abre el selector de
 * dispositivos del sistema (Chromecast, Nest, Android TV...). Se implementa
 * envolviendo el [MediaRouteButton] clásico de `androidx.mediarouter` porque
 * es el único componente que Google mantiene oficialmente para esta acción;
 * Compose no tiene un equivalente propio.
 *
 * Si el SDK de Cast no puede inicializarse en este dispositivo (Google Play
 * Services desactualizado o ausente), no debe tirar abajo toda la app: se
 * deja un espacio vacío en su lugar en vez de dejar que la excepción se
 * propague.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_cast)
    // El icono del MediaRouteButton toma su color del tema nativo de la
    // Activity, que solo sigue el modo oscuro del SISTEMA, no el selector de
    // tema propio de la app. Se envuelve el contexto con un tema explícito
    // según el tema que esté usando realmente Compose ahora mismo, para que
    // el icono no se quede invisible cuando ambos no coinciden. key(isDark)
    // fuerza a recrear la vista si el tema cambia en caliente.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    key(isDark) {
        AndroidView(
            modifier = modifier.semantics { contentDescription = description },
            factory = { context ->
                try {
                    val themedContext = ContextThemeWrapper(
                        context,
                        if (isDark) R.style.Theme_MiRadio_CastDark else R.style.Theme_MiRadio_CastLight,
                    )
                    MediaRouteButton(themedContext).apply {
                        CastButtonFactory.setUpMediaRouteButton(themedContext, this)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Google Cast no disponible en este dispositivo: ${e.message}")
                    View(context).apply { layoutParams = FrameLayout.LayoutParams(0, 0) }
                }
            },
        )
    }
}

package com.miradio.app.ui.components

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { context ->
            try {
                MediaRouteButton(context).apply {
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Cast no disponible en este dispositivo: ${e.message}")
                View(context).apply { layoutParams = FrameLayout.LayoutParams(0, 0) }
            }
        },
    )
}

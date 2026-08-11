package com.miradio.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.miradio.app.R

/**
 * Botón de Cast estándar de Google: al pulsarlo abre el selector de
 * dispositivos del sistema (Chromecast, Nest, Android TV...). Se implementa
 * envolviendo el [MediaRouteButton] clásico de `androidx.mediarouter` porque
 * es el único componente que Google mantiene oficialmente para esta acción;
 * Compose no tiene un equivalente propio.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_cast)
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { context ->
            MediaRouteButton(context).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
            }
        },
    )
}

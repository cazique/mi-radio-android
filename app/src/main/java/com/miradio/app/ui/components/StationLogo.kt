package com.miradio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.miradio.app.R

@Composable
fun StationLogo(logoUrl: String?, modifier: Modifier = Modifier, cornerRadius: Int = 16) {
    val hasLogo = !logoUrl.isNullOrBlank()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            // Casi todos los logos de emisora vienen con fondo blanco de
            // fábrica; usar el gris/oscuro del tema detrás se notaba como un
            // borde/recuadro descolocado. Blanco fijo cuando hay logo real
            // hace que se vea como una sola pieza; el icono de "sin logo"
            // mantiene el color del tema para no destacar de más.
            .background(if (hasLogo) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasLogo) {
            Icon(
                imageVector = Icons.Filled.Radio,
                contentDescription = stringResource(R.string.cd_station_logo),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context).data(logoUrl).crossfade(true).build(),
                contentDescription = stringResource(R.string.cd_station_logo),
                // Fit en vez de Crop: muchos logos de emisora no son
                // cuadrados (marca + texto debajo), así que recortarlos
                // podía cortar el logo y descentrarlo visualmente. Con Fit
                // se ve el logo completo, centrado dentro del hueco cuadrado.
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
    }
}

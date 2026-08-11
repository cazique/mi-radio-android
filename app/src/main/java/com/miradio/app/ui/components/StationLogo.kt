package com.miradio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.miradio.app.R

@Composable
fun StationLogo(logoUrl: String?, modifier: Modifier = Modifier, cornerRadius: Int = 16) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Radio,
                contentDescription = stringResource(R.string.cd_station_logo),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = stringResource(R.string.cd_station_logo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

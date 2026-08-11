package com.miradio.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miradio.app.R
import com.miradio.app.domain.model.RadioStation

@Composable
fun StationListItem(
    station: RadioStation,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(logoUrl = station.logoUrl, modifier = Modifier.size(52.dp), cornerRadius = 12)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (station.isAvailable) station.city else "${station.city} · no disponible",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onEditClick != null) {
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.stations_edit_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FavoriteToggle(isFavorite = station.isFavorite, onClick = onFavoriteClick)
    }
}

@Composable
private fun RowScope.FavoriteToggle(isFavorite: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (isFavorite) R.string.player_remove_favorite else R.string.cd_favorite)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

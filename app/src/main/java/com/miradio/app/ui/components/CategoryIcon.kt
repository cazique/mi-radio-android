package com.miradio.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

/** Icono decorativo por categoría, para que la lista se lea de un vistazo
 *  cuando hay cientos de emisoras. La categoría es una inferencia orientativa,
 *  no un dato oficial de la emisora. */
fun categoryIcon(category: String?): ImageVector = when (category?.trim()) {
    "Música" -> Icons.Filled.MusicNote
    "Informativos / Pública" -> Icons.Filled.Public
    "Deportes" -> Icons.Filled.SportsSoccer
    "Religiosa" -> Icons.Filled.Church
    else -> Icons.Filled.Radio
}

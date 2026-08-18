package com.miradio.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.miradio.app.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = RadioBluePrimary,
    onPrimary = LightSurface,
    secondary = RadioOrangeAccent,
    onSecondary = LightSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = RadioBluePrimaryDark,
    // OJO: RadioBluePrimaryDark es un azul pastel CLARO (a propósito, para
    // que resalte como texto/tinte sobre fondos oscuros). Como color de
    // FONDO de un botón relleno necesita texto oscuro encima, no claro: con
    // DarkOnBackground (casi blanco) el texto quedaba casi invisible sobre
    // ese mismo azul claro.
    onPrimary = DarkBackground,
    secondary = RadioOrangeAccent,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    error = ErrorRed,
)

@Composable
fun MiRadioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // El color dinámico de Android 12+ (Material You) sustituye la paleta de
    // marca por una generada a partir del fondo de pantalla del usuario.
    // Activado por defecto (ver PreferencesRepository.dynamicColorEnabled);
    // este valor por defecto del parámetro solo se usa si algún llamador no
    // pasa el ajuste guardado explícitamente (p. ej. una @Preview).
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiRadioTypography,
        content = content,
    )
}

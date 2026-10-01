package com.mixcasete.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta del boceto: línea negra gruesa sobre fondo claro; tema oscuro en grafito.
val Paper = Color(0xFFF4EFE6)          // fondo claro "papel"
val InkBlack = Color(0xFF14120F)       // línea negra gruesa
val Graphite = Color(0xFF23262B)       // carcasa grafito (tema oscuro)
val GraphiteDeep = Color(0xFF17191D)   // fondo oscuro
val CreamLabel = Color(0xFFEDE6D6)     // etiqueta clara del casete
val DarkLabel = Color(0xFF2B2E33)      // etiqueta oscura del casete
val AccentAmber = Color(0xFFC98A2D)    // acento retro (perillas/vumeter)
val ScreenGreen = Color(0xFF9FD356)    // mini pantalla LCD

private val LightColors = lightColorScheme(
    primary = InkBlack,
    onPrimary = Paper,
    secondary = AccentAmber,
    background = Paper,
    onBackground = InkBlack,
    surface = Color(0xFFFBF8F1),
    onSurface = InkBlack,
    error = Color(0xFF8A1F1F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8E4DA),
    onPrimary = GraphiteDeep,
    secondary = AccentAmber,
    background = GraphiteDeep,
    onBackground = Color(0xFFE8E4DA),
    surface = Graphite,
    onSurface = Color(0xFFE8E4DA),
    error = Color(0xFFFF8A80)
)

@Composable
fun MixCaseteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

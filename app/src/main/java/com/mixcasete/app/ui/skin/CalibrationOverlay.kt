package com.mixcasete.app.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Modo calibración (debug): dibuja las zonas del SkinLayout sobre el lienzo para poder
 * ajustar las fracciones mirando la pantalla. Se activa desde Ajustes.
 */
@Composable
fun CalibrationOverlay(
    portrait: Boolean,
    modifier: Modifier = Modifier
) {
    val zones = if (portrait) SkinLayout.portraitZones() else SkinLayout.landscapeZones()
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            zones.forEachIndexed { i, (name, zone) ->
                val r = zone.toRect(w, h)
                val color = Color(0xFF2196F3).copy(alpha = 0.85f)
                drawRect(
                    color = color.copy(alpha = 0.10f),
                    topLeft = r.topLeft,
                    size = r.size
                )
                drawRect(
                    color = color,
                    topLeft = r.topLeft,
                    size = r.size,
                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
                // Nombre de la zona en la esquina superior izquierda
                drawTextDebug(name, r.topLeft + Offset(4f, 14f), color)
            }
            // Cruz central del lienzo
            drawLine(Color.Red.copy(alpha = 0.4f), Offset(w / 2 - 20, h / 2), Offset(w / 2 + 20, h / 2), 2f)
            drawLine(Color.Red.copy(alpha = 0.4f), Offset(w / 2, h / 2 - 20), Offset(w / 2, h / 2 + 20), 2f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextDebug(
    text: String,
    at: Offset,
    color: Color
) {
    // Dibujo económico sin Paint de texto: marcador cuadrado + líneas como "etiqueta"
    drawRect(color = color.copy(alpha = 0.9f), topLeft = at - Offset(0f, 10f), size = androidx.compose.ui.geometry.Size(3f, 3f))
}

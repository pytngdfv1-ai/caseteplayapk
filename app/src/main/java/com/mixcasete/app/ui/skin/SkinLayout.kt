package com.mixcasete.app.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints

/**
 * Layout de la "piel" (skin) del reproductor. Todas las posiciones y tamaños se expresan
 * en fracciones 0..1 del lienzo; el sistema de coordenadas interno es fijo (BASE_W x BASE_H)
 * y la UI lo escala proporcionalmente: NUNCA se estira, solo cambia su escala.
 */
object SkinLayout {

    const val BASE_W = 360f
    const val BASE_H_PORTRAIT = 720f   // vertical: altavoz arriba + franja + casete + teclas
    const val BASE_H_LANDSCAPE = 400f  // horizontal: sin altavoz

    /** Zonas rectangulares en fracciones (x, y, w, h) sobre el lienzo. */
    data class Zone(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun toRect(canvasW: Float, canvasH: Float): Rect =
            Rect(x * canvasW, y * canvasH, (x + w) * canvasW, (y + h) * canvasH)
    }

    // ------------------------------------------------------------------ Vertical

    val speaker = Zone(0.08f, 0.02f, 0.84f, 0.20f)          // rejilla de altavoz (círculos)
    val buttonStrip = Zone(0.08f, 0.235f, 0.84f, 0.075f)    // franja: 3 perillas + mini pantalla
    val knobs = listOf(                                      // volumen, tono, balance
        Zone(0.11f, 0.245f, 0.09f, 0.055f),
        Zone(0.225f, 0.245f, 0.09f, 0.055f),
        Zone(0.34f, 0.245f, 0.09f, 0.055f)
    )
    val miniScreen = Zone(0.47f, 0.2475f, 0.42f, 0.05f)     // barra de progreso en la pantalla
    val window = Zone(0.06f, 0.33f, 0.88f, 0.36f)           // ventana del casete (marco biselado)
    val tape = Zone(0.10f, 0.355f, 0.80f, 0.31f)            // cuerpo del casete dentro de la ventana
    val lid = Zone(0.06f, 0.33f, 0.88f, 0.36f)              // tapa de vidrio (misma zona que la ventana)
    val slots = Zone(0.14f, 0.70f, 0.72f, 0.02f)            // fila de ranuras bajo la ventana
    val keys = Zone(0.06f, 0.74f, 0.88f, 0.20f)             // teclado de 6 teclas grandes

    /** Ojos del casete (centros de los carretes) en fracciones de [tape]. */
    val leftReelTapeFrac = 0.27f to 0.52f
    val rightReelTapeFrac = 0.73f to 0.52f
    val labelBandTapeFrac = Triple(0.12f, 0.06f, 0.76f to 0.22f) // x,y + (w,h) etiqueta título/artista

    // ------------------------------------------------------------------ Horizontal (sin altavoz)

    val lWindow = Zone(0.06f, 0.06f, 0.56f, 0.62f)
    val lTape = Zone(0.09f, 0.09f, 0.50f, 0.56f)
    val lLid = Zone(0.06f, 0.06f, 0.56f, 0.62f)
    val lButtonStrip = Zone(0.66f, 0.06f, 0.28f, 0.14f)
    val lKnobs = listOf(
        Zone(0.67f, 0.075f, 0.07f, 0.05f),
        Zone(0.765f, 0.075f, 0.07f, 0.05f),
        Zone(0.67f, 0.145f, 0.07f, 0.05f)
    )
    val lMiniScreen = Zone(0.765f, 0.145f, 0.17f, 0.05f)
    val lKeys = Zone(0.66f, 0.26f, 0.28f, 0.68f)            // teclas a un lado
    val lSlots = Zone(0.10f, 0.70f, 0.48f, 0.03f)

    /** Todas las zonas visibles para el modo de calibración. */
    fun portraitZones(): List<Pair<String, Zone>> = listOf(
        "speaker" to speaker,
        "buttonStrip" to buttonStrip,
        "knobVol" to knobs[0],
        "knobTone" to knobs[1],
        "knobBal" to knobs[2],
        "miniScreen" to miniScreen,
        "window" to window,
        "tape" to tape,
        "lid" to lid,
        "slots" to slots,
        "keys" to keys
    )

    fun landscapeZones(): List<Pair<String, Zone>> = listOf(
        "window" to lWindow,
        "tape" to lTape,
        "lid" to lLid,
        "buttonStrip" to lButtonStrip,
        "knobVol" to lKnobs[0],
        "knobTone" to lKnobs[1],
        "knobBal" to lKnobs[2],
        "miniScreen" to lMiniScreen,
        "keys" to lKeys,
        "slots" to lSlots
    )
}

/**
 * Lienzo de escala fija: mide el contenido (BASE_W x baseH) y lo escala uniformemente
 * para caber en [maxWidth, maxHeight] centrado. Los hijos siempre ven restricciones
 * exactas del tamaño base -> nunca hay deformación.
 */
@Composable
fun ScaledCanvas(
    baseW: Float,
    baseH: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // AspectRatio fijo: el lienzo mantiene la proporción baseW/baseH; solo cambia su escala.
    BoxWithConstraints(modifier.aspectRatio(baseW / baseH, matchHeightConstraintsFirst = true)) {
        Layout(content) { measurables, constraints ->
            val targetW = constraints.maxWidth.toFloat()
            val targetH = constraints.maxHeight.toFloat()
            val s = minOf(targetW / baseW, targetH / baseH)
            val pxW = (baseW * s).toInt().coerceAtLeast(1)
            val pxH = (baseH * s).toInt().coerceAtLeast(1)
            val placeables = measurables.map {
                it.measure(Constraints.fixed(pxW, pxH))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { p ->
                    p.place((pxW - p.width) / 2, (pxH - p.height) / 2)
                }
            }
        }
    }
}

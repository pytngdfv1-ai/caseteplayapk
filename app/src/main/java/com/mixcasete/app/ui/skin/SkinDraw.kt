package com.mixcasete.app.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mixcasete.app.ui.theme.AccentAmber
import com.mixcasete.app.ui.theme.InkBlack
import com.mixcasete.app.ui.theme.Paper
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dibujo vectorial del reproductor estilo boceto: línea negra gruesa sobre fondo claro.
 * Todo se dibuja en coordenadas reales del lienzo (medido por ScaledCanvas con escala
 * uniforme), tomando las zonas de SkinLayout en fracciones 0..1. Nunca se deforma.
 */
private const val STROKE = 4f   // grosor de línea "boceto" (px del lienzo base)

class SkinColors(
    val ink: Color,
    val body: Color,
    val fill: Color,
    val labelBg: Color,
    val labelText: Color,
    val tapeDark: Color,
    val screenBg: Color,
    val glass: Color
)

@Composable
fun SkinCanvas(
    portrait: Boolean,
    dark: Boolean,
    progress: Float,          // 0..1 cinta trasladada entre carretes
    reelAngleDeg: Float,      // rotación actual (solo avanza si playing)
    lidOpenFraction: Float,   // 0 cerrada .. 1 abierta
    shineShift: Float,        // -1..1 reflejo desplazado por acelerómetro
    pressedKey: Int?,         // índice 0..5 de tecla hundida
    miniProgress: Float       // 0..1 barra de progreso de la mini pantalla
) {
    val c = if (dark) SkinColors(
        ink = Color(0xFFD8D4CA),
        body = Color(0xFF2B2E33),
        fill = Color(0xFF17191D),
        labelBg = Color(0xFF23262B),
        labelText = Color(0xFFEDE6D6),
        tapeDark = Color(0xFF0C0D0F),
        screenBg = Color(0xFF101418),
        glass = Color(0x22FFFFFF)
    ) else SkinColors(
        ink = InkBlack,
        body = Paper,
        fill = Color(0xFFFFFFFF),
        labelBg = Color(0xFFEDE6D6),
        labelText = InkBlack,
        tapeDark = Color(0xFF2A2320),
        screenBg = Color(0xFFDDE8CE),
        glass = Color(0x1F202020)
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Escala respecto al lienzo base para mantener proporción de trazos
        val s = w / SkinLayout.BASE_W
        val sw = STROKE * s * (h / (if (portrait) SkinLayout.BASE_H_PORTRAIT else SkinLayout.BASE_H_LANDSCAPE)).let { k ->
            ((s + k) / 2f).coerceIn(0.5f, 3f)
        }

        if (portrait) {
            drawSpeaker(c, w, h, sw)
            drawButtonStrip(c, w, h, sw, miniProgress)
            drawWindowAndKeys(c, w, h, s, sw, progress, reelAngleDeg, lidOpenFraction, shineShift, pressedKey, portrait = true)
        } else {
            drawButtonStripLandscape(c, w, h, sw, miniProgress)
            drawWindowAndKeys(c, w, h, s, sw, progress, reelAngleDeg, lidOpenFraction, shineShift, pressedKey, portrait = false)
        }
    }
}

// ---------------------------------------------------------------- Altavoz (vertical)

private fun DrawScope.drawSpeaker(c: SkinColors, w: Float, h: Float, sw: Float) {
    val r = SkinLayout.speaker.toRect(w, h)
    // rejilla de círculos: puntos rellenos en patrón hexagonal
    val cols = 11
    val rows = 5
    val dx = r.width / (cols + 1)
    val dy = r.height / (rows + 1)
    val rad = minOf(dx, dy) * 0.34f
    for (row in 0 until rows) {
        val offset = if (row % 2 == 1) dx / 2 else 0f
        for (col in 0..cols) {
            val x = r.left + dx * (col + 0.5f) + offset
            val y = r.top + dy * (row + 0.5f)
            if (x > r.right - rad || y > r.bottom - rad) continue
            drawCircle(c.ink.copy(alpha = 0.85f), radius = rad, center = Offset(x, y))
        }
    }
}

// ---------------------------------------------------------------- Franja con perillas + mini pantalla

private fun DrawScope.stripContent(
    c: SkinColors,
    strip: androidx.compose.ui.geometry.Rect,
    knobs: List<SkinLayout.Zone>,
    screen: SkinLayout.Zone,
    w: Float,
    h: Float,
    sw: Float,
    miniProgress: Float
) {
    // carcasa de la franja
    drawRoundRect(
        color = c.fill,
        topLeft = strip.topLeft, size = strip.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(strip.height / 3f, strip.height / 3f),
        style = Stroke(width = sw)
    )
    // tres perillas redondas: volumen, tono, balance
    knobs.forEachIndexed { i, z ->
        val kr = z.toRect(w, h)
        val center = Offset(kr.center.x, kr.center.y)
        val rad = minOf(kr.width, kr.height) / 2f
        drawCircle(c.fill, radius = rad, center = center)
        drawCircle(c.ink, radius = rad, center = center, style = Stroke(width = sw))
        // marca de la perilla (posición angular según valor por defecto simulado)
        val ang = (-140f + i * 40f) * Math.PI / 180.0
        drawLine(
            c.ink,
            start = center,
            end = center + Offset((rad * 0.8f * cos(ang)).toFloat(), (rad * 0.8f * sin(ang)).toFloat()),
            strokeWidth = sw, cap = StrokeCap.Round
        )
        drawCircle(c.ink, radius = rad * 0.12f, center = center)
    }
    // pequeña pantalla rectangular con barra de progreso
    val sr = screen.toRect(w, h)
    drawRoundRect(
        color = c.screenBg, topLeft = sr.topLeft, size = sr.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(sr.height / 4f, sr.height / 4f),
        style = Fill
    )
    drawRoundRect(
        color = c.ink, topLeft = sr.topLeft, size = sr.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(sr.height / 4f, sr.height / 4f),
        style = Stroke(width = sw)
    )
    val barH = sr.height * 0.28f
    val barY = sr.bottom - barH - sr.height * 0.18f
    drawRect(c.ink.copy(alpha = 0.25f), Offset(sr.left + sw * 1.5f, barY),
        Size(sr.width - sw * 3f, barH))
    drawRect(AccentAmber, Offset(sr.left + sw * 1.5f, barY),
        Size((sr.width - sw * 3f) * miniProgress.coerceIn(0f, 1f), barH))
}

private fun DrawScope.drawButtonStrip(c: SkinColors, w: Float, h: Float, sw: Float, miniProgress: Float) {
    stripContent(c, SkinLayout.buttonStrip.toRect(w, h), SkinLayout.knobs, SkinLayout.miniScreen, w, h, sw, miniProgress)
}

private fun DrawScope.drawButtonStripLandscape(c: SkinColors, w: Float, h: Float, sw: Float, miniProgress: Float) {
    stripContent(c, SkinLayout.lButtonStrip.toRect(w, h), SkinLayout.lKnobs, SkinLayout.lMiniScreen, w, h, sw, miniProgress)
}

// ---------------------------------------------------------------- Ventana + casete + tapa + ranuras + teclas

private fun DrawScope.drawWindowAndKeys(
    c: SkinColors,
    w: Float, h: Float, s: Float, sw: Float,
    progress: Float, reelAngleDeg: Float, lidOpenFraction: Float, shineShift: Float,
    pressedKey: Int?, portrait: Boolean
) {
    val window = (if (portrait) SkinLayout.window else SkinLayout.lWindow).toRect(w, h)
    val tapeZone = (if (portrait) SkinLayout.tape else SkinLayout.lTape).toRect(w, h)
    val keysZone = (if (portrait) SkinLayout.keys else SkinLayout.lKeys).toRect(w, h)
    val slotsZone = (if (portrait) SkinLayout.slots else SkinLayout.lSlots).toRect(w, h)

    // marco biselado de la ventana
    drawRoundRect(c.fill, window.topLeft, window.size,
        androidx.compose.ui.geometry.CornerRadius(window.width * 0.06f, window.width * 0.06f), Fill)
    // bisel interior
    val inset = sw * 2.5f
    drawRoundRect(c.ink.copy(alpha = 0.35f),
        Offset(window.left + inset, window.top + inset),
        Size(window.width - inset * 2, window.height - inset * 2),
        androidx.compose.ui.geometry.CornerRadius(inset, inset), Fill)
    drawRoundRect(c.ink, window.topLeft, window.size,
        androidx.compose.ui.geometry.CornerRadius(window.width * 0.06f, window.width * 0.06f),
        Stroke(width = sw))

    // cuerpo del casete
    drawRoundRect(c.body, tapeZone.topLeft, tapeZone.size,
        androidx.compose.ui.geometry.CornerRadius(tapeZone.width * 0.05f, tapeZone.width * 0.05f), Fill)
    drawRoundRect(c.ink, tapeZone.topLeft, tapeZone.size,
        androidx.compose.ui.geometry.CornerRadius(tapeZone.width * 0.05f, tapeZone.width * 0.05f),
        Stroke(width = sw))

    // etiqueta superior del casete (título/artista se superponen como texto Compose encima)
    val (lx, ly) = SkinLayout.labelBandTapeFrac.first to SkinLayout.labelBandTapeFrac.second
    val (lw, lh) = SkinLayout.labelBandTapeFrac.third.first to SkinLayout.labelBandTapeFrac.third.second
    val label = androidx.compose.ui.geometry.Rect(
        tapeZone.left + lx * tapeZone.width, tapeZone.top + ly * tapeZone.height,
        tapeZone.left + (lx + lw) * tapeZone.width, tapeZone.top + (ly + lh) * tapeZone.height
    )
    drawRoundRect(c.labelBg, label.topLeft, label.size,
        androidx.compose.ui.geometry.CornerRadius(label.height * 0.2f, label.height * 0.2f), Fill)
    drawRoundRect(c.ink.copy(alpha = 0.7f), label.topLeft, label.size,
        androidx.compose.ui.geometry.CornerRadius(label.height * 0.2f, label.height * 0.2f),
        Stroke(width = sw * 0.6f))

    // tornillos en esquinas
    listOf(
        Offset(tapeZone.left + tapeZone.width * 0.06f, tapeZone.top + tapeZone.height * 0.08f),
        Offset(tapeZone.right - tapeZone.width * 0.06f, tapeZone.top + tapeZone.height * 0.08f),
        Offset(tapeZone.left + tapeZone.width * 0.06f, tapeZone.bottom - tapeZone.height * 0.08f),
        Offset(tapeZone.right - tapeZone.width * 0.06f, tapeZone.bottom - tapeZone.height * 0.08f)
    ).forEach { p ->
        drawCircle(c.ink.copy(alpha = 0.8f), radius = sw * 1.1f, center = p)
        drawCircle(c.body, radius = sw * 0.5f, center = p)
    }

    // orificio central trapezoidal inferior típico del casete
    val holeW = tapeZone.width * 0.34f
    val holeH = tapeZone.height * 0.16f
    val holeTop = tapeZone.bottom - holeH - tapeZone.height * 0.06f
    drawPath(trapezoid(
        tapeZone.center.x - holeW / 2, holeTop,
        tapeZone.center.x + holeW / 2, holeTop,
        tapeZone.center.x + holeW * 0.38f, holeTop + holeH,
        tapeZone.center.x - holeW * 0.38f, holeTop + holeH
    ), color = c.tapeDark, style = Fill)

    // carretes: disco de cinta (radio variable) + cubo dentado giratorio
    val leftCenter = Offset(
        tapeZone.left + SkinLayout.leftReelTapeFrac.first * tapeZone.width,
        tapeZone.top + SkinLayout.leftReelTapeFrac.second * tapeZone.height
    )
    val rightCenter = Offset(
        tapeZone.left + SkinLayout.rightReelTapeFrac.first * tapeZone.width,
        tapeZone.top + SkinLayout.rightReelTapeFrac.second * tapeZone.height
    )
    val maxR = tapeZone.height * 0.24f
    val minR = maxR * 0.45f
    val p = progress.coerceIn(0f, 1f)
    drawReel(leftCenter, minR + (maxR - minR) * (1f - p), reelAngleDeg, c, sw)
    drawReel(rightCenter, minR + (maxR - minR) * p, -reelAngleDeg, c, sw)

    // fila de ranuras bajo la ventana
    run {
        val sr = slotsZone
        val n = 8
        val gap = sr.width / (n * 2f - 1f)
        for (i in 0 until n) {
            drawRoundRect(color = c.ink.copy(alpha = 0.7f),
                topLeft = Offset(sr.left + i * gap * 2f, sr.top),
                size = Size(gap, sr.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(sr.height / 2f, sr.height / 2f))
        }
    }

    // teclado de 6 teclas grandes con patas y detalle de rejilla lateral
    drawKeys(c, keysZone, pressedKey, sw, portrait)

    // tapa de vidrio (encima de todo, no bloquea toques: se dibuja en otra capa)
    drawLid(c, window, lidOpenFraction, shineShift, sw)
}

private fun trapezoid(l: Float, t: Float, r: Float, t2: Float, r2: Float, b: Float, l2: Float, b2: Float): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        moveTo(l, t); lineTo(r, t2); lineTo(r2, b); lineTo(l2, b2); close()
    }

private fun DrawScope.drawReel(
    center: Offset, tapeRadius: Float, angleDeg: Float, c: SkinColors, sw: Float
) {
    // disco oscuro de cinta cuyo radio cambia con el progreso
    drawCircle(c.tapeDark, radius = tapeRadius, center = center)
    drawCircle(c.ink.copy(alpha = 0.6f), radius = tapeRadius, center = center, style = Stroke(width = sw * 0.5f))
    // cubo dentado (6 dientes) que gira
    val cubeR = tapeRadius * 0.55f
    val path = androidx.compose.ui.graphics.Path()
    val teeth = 6
    val step = (Math.PI * 2 / teeth).toFloat()
    val outer = cubeR
    val inner = cubeR * 0.72f
    for (i in 0 until teeth) {
        val a0 = Math.toRadians(angleDeg.toDouble()).toFloat() + i * step
        val a1 = a0 + step * 0.45f
        val a2 = a0 + step * 0.55f
        val a3 = a0 + step
        if (i == 0) path.moveTo(center.x + outer * cos(a0), center.y + outer * sin(a0))
        path.lineTo(center.x + outer * cos(a1), center.y + outer * sin(a1))
        path.lineTo(center.x + inner * cos(a2), center.y + inner * sin(a2))
        path.lineTo(center.x + inner * cos(a3 - step * 0.45f), center.y + inner * sin(a3 - step * 0.45f))
        path.lineTo(center.x + outer * cos(a3), center.y + outer * sin(a3))
    }
    path.close()
    drawPath(path, c.fill, style = Fill)
    drawPath(path, c.ink, style = Stroke(width = sw * 0.7f))
    drawCircle(c.ink, radius = cubeR * 0.18f, center = center)
}

private fun DrawScope.drawKeys(
    c: SkinColors, zone: androidx.compose.ui.geometry.Rect, pressedKey: Int?, sw: Float, portrait: Boolean
) {
    val cols = if (portrait) 3 else 2
    val rows = if (portrait) 2 else 3
    val gap = zone.width * 0.04f
    val keyW = (zone.width - gap * (cols - 1)) / cols
    val keyH = (zone.height - gap * (rows - 1)) / rows
    for (i in 0 until 6) {
        val col = i % cols
        val row = i / cols
        val sunk = pressedKey == i
        val x = zone.left + col * (keyW + gap)
        val y = zone.top + row * (keyH + gap) + if (sunk) keyH * 0.06f else 0f
        val hh = keyH * if (sunk) 0.94f else 1f
        // sombra/pata
        drawRoundRect(c.ink.copy(alpha = 0.35f),
            Offset(x + sw, zone.top + row * (keyH + gap) + keyH * 0.92f),
            Size(keyW - sw * 2, keyH * 0.10f),
            androidx.compose.ui.geometry.CornerRadius(sw * 2, sw * 2), Fill)
        // tecla
        drawRoundRect(if (sunk) c.body.copy(alpha = 0.6f) else c.fill,
            Offset(x, y), Size(keyW, hh),
            androidx.compose.ui.geometry.CornerRadius(keyW * 0.18f, keyW * 0.18f), Fill)
        drawRoundRect(c.ink, Offset(x, y), Size(keyW, hh),
            androidx.compose.ui.geometry.CornerRadius(keyW * 0.18f, keyW * 0.18f), Stroke(width = sw))
        // rejilla lateral de la tecla (líneas verticales a la derecha)
        val lines = 3
        for (l in 0 until lines) {
            val lx = x + keyW - (l + 1) * keyW * 0.08f - sw
            drawLine(c.ink.copy(alpha = 0.5f),
                Offset(lx, y + hh * 0.25f), Offset(lx, y + hh * 0.75f), strokeWidth = sw * 0.4f)
        }
    }
}

private fun DrawScope.drawLid(
    c: SkinColors, window: androidx.compose.ui.geometry.Rect, open: Float, shineShift: Float, sw: Float
) {
    // La tapa rota sobre la bisagra superior: se "levanta" reduciendo su altura proyectada
    val lidH = window.height * (1f - open * 0.92f)
    if (lidH <= 2f) return
    val rect = androidx.compose.ui.geometry.Rect(window.left, window.top, window.right, window.top + lidH)
    // vidrio translúcido
    drawRoundRect(brush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.10f), c.glass, Color.White.copy(alpha = 0.06f))
    ), topLeft = rect.topLeft, size = rect.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rect.width * 0.06f, rect.width * 0.06f))
    // reflejo desplazado por el acelerómetro
    val shineX = rect.left + rect.width * (0.25f + 0.5f * (0.5f + shineShift / 2f))
    drawLine(Color.White.copy(alpha = 0.35f),
        Offset(shineX, rect.top + rect.height * 0.1f),
        Offset(shineX - rect.width * 0.18f, rect.bottom - rect.height * 0.1f),
        strokeWidth = sw * 2f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.18f),
        Offset(shineX + sw * 4f, rect.top + rect.height * 0.1f),
        Offset(shineX - rect.width * 0.18f + sw * 4f, rect.bottom - rect.height * 0.1f),
        strokeWidth = sw, cap = StrokeCap.Round)
    // marco y bisagra superior
    drawRoundRect(color = c.ink, topLeft = rect.topLeft, size = rect.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rect.width * 0.06f, rect.width * 0.06f),
        style = Stroke(width = sw))
    drawLine(c.ink, Offset(rect.left - sw * 2, rect.top), Offset(rect.right + sw * 2, rect.top), strokeWidth = sw * 1.6f)
}

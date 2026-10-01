package com.mixcasete.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mixcasete.app.MainActivity
import com.mixcasete.app.player.PlayerViewModel
import com.mixcasete.app.ui.skin.SkinCanvas
import com.mixcasete.app.ui.skin.SkinLayout
import com.mixcasete.app.ui.skin.CalibrationOverlay
import com.mixcasete.app.ui.skin.ScaledCanvas
import com.mixcasete.app.ui.theme.ScreenGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/** Raíz de la app: decide orientación y compone el reproductor + overlays. */
@Composable
fun CassetteApp(onToggleImmersive: (Boolean) -> Unit) {
    val vm: PlayerViewModel = hiltViewModel()
    val ui by vm.ui.collectAsState()
    val configuration = LocalConfiguration.current
    val portrait = configuration.screenHeightDp > configuration.screenWidthDp

    // Sonido de clic mecánico al pulsar teclas/perillas
    val context = LocalContext.current
    val clicker = remember { MediaPlayer().apply {
        runCatching {
            setDataSource(context, Uri.parse("android.resource://${context.packageName}/raw/key_click"))
            prepare()
        }
    } }
    fun click() = runCatching { if (clicker.isPlaying) clicker.seekTo(0) else clicker.start() }

    YouTubeLoginBus.init(vm)
    TvShareBus.init(vm)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PlayerSkin(vm = vm, ui = ui, portrait = portrait, onClick = { click() })

        // Hoja inferior con la lista (vertical) / panel lateral (horizontal)
        PlaylistSheet(vm = ui.let { vm }, visible = ui.showPlaylist, onDismiss = vm::hidePlaylist, portrait = portrait)

        // Avisos / errores sobre la mini pantalla
        LaunchedEffect(ui.error, ui.notice) { delay(4500) }
        if (ui.debugLog.isNotEmpty()) {
            DebugLogPanel(vm = vm, log = ui.debugLog, modifier = Modifier.align(Alignment.TopEnd))
        }

        // Botón flotante discreto: lista + compartir TV + login
        TopControls(
            vm = vm,
            sharing = ui.sharingToTv,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(8.dp),
            onToggleImmersive = onToggleImmersive
        )
    }
}

// ---------------------------------------------------------------- Skin interactivo

@Composable
private fun PlayerSkin(
    vm: PlayerViewModel,
    ui: com.mixcasete.app.player.PlayerUiState,
    portrait: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressedKey by remember { mutableStateOf<Int?>(null) }

    // Rotación infinita de los carretes SOLO mientras hay estado real "playing"
    val reelAngle = remember { Animatable(0f) }
    LaunchedEffect(ui.isPlaying) {
        if (ui.isPlaying) {
            while (true) {
                reelAngle.snapTo((reelAngle.value + 6f) % 360f)
                delay(33)
            }
        }
    }

    // Tapa: animación de giro al abrir/cerrar
    val lidFrac = remember { Animatable(if (ui.lidOpen) 1f else 0f) }
    LaunchedEffect(ui.lidOpen) {
        lidFrac.animateTo(if (ui.lidOpen) 1f else 0f, tween(500, easing = LinearEasing))
        if (!ui.lidOpen && lidFrac.value < 0.05f) onClick() // clic al insertar el casete
    }

    // Reflejo del vidrio movido por acelerómetro
    val context = LocalContext.current
    var shine by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // x en -9.8..9.8 -> shine en -1..1, suavizado
                val target = (e.values[0] / 9.8f).coerceIn(-1f, 1f)
                shine = shine * 0.9f + target * 0.1f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }

    val progress = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f

    val baseH = if (portrait) SkinLayout.BASE_H_PORTRAIT else SkinLayout.BASE_H_LANDSCAPE

    ScaledCanvas(
        baseW = SkinLayout.BASE_W,
        baseH = baseH,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            // Capa de dibujo vectorial
            SkinCanvas(
                portrait = portrait,
                dark = MaterialTheme.colorScheme.background == Color(0xFF17191D),
                progress = progress,
                reelAngleDeg = reelAngle.value,
                lidOpenFraction = lidFrac.value,
                shineShift = shine,
                pressedKey = pressedKey,
                miniProgress = progress
            )

            // Zona táctil del teclado: 6 teclas grandes
            val keysZone = if (portrait) SkinLayout.keys else SkinLayout.lKeys
            val keyOrder = listOf("REW", "PLAY", "F.F", "STOP/EJECT", "PREV", "NEXT")
            TouchGrid(
                zone = keysZone,
                columns = if (portrait) 3 else 2,
                rows = if (portrait) 2 else 3,
                labels = keyOrder,
                onDown = { idx -> pressedKey = idx; onClick() },
                onUp = { idx ->
                    pressedKey = null
                    when (idx) {
                        0 -> vm.rewind()
                        1 -> vm.playPause()
                        2 -> vm.fastForward()
                        3 -> vm.stopEject()
                        4 -> vm.prev()
                        5 -> vm.next()
                    }
                }
            )

            // Perillas táctiles: volumen / tono / balance (deslizar vertical)
            val knobZones = if (portrait) SkinLayout.knobs else SkinLayout.lKnobs
            KnobTouch(zone = knobZones[0], onDelta = { d -> vm.setVolume((ui.volume + d).coerceIn(0f, 1f)) }, container = true)
            KnobTouch(zone = knobZones[1], onDelta = { d -> vm.setTone((ui.tone + (d * 60f).roundToInt()).coerceIn(-50, 50)) }, container = true)
            KnobTouch(zone = knobZones[2], onDelta = { d -> vm.setBalance((ui.balance + (d * 60f).roundToInt()).coerceIn(-50, 50)) }, container = true)

            // Toques en la franja: repetir/aleatorio/favoritos en la mini pantalla
            val screenZone = if (portrait) SkinLayout.miniScreen else SkinLayout.lMiniScreen
            MiniScreenTouch(
                zone = screenZone,
                onRepeat = { onClick(); vm.cycleRepeat() },
                onShuffle = { onClick(); vm.toggleShuffle() },
                onFavorite = { onClick(); vm.toggleFavorite() }
            )

            // EJECT: tocar la tapa/ventana expulsa e inserta
            val windowZone = if (portrait) SkinLayout.window else SkinLayout.lWindow
            ZoneTouch(zone = windowZone, onTap = { onClick(); vm.toggleLid() })

            // Texto de la etiqueta del casete (título/artista) encima del dibujo
            TapeLabel(
                tapeZone = if (portrait) SkinLayout.tape else SkinLayout.lTape,
                title = ui.currentTrack?.title ?: (ui.notice ?: "Mix.Casete"),
                artist = ui.currentTrack?.artist ?: (if (ui.hasTape) "" else "sin casete"),
                dark = MaterialTheme.colorScheme.background == Color(0xFF17191D)
            )

            // Pantalla de error/noticia parpadeando en la mini pantalla
            if (ui.error != null || ui.notice != null) {
                ZoneText(zone = if (portrait) SkinLayout.miniScreen else SkinLayout.lMiniScreen,
                    text = ui.error ?: ui.notice ?: "",
                    color = if (ui.error != null) Color(0xFFB3261E) else ScreenGreen)
            }

            if (ui.calibration) CalibrationOverlay(portrait = portrait)
        }
    }
}

// ---------------------------------------------------------------- Utilidades de zonas táctiles

private fun zoneRect(zone: SkinLayout.Zone, full: IntSize): androidx.compose.ui.geometry.Rect =
    androidx.compose.ui.geometry.Rect(
        zone.x * full.width, zone.y * full.height,
        (zone.x + zone.w) * full.width, (zone.y + zone.h) * full.height
    )

@Composable
private fun ZoneTouch(zone: SkinLayout.Zone, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(zone) {
                detectTapGestures { pos ->
                    if (zoneRect(zone, size).contains(pos)) onTap()
                }
            }
    )
}

@Composable
private fun TouchGrid(
    zone: SkinLayout.Zone,
    columns: Int,
    rows: Int,
    labels: List<String>,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(zone, columns, rows) {
                detectTapGestures(
                    onPress = { pos ->
                        val r = zoneRect(zone, size)
                        if (!r.contains(pos)) return@detectTapGestures
                        val col = (((pos.x - r.left) / r.width) * columns).toInt().coerceIn(0, columns - 1)
                        val row = (((pos.y - r.top) / r.height) * rows).toInt().coerceIn(0, rows - 1)
                        val idx = row * columns + col
                        if (idx in 0 until 6) {
                            onDown(idx)
                            awaitRelease()
                            onUp(idx)
                        }
                    }
                )
            }
    )
}

@Composable
private fun KnobTouch(zone: SkinLayout.Zone, onDelta: (Float) -> Unit, container: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(zone) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        val r = zoneRect(zone, size)
                        if (r.contains(change.position)) {
                            // desplazamiento vertical -> delta normalizado
                            onDelta(-drag.y / (size.height * 0.6f))
                        }
                        change.consume()
                    }
                )
            }
    )
}

@Composable
private fun MiniScreenTouch(zone: SkinLayout.Zone, onRepeat: () -> Unit, onShuffle: () -> Unit, onFavorite: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(zone) {
                detectTapGestures { pos ->
                    val r = zoneRect(zone, size)
                    if (!r.contains(pos)) return@detectTapGestures
                    val third = r.width / 3f
                    when {
                        pos.x < r.left + third -> onRepeat()
                        pos.x < r.left + third * 2 -> onShuffle()
                        else -> onFavorite()
                    }
                }
            }
    )
}

/** Etiqueta del casete: título y artista, posicionados en fracciones dentro del lienzo escalado. */
@Composable
private fun TapeLabel(tapeZone: SkinLayout.Zone, title: String, artist: String, dark: Boolean) {
    val lx = SkinLayout.labelBandTapeFrac.first
    val ly = SkinLayout.labelBandTapeFrac.second
    val lw = SkinLayout.labelBandTapeFrac.third.first
    val lh = SkinLayout.labelBandTapeFrac.third.second
    val textColor = if (dark) Color(0xFFEDE6D6) else Color(0xFF14120F)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val r = zoneRect(tapeZone, IntSize(wPx.toInt(), hPx.toInt()))
        val labelLeft = r.left + lx * r.width
        val labelTop = r.top + ly * r.height
        val labelW = lw * r.width
        val labelH = lh * r.height
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(
                    x = with(density) { labelLeft.toDp() },
                    y = with(density) { labelTop.toDp() }
                )
                .width(with(density) { labelW.toDp() })
                .height(with(density) { labelH.toDp() })
        ) {
            Text(
                text = title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = min(with(density) { labelW.toDp() }, with(density) { labelH.toDp() }) * 0.22f,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                color = textColor.copy(alpha = 0.75f),
                fontSize = min(with(density) { labelW.toDp() }, with(density) { labelH.toDp() }) * 0.16f,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Texto de aviso/error dentro de una zona (mini pantalla). */
@Composable
private fun ZoneText(zone: SkinLayout.Zone, text: String, color: Color) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val r = zoneRect(
            zone,
            IntSize(constraints.maxWidth, constraints.maxHeight)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(with(density) { r.left.toDp() }, with(density) { r.top.toDp() })
                .size(with(density) { r.width.toDp() }, with(density) { (r.height * 0.9f).toDp() })
        ) {
            Text(
                text = text, color = color, fontSize = 9.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 3.dp)
            )
        }
    }
}

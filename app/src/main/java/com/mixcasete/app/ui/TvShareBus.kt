package com.mixcasete.app.ui

import android.content.Context
import android.content.Intent
import com.mixcasete.app.MainActivity
import com.mixcasete.app.player.PlayerViewModel

/**
 * "Bus" mínimo para compartir el video en una pantalla externa (Presentation API).
 * La UI llama a [toggle]; si hay un display externo disponible se lanza [TvPresentationActivity]
 * que muestra SOLO el video a pantalla completa (horizontal, letterbox negro, sin controles).
 * El teléfono queda como control remoto: el ViewModel sigue pilotando audio + posición.
 */
object TvShareBus {

    private var vmRef: PlayerViewModel? = null

    /** Estado pendiente desde la pantalla externa (bridge JS -> ViewModel). */
    data class PendingState(
        val playing: Boolean, val positionMs: Long, val durationMs: Long, val error: String?
    )
    private val pendingStates = java.util.concurrent.ConcurrentLinkedQueue<PendingState>()
    @Volatile private var stopped = false

    fun init(vm: PlayerViewModel) {
        vmRef = vm
        // Drenar estados que llegaron antes de que el VM estuviera disponible
        while (true) {
            val p = pendingStates.poll() ?: break
            vm.onYouTubeState(p.playing, p.positionMs, p.durationMs, p.error)
        }
    }

    /** Llamado por TvPresentationActivity (no puede inyectar un @HiltViewModel directamente). */
    fun reportState(playing: Boolean, positionMs: Long, durationMs: Long, error: String?) {
        val vm = vmRef
        if (vm != null) {
            vm.onYouTubeState(playing, positionMs, durationMs, error)
        } else {
            pendingStates.add(PendingState(playing, positionMs, durationMs, error))
        }
    }

    /** Llamado por TvPresentationActivity al destruirse. */
    fun activityStopped() {
        stopped = true
        val vm = vmRef
        if (vm != null) {
            vm.setSharing(false)
        }
    }

    /** Instantánea de estado que el teléfono publica para la pantalla externa. */
    data class ShareSnapshot(val playing: Boolean, val youtubeId: String?)

    @Volatile private var snapshot: ShareSnapshot? = null

    /** Llamado periódicamente desde la UI (LaunchedEffect) para mantener sincronizada la TV. */
    fun publishSnapshot(playing: Boolean, youtubeId: String?) {
        snapshot = ShareSnapshot(playing, youtubeId)
    }

    fun snapshotOrNull(): ShareSnapshot? = snapshot

    /** Devuelve true una vez (para que la Activity termine si el sharing se apagó). */
    fun consumeStop(): Boolean {
        val v = stopped
        stopped = false
        return v
    }

    fun toggle(context: Context) {
        val vm = vmRef ?: return
        val displays = DisplayHelper.externalDisplays(context)
        if (displays.isEmpty()) {
            // Sin pantalla externa: avisar por la mini pantalla mediante notice del VM.
            vm.setSharing(false)
            vm.notifyNoExternalDisplay()
            return
        }
        val active = vm.ui.value.sharingToTv
        if (active) {
            vm.setSharing(false)
            MainActivity.sharingActive = false
            runCatching { context.startActivity(DisplayHelper.buildStopIntent(context)) }
        } else {
            vm.setSharing(true)
            MainActivity.sharingActive = true
            val i = Intent(context, TvPresentationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(i)
        }
    }
}

/** Utilidades de Displays (Presentation API) separadas para testear/fallback. */
object DisplayHelper {
    fun externalDisplays(context: Context): List<android.view.Display> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            ?: return emptyList()
        return dm.displays.filter { it.displayId != android.view.Display.DEFAULT_DISPLAY }
    }

    fun buildStopIntent(context: Context): Intent =
        Intent(context, TvPresentationActivity::class.java).apply {
            action = ACTION_STOP
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    const val ACTION_STOP = "com.mixcasete.app.action.STOP_PRESENTATION"
}


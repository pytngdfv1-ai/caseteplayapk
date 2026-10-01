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

    fun init(vm: PlayerViewModel) {
        vmRef = vm
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


package com.mixcasete.app.ui

import android.content.Context
import com.mixcasete.app.player.PlayerViewModel

/**
 * Bus del login opcional de Google/YouTube. Abre una WebView propia ([YouTubeLoginActivity])
 * con cookies persistentes (NO se hace OAuth dentro de la WebView; solo inicio de sesión
 * clásico de YouTube para que el IFrame player pueda usar sesiones privadas / Music).
 */
object YouTubeLoginBus {

    private var vmRef: PlayerViewModel? = null

    fun init(vm: PlayerViewModel) {
        vmRef = vm
    }

    fun open(context: Context) {
        context.startActivity(
            android.content.Intent(context, YouTubeLoginActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Llamado desde la actividad de login cuando la sesión cambia. */
    fun reportLoggedIn(logged: Boolean) {
        vmRef?.setLoggedIn(logged)
    }

    fun viewModel(): PlayerViewModel? = vmRef
}

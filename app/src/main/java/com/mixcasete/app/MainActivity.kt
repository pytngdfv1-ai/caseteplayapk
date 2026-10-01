package com.mixcasete.app

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mixcasete.app.ui.CassetteApp
import com.mixcasete.app.ui.theme.MixCaseteTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Actividad única. configChanges en el manifest evita reiniciar la reproducción al girar;
 * el estado vive en el ViewModel (compartido vía Hilt) y el servicio en segundo plano.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        insetsController = WindowCompat.getInsetsController(window, window.decorView)

        setContent {
            MixCaseteTheme {
                CassetteApp(
                    onToggleImmersive = { immersive ->
                        if (immersive) {
                            insetsController.hide(
                                WindowInsetsCompat.Type.systemBars() or
                                    WindowInsetsCompat.Type.displayCutout()
                            )
                            insetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            insetsController.show(
                                WindowInsetsCompat.Type.systemBars() or
                                    WindowInsetsCompat.Type.displayCutout()
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Mantener la pantalla encendida mientras se comparte con la TV
        if (sharingActive) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        /** Lo fija la UI de "Compartir con TV"; se consulta aquí para KEEP_SCREEN_ON. */
        @Volatile
        var sharingActive: Boolean = false
    }
}

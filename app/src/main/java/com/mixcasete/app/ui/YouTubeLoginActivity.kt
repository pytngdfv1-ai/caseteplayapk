package com.mixcasete.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mixcasete.app.ui.theme.MixCaseteTheme

/**
 * Login OPCIONAL de Google/YouTube en WebView propia con cookies persistentes.
 * No se usa OAuth dentro de la WebView: el usuario inicia sesión normalmente en
 * youtube.com y las cookies quedan guardadas para el IFrame player.
 */
class YouTubeLoginActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var loggedIn by mutableStateOf(false)

        setContent {
            MixCaseteTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            text = if (loggedIn) "Sesión guardada. Ya puedes cerrarla." else
                                "Inicia sesión en YouTube (opcional). Las cookies se guardan solo en esta app.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
                                    webViewClient = object : WebViewClient() {
                                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                            val c = cm.getCookie("https://www.youtube.com") ?: ""
                                            val now = c.contains("SID=") || c.contains("HSID=") || c.contains("SSID=")
                                            if (now != loggedIn) {
                                                loggedIn = now
                                                YouTubeLoginBus.reportLoggedIn(now)
                                            }
                                        }
                                    }
                                    loadUrl("https://www.youtube.com/")
                                }
                            }
                        )
                        Button(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        ) { Text("LISTO") }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Asegurar flush de cookies al salir
        runCatching { CookieManager.getInstance().flush() }
        super.onDestroy()
    }
}

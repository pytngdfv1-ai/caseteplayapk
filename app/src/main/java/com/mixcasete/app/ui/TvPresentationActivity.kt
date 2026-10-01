package com.mixcasete.app.ui

import android.annotation.SuppressLint
import android.app.Presentation
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.mixcasete.app.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** HTML del IFrame oficial de YouTube (librería youtube-player IFrame API vía WebView). */
private const val YT_HTML = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://www.youtube.com/iframe_api"></script>
<style>html,body{margin:0;padding:0;background:#000;height:100%}#player{position:absolute;inset:0}</style>
</head><body><div id="player"></div>
<script>
var player=null, ready=false, pending=null;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('player',{
    videoId:'',
    playerVars:{autoplay:0,playsinline:1,controls:0,rel:0,modestbranding:1},
    events:{onReady:function(){ready=true; if(pending)cmd(pending);},
            onStateChange:function(e){
              var p=(e.data===YT.PlayerState.PLAYING);
              AndroidBridge.onState(p, player.getCurrentTime()*1000, player.getDuration()*1000, '');
            },
            onError:function(e){AndroidBridge.onState(false,0,0,'IFrame error '+e.data);}
    }
  });
}
function cmd(o){
  if(!ready){pending=o;return;}
  if(o.v!=null && o.v!==undefined && o.v!==''){ player.cueVideoById(o.v); }
  if(o.seekMs!=null){ player.seekTo(o.seekMs/1000,true); }
  if(o.play){ player.playVideo(); } else if(o.stop){ player.stopVideo(); }
}
window.AndroidBridge={ onState:function(p,pos,dur,err){
  if(window.__send) window.__send(JSON.stringify({playing:p,pos:pos,dur:dur,err:err}));
}};
</script></body></html>
"""

/**
 * Reproductor de YouTube por IFrame API oficial dentro de un WebView.
 * - Modo "oculto" (audio): el WebView se muestra con tamaño mínimo (no visible al usuario)
 *   pero sigue reproduciendo audio.
 * - Modo "visible": se muestra a pantalla completa para video.
 * El estado (playing/position/duration/error) se reporta al PlayerViewModel vía JS bridge.
 */
class YouTubePlayerWebView internal constructor()

@SuppressLint("SetJavaScriptEnabled")
@AndroidEntryPoint
class TvPresentationActivity : ComponentActivity() {

    @Inject lateinit var vm: PlayerViewModel

    private var presentation: Presentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == DisplayHelper.ACTION_STOP) { finish(); return }

        val display: Display? = DisplayHelper.externalDisplays(this).firstOrNull()
        if (display == null) {
            // Sin display externo: cerrar (el teléfono sigue sonando).
            finish(); return
        }
        // Pantalla encendida durante la transmisión
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val web = WebView(display.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun post(msg: String) { parseState(msg) }
            }, "__jsbridge")
            loadDataWithBaseURL("https://www.youtube.com/", YT_HTML, "text/html", "utf-8", null)
        }
        // Instalar el callback __send desde el load (no se puede en el HTML sin interface)
        web.post {
            web.evaluateJavascript(
                "window.__send=function(m){__jsbridge.post(m);};", null
            )
        }

        presentation = ObjPresentation(display, this, web).also { it.show() }

        // Sincronizar play/pause + seek desde el teléfono (VM es la fuente de verdad)
        lifecycleScopeObserve(web)
    }

    private fun parseState(json: String) {
        try {
            val o = org.json.JSONObject(json)
            vm.onYouTubeState(
                playing = o.optBoolean("playing"),
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                error = o.optString("err").ifBlank { null }
            )
        } catch (_: Exception) { /* ignorar estados malformados */ }
    }

    private fun lifecycleScopeObserve(web: WebView) {
        lifecycleScope.launch {
            vm.ui.collect { st ->
                val v = st.currentTrack?.youtubeId ?: ""
                val js = "cmd({v:'$v',play:${st.isPlaying},seekMs:null})"
                web.evaluateJavascript(js, null)
            }
        }
    }

    override fun onDestroy() {
        runCatching { presentation?.dismiss() }
        presentation = null
        MainActivitySharingOff()
        super.onDestroy()
    }

    private fun MainActivitySharingOff() {
        com.mixcasete.app.MainActivity.sharingActive = false
        vm.setSharing(false)
    }
}

/** Wrapper para no exponer el constructor protegido de Presentation directamente. */
private class ObjPresentation(
    display: Display,
    context: android.content.Context,
    private val content: android.view.View
) : Presentation(context, display) {
    init {
        setContentView(
            content,
            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        // Sin barra de sistema en la TV: solo video, letterbox negro alrededor
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }
}

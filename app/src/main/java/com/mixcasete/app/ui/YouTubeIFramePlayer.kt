package com.mixcasete.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mixcasete.app.player.PlayerViewModel

/**
 * WebView con el IFrame API OFICIAL de YouTube.
 * - [visible] = false: tamaño 1x1 dp (reproductor "oculto" para SOLO audio).
 * - [visible] = true: pantalla completa para ver el video.
 * Reporta estado real (playing/position/duration/error) al PlayerViewModel; los carretes
 * solo giran cuando este WebView reporta "playing".
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeIFramePlayer(
    vm: PlayerViewModel = hiltViewModel(),
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    // Los enlaces externos (p. ej. "Ver en YouTube") se abren fuera de la app
                    val url = request?.url ?: return true
                    if (url.host?.contains("youtube.com") != true &&
                        url.host?.contains("ytimg.com") != true &&
                        url.scheme in setOf("http", "https")) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                            )
                        }
                        return true
                    }
                    return false
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun post(msg: String) {
                    try {
                        val o = org.json.JSONObject(msg)
                        vm.onYouTubeState(
                            playing = o.optBoolean("playing"),
                            positionMs = o.optLong("pos"),
                            durationMs = o.optLong("dur"),
                            error = o.optString("err").ifBlank { null }
                        )
                    } catch (_: Exception) { }
                }
            }, "mcBridge")
            loadDataWithBaseURL(
                "https://www.youtube.com/", YT_IFRAME_HTML, "text/html", "utf-8", null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    // Sincronizar comando con el estado del VM (fuente de verdad: teclas / lista)
    LaunchedEffect(ui.currentTrack?.youtubeId, ui.isPlaying, ui.seekCommandTick) {
        val id = ui.currentTrack?.youtubeId
        val js = buildString {
            append("cmd({v:")
            append(if (id == null) "null" else "'$id'")
            append(",play:")
            append(if (ui.isPlaying) "true" else "false")
            append(",seek:")
            append(if (ui.seekCommandTick >= 0 && ui.pendingSeekMs > 0) ui.pendingSeekMs else "null")
            append("})")
        }
        webView.evaluateJavascript(js, null)
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .then(
                if (visible) Modifier.fillMaxSize()
                // Oculto: 1dp x 1dp, sigue reproduciendo audio (no usar visibility=GONE)
                else Modifier
            )
    )
}

private const val YT_IFRAME_HTML = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://www.youtube.com/iframe_api"></script>
<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}#player{position:absolute;top:0;left:0;width:100%;height:100%}</style>
</head><body><div id="player"></div>
<script>
var player=null, ready=false, pending=null;
function send(m){ try{ mcBridge.post(JSON.stringify(m)); }catch(e){} }
function onYouTubeIframeAPIReady(){
  player=new YT.Player('player',{
    videoId:'',
    playerVars:{autoplay:0,playsinline:1,controls:1,rel:0,modestbranding:1},
    events:{
      onReady:function(){ ready=true; if(pending){cmd(pending);pending=null;} send({playing:false,pos:0,dur:0,err:''}); },
      onStateChange:function(e){
        var p=(e.data===YT.PlayerState.PLAYING);
        send({playing:p,pos:Math.round(player.getCurrentTime()*1000),dur:Math.round(player.getDuration()*1000),err:''});
        if(e.data===YT.PlayerState.ENDED) send({playing:false,pos:0,dur:0,err:'ended'});
      },
      onError:function(e){ send({playing:false,pos:0,dur:0,err:'IFrame error '+e.data}); }
    }
  });
}
function cmd(o){
  if(!ready){ pending=o; return; }
  if(o.v!==undefined && o.v!==null && o.v!==''){ player.cueVideoById(o.v); }
  if(typeof o.seek==='number'){ player.seekTo(o.seek/1000,true); }
  if(o.play===true){ player.playVideo(); } else if(o.play===false){ player.pauseVideo(); }
}
setInterval(function(){ if(player&&ready&&player.getPlayerState()===1){ send({playing:true,pos:Math.round(player.getCurrentTime()*1000),dur:Math.round(player.getDuration()*1000),err:''}); } }, 1000);
</script></body></html>
"""

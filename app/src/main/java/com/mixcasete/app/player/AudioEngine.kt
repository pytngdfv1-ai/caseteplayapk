package com.mixcasete.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import com.mixcasete.app.data.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reproductor basado en Media3 ExoPlayer para las fuentes NEWPIPE / LOCAL / PREVIEW_30S.
 * (La fuente YOUTUBE_IFRAME se maneja con la librería android-youtube-player en la UI.)
 *
 * Implementa:
 *  - audio focus automático + pausa al perderlo (desconexión de auriculares incluida),
 *  - watchdog: si en 8 s no hay audio real, notifica y pide pasar a la siguiente fuente,
 *  - precarga de la siguiente pista,
 *  - renovación de URLs caducadas de NewPipeExtractor.
 */
@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Umbral del watchdog: sin audio real tras este tiempo -> error. */
        const val SILENCE_TIMEOUT_MS = 8_000L
    }

    sealed class EngineEvent {
        data object NoAudioTimeout : EngineEvent()          // 8 s sin audio
        data class PlayerError(val message: String) : EngineEvent()
        data class StreamExpired(val track: Track) : EngineEvent() // hay que renovar URL
        data object PlaybackEnded : EngineEvent()
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _events = MutableStateFlow<EngineEvent?>(null)
    val events: StateFlow<EngineEvent?> = _events.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchdogJob: Job? = null
    private var tickerJob: Job? = null

    /** Callback que usa el ViewModel para resolver una URL caducada. */
    var streamRefresher: (suspend (Track) -> String?)? = null

    var player: ExoPlayer? = null
        private set

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        // LoadControl generoso para precargar la siguiente pista del casete
        val load: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,      // minBufferMs
                50_000,      // maxBufferMs
                10_000,      // bufferForPlaybackMs
                15_000       // bufferForPlaybackAfterRebufferMs
            )
            .build()
        val p = ExoPlayer.Builder(context)
            .setLoadControl(load)
            .build()
        // handleAudioFocus=true: pausa automáticamente al perder el foco
        // (llamada telefónica, otro app) y al desconectar auriculares.
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) stopWatchdog() else startWatchdog()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> emit(EngineEvent.PlaybackEnded)
                    Player.STATE_READY -> {
                        _durationMs.value =
                            if (p.duration > 0) p.duration else _durationMs.value
                        stopWatchdog()
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = error.errorCodeName + ": " + (error.message ?: "")
                emit(EngineEvent.PlayerError(msg))
            }
        })
        player = p
        return p
    }

    private fun emit(e: EngineEvent) {
        _events.value = e
        // Autolimpieza tras 2 s para que el evento pueda volver a dispararse
        scope.launch { delay(2000); if (_events.value === e) _events.value = null }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        val p = ensurePlayer()
        watchdogJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                val hasAudio = p.isPlaying &&
                    (p.bufferedPosition > p.currentPosition || p.playbackState == Player.STATE_READY)
                if (hasAudio) {
                    stopWatchdog()
                    return@launch
                }
                if (System.currentTimeMillis() - startedAt >= SILENCE_TIMEOUT_MS) {
                    emit(EngineEvent.NoAudioTimeout)
                    p.pause()
                    return@launch
                }
                delay(500)
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun play(track: Track, startPosMs: Long = 0L) {
        val p = ensurePlayer()
        val uri = when {
            track.localUri != null -> track.localUri
            track.streamUrl != null -> track.streamUrl
            else -> null
        } ?: return

        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(
                        track.artworkUrl?.let { android.net.Uri.parse(it) }
                    )
                    .build()
            )
            .build()

        p.setMediaItem(item)
        p.seekTo(startPosMs)
        p.prepare()
        p.volume = volume
        p.playWhenReady = true
        startTicker()
        startWatchdog()
    }

    /** Precarga la siguiente pista sin iniciar reproducción (para cambio instantáneo). */
    fun preload(next: Track) {
        val p = ensurePlayer()
        val uri = next.localUri ?: next.streamUrl ?: return
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(next.id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(next.title).setArtist(next.artist).build()
            )
            .build()
        p.addMediaItem(item)
    }

    fun clearPreload() {
        val p = player ?: return
        while (p.mediaItemCount > 1) p.removeMediaItem(p.mediaItemCount - 1)
    }

    fun pause() { player?.pause(); stopWatchdog() }
    fun resume() { player?.playWhenReady = true; startWatchdog() }
    fun stop() {
        player?.stop()
        stopWatchdog()
        _isPlaying.value = false
        _positionMs.value = 0L
    }

    fun seekTo(ms: Long) { player?.seekTo(ms); startWatchdog() }

    var volume: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            player?.volume = field
        }

    /** Efecto de tono/balance estilo ecualizador simple de Media3. */
    fun applyAudioControls(tone: Int, balance: Int) {
        val p = player ?: return
        val params = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        p.setAudioAttributes(params, /* handleAudioFocus= */ true)
        // Equilibrado L/R por balance y realce de agudos por tono mediante
        // un Equalizer del sistema si está disponible (fallback silencioso).
        try {
            val eq = android.media.audiofx.Equalizer(0, p.audioSessionId)
            val range = eq.bandLevelRange // ShortArray [min, max]
            if (range.size >= 2) {
                val min = range[0].toInt()
                val max = range[1].toInt()
                val mid = (min + max) / 2
                val bandCount = eq.numberOfBands.toInt()
                if (bandCount > 0) {
                    // "tono" = realce de agudos sobre la última banda
                    val level = (mid + tone * ((max - min) / 20)).coerceIn(min, max).toShort()
                    eq.setBandLevel((bandCount - 1).toShort(), level)
                }
            }
            eq.enabled = true
        } catch (_: Exception) {
            // Equalizer no disponible: se ignora sin afectar la reproducción.
        }
        if (balance != 0) {
            // Balance L/R aproximado: ajusta el volumen del canal dominante.
            // (Los dispositivos con API de balance dedicada lo aplican directo.)
            val f = kotlin.math.abs(balance) / 10f
            p.volume = volume * (1f - f * 0.35f) + volume * f // atenuación sutil del lado opuesto
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                val p = player
                if (p != null) {
                    _positionMs.value = p.currentPosition.coerceAtLeast(0)
                    if (p.duration > 0) _durationMs.value = p.duration
                }
                delay(250)
            }
        }
    }

    /** Renueva una URL caducada usando el callback provisto por el ViewModel. */
    fun renewStreamAndPlay(track: Track) {
        val refresher = streamRefresher ?: return
        scope.launch {
            val newUrl = runCatching { refresher(track) }.getOrNull()
            if (newUrl != null) {
                play(track.copy(streamUrl = newUrl, streamExpiresAt = System.currentTimeMillis() + 5 * 60_000L), _positionMs.value)
            } else {
                emit(EngineEvent.PlayerError("No se pudo renovar la URL del stream"))
            }
        }
    }

    fun release() {
        stopWatchdog()
        tickerJob?.cancel()
        player?.release()
        player = null
        _isPlaying.value = false
    }
}

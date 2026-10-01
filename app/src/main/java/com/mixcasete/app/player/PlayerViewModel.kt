package com.mixcasete.app.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixcasete.app.data.RepeatMode
import com.mixcasete.app.data.Repository
import com.mixcasete.app.data.SearchResultItem
import com.mixcasete.app.data.SourceKind
import com.mixcasete.app.data.Track
import com.mixcasete.app.util.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado observable del reproductor para la UI (carretes, tapa, pantalla, teclas). */
data class PlayerUiState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val source: SourceKind? = null,
    val volume: Float = 0.8f,
    val tone: Int = 0,
    val balance: Int = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val lidOpen: Boolean = true,          // tapa abierta al inicio (sin casete insertado)
    val error: String? = null,
    val notice: String? = null,           // avisos no errores (p. ej. preview de 30 s)
    val sharingToTv: Boolean = false,
    val calibration: Boolean = false,
    val ytLoggedIn: Boolean = false,
    val showPlaylist: Boolean = false,
    val seekCommandTick: Long = 0L,
    val pendingSeekMs: Long = 0L,
    val debugLog: List<String> = emptyList()
) {
    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)
    val hasTape: Boolean get() = currentIndex >= 0 && currentTrack != null
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: Repository,
    private val engine: AudioEngine,
    private val settingsStore: SettingsStore
) : ViewModel() {

    companion object {
        /** Orden de fuentes por pista: YouTube IFrame -> NewPipe -> Local -> Preview 30 s. */
        fun sourcesFor(track: Track): List<SourceKind> = buildList {
            if (track.youtubeId != null) {
                add(SourceKind.YOUTUBE_IFRAME)
                add(SourceKind.NEWPIPE)
            }
            if (track.localUri != null) add(SourceKind.LOCAL)
            if (track.streamUrl != null) add(if (track.isPreview) SourceKind.PREVIEW_30S else SourceKind.NEWPIPE)
            if (track.youtubeId != null || track.localUri != null) add(SourceKind.PREVIEW_30S)
        }
    }

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    /** El WebView de YouTube expuesto en la UI; el ViewModel lo pilota vía este callback. */
    var ytCommand: YtCommand? = null

    /** Comando tipado para controlar el IFrame de YouTube desde la UI. */
    fun interface YtCommand {
        /**
         * @param youtubeId video a cargar (null = detener).
         * @param play true para reproducir, false para pausar.
         * @param seekMs posición inicial/objetivo en ms, o null para continuar.
         */
        fun send(youtubeId: String?, play: Boolean, seekMs: Long?)
    }

    /** Estado "playing" reportado por el IFrame de YouTube (para los carretes). */
    private val _ytPlaying = MutableStateFlow(false)

    private var pendingSources: List<SourceKind> = emptyList()
    private var watchdogJob: kotlinx.coroutines.Job? = null

    init {
        // Lista persistente (Room)
        repo.observeTracks()
            .catch { emit(emptyList()) }
            .onEach { list ->
                val keepIndex = _ui.value.currentIndex.coerceIn(-1, (list.size - 1).coerceAtLeast(-1))
                _ui.value = _ui.value.copy(tracks = list, currentIndex = if (list.isEmpty()) -1 else keepIndex)
            }
            .launchIn(viewModelScope)

        // Preferencias (DataStore)
        settingsStore.settings
            .onEach { s ->
                _ui.value = _ui.value.copy(
                    volume = s.volume, tone = s.tone, balance = s.balance,
                    shuffle = s.shuffle, calibration = s.calibration,
                    ytLoggedIn = s.ytLoggedIn,
                    repeat = RepeatMode.entries[s.repeatModeIndex]
                )
                engine.volume = s.volume
                engine.applyAudioControls(s.tone, s.balance)
            }
            .launchIn(viewModelScope)

        // Estado real de ExoPlayer -> UI + watchdog unificado
        engine.isPlaying
            .onEach { playing ->
                val effective = playing || _ytPlaying.value
                _ui.value = _ui.value.copy(isPlaying = effective)
                if (effective) stopNoAudioWatchdog() else startNoAudioWatchdog()
            }
            .launchIn(viewModelScope)

        engine.positionMs
            .onEach { ms -> _ui.value = _ui.value.copy(positionMs = ms) }
            .launchIn(viewModelScope)

        engine.durationMs
            .onEach { ms -> if (ms > 0) _ui.value = _ui.value.copy(durationMs = ms) }
            .launchIn(viewModelScope)

        engine.events
            .onEach { ev ->
                when (ev) {
                    is AudioEngine.EngineEvent.NoAudioTimeout -> onNoAudio()
                    is AudioEngine.EngineEvent.PlayerError -> {
                        log("ExoPlayer error: ${ev.message}")
                        advanceSourceOrNext(reason = ev.message)
                    }
                    is AudioEngine.EngineEvent.StreamExpired -> engine.renewStreamAndPlay(ev.track)
                    AudioEngine.EngineEvent.PlaybackEnded -> onTrackEnded()
                    null -> Unit
                }
            }
            .launchIn(viewModelScope)

        // Callback de renovación de URL caducada (re-extrae una URL fresca con reintentos)
        engine.streamRefresher = { track ->
            val key = track.youtubeId ?: track.streamUrl
            if (key == null) null
            else runCatching { repo.resolveStream(key).url }.getOrNull()
        }
    }

    // ---------------------------------------------------------------- Comandos de teclas

    fun playPause() {
        val st = _ui.value
        if (!st.hasTape) {
            if (st.tracks.isNotEmpty()) playAt(0) else _ui.value = st.copy(notice = "Casete vacío: agrega pistas")
            return
        }
        if (st.isPlaying) pause() else resume()
    }

    fun playAt(index: Int) {
        val track = _ui.value.tracks.getOrNull(index) ?: return
        viewModelScope.launch {
            // Cambiar de casete abre la tapa y luego se cierra con animación
            _ui.value = _ui.value.copy(lidOpen = true)
            delay(450)
            _ui.value = _ui.value.copy(
                currentIndex = index,
                lidOpen = false,
                positionMs = 0,
                durationMs = track.durationMs,
                error = null,
                notice = if (track.isPreview) "Sonando SOLO un preview de 30 s (último recurso)" else null
            )
            pendingSources = sourcesFor(track)
            tryNextSource(startFromFront = true)
            startNoAudioWatchdog()
        }
    }

    fun pause() {
        engine.pause()
        ytCommand?.send(_ui.value.currentTrack?.youtubeId, false, null)
        _ui.value = _ui.value.copy(isPlaying = false)
    }

    fun resume() {
        val st = _ui.value
        when (st.source) {
            SourceKind.YOUTUBE_IFRAME -> ytCommand?.send(st.currentTrack?.youtubeId, true, null)
            else -> engine.resume()
        }
        _ui.value = st.copy(isPlaying = true)
    }

    fun stopEject() {
        engine.stop()
        ytCommand?.send(null, false, null)
        _ui.value = _ui.value.copy(
            currentIndex = -1, isPlaying = false, lidOpen = true,
            positionMs = 0, durationMs = 0, source = null, error = null
        )
        stopNoAudioWatchdog()
    }

    fun next() {
        val st = _ui.value
        if (st.tracks.isEmpty()) return
        val idx = if (st.shuffle) st.tracks.indices.random() else (st.currentIndex + 1) % st.tracks.size
        playAt(idx)
    }

    fun prev() {
        val st = _ui.value
        if (st.tracks.isEmpty()) return
        val idx = if (st.currentIndex <= 0) st.tracks.size - 1 else st.currentIndex - 1
        playAt(idx)
    }

    /** REW: retrocede 10 s (como rebobinar el casete). */
    fun rewind() { seekRelative(-10_000) }

    /** F.F: avanza 10 s. */
    fun fastForward() { seekRelative(+10_000) }

    fun seekRelative(deltaMs: Long) {
        val st = _ui.value
        val target = (st.positionMs + deltaMs).coerceIn(0, st.durationMs.coerceAtLeast(1))
        seekTo(target)
    }

    fun seekTo(ms: Long) {
        _ui.value = _ui.value.copy(
            pendingSeekMs = ms,
            seekCommandTick = _ui.value.seekCommandTick + 1
        )
        when (_ui.value.source) {
            SourceKind.YOUTUBE_IFRAME -> ytCommand?.send(_ui.value.currentTrack?.youtubeId, true, ms)
            else -> engine.seekTo(ms)
        }
        _ui.value = _ui.value.copy(positionMs = ms)
    }

    fun toggleFavorite() {
        val t = _ui.value.currentTrack ?: return
        viewModelScope.launch { repo.setFavorite(t.id, !t.favorite) }
    }

    fun cycleRepeat() {
        val next = (_ui.value.repeat.ordinal + 1) % RepeatMode.entries.size
        viewModelScope.launch { settingsStore.setRepeat(next) }
    }

    fun toggleShuffle() {
        viewModelScope.launch { settingsStore.setShuffle(!_ui.value.shuffle) }
    }

    fun setVolume(v: Float) {
        viewModelScope.launch { settingsStore.setVolume(v) }
    }

    fun setTone(t: Int) { viewModelScope.launch { settingsStore.setTone(t) } }
    fun setBalance(b: Int) { viewModelScope.launch { settingsStore.setBalance(b) } }
    fun toggleCalibration() { viewModelScope.launch { settingsStore.setCalibration(!_ui.value.calibration) } }

    fun toggleLid() {
        _ui.value = _ui.value.copy(lidOpen = !_ui.value.lidOpen)
    }

    // ---------------------------------------------------------------- Búsqueda / agregar

    data class SearchUiState(
        val loading: Boolean = false,
        val results: List<SearchResultItem> = emptyList(),
        val error: String? = null
    )

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    fun doSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _search.value = SearchUiState(loading = true)
            try {
                val res = repo.search(query)
                _search.value = SearchUiState(results = res)
            } catch (e: Exception) {
                _search.value = SearchUiState(error = e.message ?: "Error de búsqueda")
            }
        }
    }

    fun addSearchResult(item: SearchResultItem) {
        viewModelScope.launch {
            // Carátula/metadatos extra de iTunes/Deezer si están disponibles
            val meta = runCatching { com.mixcasete.app.util.MetadataApi.search("${item.artist} ${item.title}") }.getOrNull()
            repo.addTrack(
                Track(
                    id = "yt-" + item.youtubeId,
                    title = meta?.title ?: item.title,
                    artist = meta?.artist ?: item.artist,
                    youtubeId = item.youtubeId,
                    artworkUrl = item.artworkUrl ?: meta?.artworkUrl,
                    durationMs = item.durationMs.takeIf { it > 0 } ?: (meta?.durationMs ?: 0L)
                )
            )
        }
    }

    fun addLocalFile(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { repo.addLocalFile(uri) }
                .onFailure { _ui.value = _ui.value.copy(error = "No se pudo agregar el archivo") }
        }
    }

    fun removeTrack(id: String) {
        viewModelScope.launch { repo.removeTrack(id) }
    }

    /** Mover pista dentro de la lista: delta = -1 sube, +1 baja (reordenación persistida). */
    fun moveTrack(index: Int, delta: Int) {
        val list = _ui.value.tracks.toMutableList()
        if (index !in list.indices) return
        val to = index + delta
        if (to !in list.indices) return
        val cur = _ui.value.currentIndex
        val item = list.removeAt(index)
        list.add(to, item)
        val newCur = when {
            cur == index -> to
            delta < 0 && cur == to -> cur + 1
            delta > 0 && cur == to -> cur - 1
            else -> cur
        }
        _ui.value = _ui.value.copy(tracks = list, currentIndex = newCur)
        viewModelScope.launch { repo.reorder(list.map { it.id }) }
    }

    fun showPlaylist() { _ui.value = _ui.value.copy(showPlaylist = true) }
    fun hidePlaylist() { _ui.value = _ui.value.copy(showPlaylist = false) }

    fun notifyNoExternalDisplay() {
        _ui.value = _ui.value.copy(notice = "No se detecta pantalla externa")
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { repo.reorder(ids) }
    }

    // ---------------------------------------------------------------- Login opcional

    fun setLoggedIn(logged: Boolean) {
        viewModelScope.launch { settingsStore.setYtLoggedIn(logged) }
    }

    // ---------------------------------------------------------------- Compartir TV

    fun setSharing(active: Boolean) {
        _ui.value = _ui.value.copy(sharingToTv = active)
    }

    // ---------------------------------------------------------------- Fuente / fallback

    /** Intenta la siguiente fuente pendiente de la pista actual. */
    private fun tryNextSource(startFromFront: Boolean) {
        if (startFromFront) pendingSources = sourcesFor(_ui.value.currentTrack ?: return)
        val track = _ui.value.currentTrack ?: return
        val next = pendingSources.firstOrNull() ?: run {
            // Sin más fuentes: probar preview de 30 s como último recurso
            viewModelScope.launch {
                val preview = repo.findPreview(track.title, track.artist)
                if (preview != null) {
                    _ui.value = _ui.value.copy(
                        notice = "Fuentes agotadas: usando preview de 30 s",
                        source = SourceKind.PREVIEW_30S
                    )
                    engine.play(preview)
                } else {
                    _ui.value = _ui.value.copy(error = "Sin fuentes disponibles para «${track.title}»")
                    stopNoAudioWatchdog()
                }
            }
            return
        }
        pendingSources = pendingSources.drop(1)
        log("Fuente -> $next")
        when (next) {
            SourceKind.YOUTUBE_IFRAME -> {
                _ui.value = _ui.value.copy(source = next)
                ytCommand?.send(track.youtubeId, true, null)
            }
            SourceKind.NEWPIPE -> viewModelScope.launch {
                val id = track.youtubeId
                if (id != null) {
                    val resolved = runCatching { repo.resolveStream(id) }.getOrNull()
                    if (resolved != null) {
                        _ui.value = _ui.value.copy(source = next)
                        engine.play(
                            track.copy(
                                streamUrl = resolved.url,
                                streamExpiresAt = resolved.expiresAt,
                                artworkUrl = resolved.artworkUrl ?: track.artworkUrl,
                                durationMs = resolved.durationMs
                            )
                        )
                    } else {
                        log("NewPipe falló para $id")
                        tryNextSource(startFromFront = false)
                    }
                } else tryNextSource(startFromFront = false)
            }
            SourceKind.LOCAL -> {
                _ui.value = _ui.value.copy(source = next)
                engine.play(track)
            }
            SourceKind.PREVIEW_30S -> viewModelScope.launch {
                val preview = repo.findPreview(track.title, track.artist)
                if (preview != null) {
                    _ui.value = _ui.value.copy(
                        source = next,
                        notice = "Último recurso: preview de 30 s"
                    )
                    engine.play(preview)
                } else tryNextSource(startFromFront = false)
            }
        }
    }

    /** Llamado por la UI cuando el IFrame de YouTube reporta estado real. */
    fun onYouTubeState(playing: Boolean, positionMs: Long, durationMs: Long, error: String?) {
        _ytPlaying.value = playing
        if (playing) {
            _ui.value = _ui.value.copy(
                isPlaying = true,
                positionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else _ui.value.durationMs
            )
            stopNoAudioWatchdog()
        } else if (_ui.value.source == SourceKind.YOUTUBE_IFRAME) {
            _ui.value = _ui.value.copy(isPlaying = engine.isPlaying.value)
        }
        if (error != null) {
            log("IFrame error: $error")
            advanceSourceOrNext(reason = error)
        }
    }

    /** Watchdog global: si en 8 s ninguna fuente reporta audio, pasar a la siguiente. */
    private fun startNoAudioWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            delay(AudioEngine.SILENCE_TIMEOUT_MS)
            if (!_ui.value.isPlaying && _ui.value.hasTape) onNoAudio()
        }
    }

    private fun stopNoAudioWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun onNoAudio() {
        val st = _ui.value
        if (!st.hasTape) return
        log("Watchdog: sin audio tras ${AudioEngine.SILENCE_TIMEOUT_MS / 1000} s")
        _ui.value = st.copy(error = "Sin audio: cambiando de fuente…")
        engine.stop()
        ytCommand?.send(null, false, null)
        tryNextSource(startFromFront = false)
        startNoAudioWatchdog()
    }

    private fun advanceSourceOrNext(reason: String) {
        engine.stop()
        if (pendingSources.isNotEmpty()) {
            tryNextSource(startFromFront = false)
        } else {
            _ui.value = _ui.value.copy(error = "Error en «${_ui.value.currentTrack?.title}»: $reason")
            next()
        }
    }

    private fun onTrackEnded() {
        val st = _ui.value
        when (st.repeat) {
            RepeatMode.ONE -> st.currentIndex.let { playAt(it) }
            RepeatMode.ALL -> next()
            RepeatMode.OFF -> {
                if (st.currentIndex < st.tracks.size - 1) next()
                else stopEject()
            }
        }
    }

    private fun log(msg: String) {
        _ui.value = _ui.value.copy(debugLog = (_ui.value.debugLog + msg).takeLast(60))
    }

    override fun onCleared() {
        super.onCleared()
        // El servicio mantiene la reproducción en segundo plano; no liberamos el motor aquí.
    }
}

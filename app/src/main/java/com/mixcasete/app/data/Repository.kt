package com.mixcasete.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mixcasete.app.data.db.TrackDao
import com.mixcasete.app.data.db.TrackEntity
import com.mixcasete.app.util.MetadataApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de una búsqueda para agregar pistas. */
data class SearchResultItem(
    val youtubeId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String?
)

/** Detalles resueltos por NewPipeExtractor para reproducir con ExoPlayer. */
data class ResolvedStream(
    val url: String,
    val expiresAt: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long
)

/** Downloader mínimo sobre HttpURLConnection para NewPipeExtractor. */
private class HttpDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        for ((k, v) in request.headers()) conn.setRequestProperty(k, v.joinToString("; "))
        conn.requestMethod = request.httpMethod()
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = false
        val body = request.dataToSend()
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val message = try { conn.responseMessage } catch (_: Exception) { "" }
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = conn.headerFields
            .filterKeys { it != null }
            .mapValues { e -> e.value ?: listOf() }
        // Firma real en v0.24.8: Response(int, String?, Map, String?, String?)
        return Response(code, message, headers, text, request.url())
    }
}

@Singleton
class Repository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrackDao
) {
    init {
        // Inicialización global del extractor (idempotente)
        runCatching { NewPipe.init(HttpDownloader(), Localization.DEFAULT) }
    }

    /** Búsqueda de streams de YouTube vía NewPipeExtractor (usando la API pública de metadatos). */
    private val ytService by lazy {
        runCatching { NewPipe.getService("YouTube") }.getOrNull()
    }

    // ---------------------------------------------------------------- Lista

    fun observeTracks(): Flow<List<Track>> = dao.observeAll().map { list ->
        list.map {
            Track(
                id = it.id,
                title = it.title,
                artist = it.artist,
                youtubeId = it.youtubeId,
                localUri = it.localUri,
                artworkUrl = it.artworkUrl,
                durationMs = it.durationMs,
                favorite = it.favorite
            )
        }
    }

    suspend fun addTrack(track: Track) = withContext(Dispatchers.IO) {
        val pos = (dao.maxPosition() ?: 0) + 1
        dao.insert(
            TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                youtubeId = track.youtubeId,
                localUri = track.localUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                position = pos,
                favorite = track.favorite
            )
        )
    }

    suspend fun removeTrack(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    /** Reordena la lista completa asignando posiciones secuenciales. */
    suspend fun reorder(idsInOrder: List<String>) = withContext(Dispatchers.IO) {
        idsInOrder.forEachIndexed { i, id -> dao.setPosition(id, i + 1) }
    }

    suspend fun setFavorite(id: String, fav: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, fav)
    }

    // ---------------------------------------------------------------- Búsqueda

    /**
     * Busca pistas usando las APIs de metadatos (iTunes primero, Deezer como respaldo),
     * que devuelven título, artista, duración y carátula. El usuario crea sus listas;
     * no se trae ninguna lista impuesta.
     */
    suspend fun search(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val metas = MetadataApi.searchAll(query)
        metas.map { meta ->
            SearchResultItem(
                youtubeId = "",  // sin ID de YouTube: se reproducirá vía preview/local si procede
                title = meta.title ?: query,
                artist = meta.artist ?: "Desconocido",
                durationMs = meta.durationMs,
                artworkUrl = meta.artworkUrl
            )
        }.take(25)
    }

    // ---------------------------------------------------------------- Resolución NewPipe

    /**
     * Extrae el stream de audio de un video (YouTube o URL directa) con reintentos y backoff.
     * La URL resultante caduca; [ResolvedStream.expiresAt] indica cuándo renovar
     * (se vuelve a llamar a este método, que re-extrae una URL fresca).
     */
    suspend fun resolveStream(urlOrId: String, attempts: Int = 3): ResolvedStream =
        withContext(Dispatchers.IO) {
            val url = if (urlOrId.startsWith("http")) urlOrId
                      else "https://www.youtube.com/watch?v=$urlOrId"
            var lastError: Exception? = null
            for (attempt in 1..attempts) {
                try {
                    val info = StreamInfo.getInfo(url)
                    val stream = info.audioStreams.sortedByDescending { it.bitrate }.firstOrNull()
                        ?: info.videoStreams.sortedBy { it.bitrate }.firstOrNull()
                        ?: throw ExtractionException("Sin streams disponibles")
                    return@withContext ResolvedStream(
                        url = stream.content,
                        expiresAt = System.currentTimeMillis() + 5 * 60 * 1000L, // renovación a los 5 min
                        title = info.name,
                        artist = info.uploaderName,
                        artworkUrl = info.thumbnails.firstOrNull()?.url,
                        durationMs = info.duration * 1000L
                    )
                } catch (e: Exception) {
                    lastError = e
                    // Backoff exponencial simple: 1 s, 2 s, 4 s
                    delay(1000L shl (attempt - 1))
                }
            }
            throw lastError ?: ExtractionException("No se pudo resolver $urlOrId")
        }

    // ---------------------------------------------------------------- Archivos locales

    /** Agrega un archivo seleccionado con ACTION_OPEN_DOCUMENT. */
    suspend fun addLocalFile(uri: Uri): Track = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri) ?: "Pista local"
        val (title, artist) = splitTitleArtist(name.removeSuffix(extensionOf(name)))
        // Intentar completar metadatos/carátula desde iTunes/Deezer
        val meta = runCatching { MetadataApi.search("$title $artist") }.getOrNull()
        val track = Track(
            id = "local-" + uri.toString().hashCode().toString(16),
            title = meta?.title ?: title,
            artist = meta?.artist ?: artist,
            localUri = uri.toString(),
            artworkUrl = meta?.artworkUrl,
            durationMs = meta?.durationMs ?: 0L
        )
        addTrack(track)
        track
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else ""
    }

    private fun splitTitleArtist(name: String): Pair<String, String> {
        val parts = name.split(" - ", limit = 2)
        return if (parts.size == 2) parts[1].trim() to parts[0].trim() else name.trim() to "Desconocido"
    }

    // ---------------------------------------------------------------- Preview 30 s (último recurso)

    /** Devuelve una pista de preview de 30 s desde iTunes/Deezer. El llamador debe avisar al usuario. */
    suspend fun findPreview(title: String, artist: String): Track? = withContext(Dispatchers.IO) {
        val meta = MetadataApi.search("$artist $title") ?: return@withContext null
        val preview = meta.previewUrl ?: return@withContext null
        Track(
            id = "preview-" + preview.hashCode().toString(16),
            title = meta.title ?: title,
            artist = meta.artist ?: artist,
            streamUrl = preview,
            streamExpiresAt = Long.MAX_VALUE, // los previews no caducan rápido
            artworkUrl = meta.artworkUrl,
            durationMs = 30_000L,
            isPreview = true
        )
    }
}

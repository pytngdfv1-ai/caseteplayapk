package com.mixcasete.app.data

/**
 * Una pista del casete. Puede provenir de YouTube (videoId), de un archivo local (uri)
 * o de una URL directa resuelta por NewPipeExtractor.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    /** ID de 11 caracteres de YouTube si la fuente principal es YouTube. */
    val youtubeId: String? = null,
    /** URI local (content:// o file://) obtenida con el selector de documentos. */
    val localUri: String? = null,
    /** URL de audio directa (stream) ya resuelta; puede caducar y se renueva. */
    val streamUrl: String? = null,
    /** Caducidad estimada del streamUrl en epoch millis. */
    val streamExpiresAt: Long = 0L,
    /** Carátula obtenida de iTunes/Deezer. */
    val artworkUrl: String? = null,
    /** Duración conocida en ms (0 si desconocida). */
    val durationMs: Long = 0L,
    /** true si solo se pudo obtener un preview de 30 s (iTunes/Deezer). */
    val isPreview: Boolean = false,
    val favorite: Boolean = false
)

enum class SourceKind { YOUTUBE_IFRAME, NEWPIPE, LOCAL, PREVIEW_30S }

enum class RepeatMode { OFF, ONE, ALL }

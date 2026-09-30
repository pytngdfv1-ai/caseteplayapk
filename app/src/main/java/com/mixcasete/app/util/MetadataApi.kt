package com.mixcasete.app.util

import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Búsqueda de metadatos y carátulas: iTunes primero, Deezer como respaldo.
 * El preview de 30 s SOLO se usa como último recurso (lo decide el Repository).
 */
object MetadataApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Meta(
        val title: String?,
        val artist: String?,
        val artworkUrl: String?,
        /** Preview de 30 s; solo se usa si no hay ninguna otra fuente de audio. */
        val previewUrl: String?,
        val durationMs: Long
    )

    private fun get(url: String): String? = try {
        val req = okhttp3.Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (_: Exception) {
        null
    }

    fun searchItunes(query: String): Meta? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val body = get("https://itunes.apple.com/search?term=$encoded&media=music&limit=1") ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val r = results.getJSONObject(0)
            // 100x100 -> 600x600 para carátula grande
            val art = r.optString("artworkUrl100").replace("100x100", "600x600")
            Meta(
                title = r.optString("trackName").ifBlank { null },
                artist = r.optString("artistName").ifBlank { null },
                artworkUrl = art.ifBlank { null },
                previewUrl = r.optString("previewUrl").ifBlank { null },
                durationMs = (r.optDouble("trackTimeMillis", 0.0)).toLong()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun searchDeezer(query: String): Meta? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val body = get("https://api.deezer.com/search?q=$encoded&limit=1") ?: return null
        return try {
            val data = JSONObject(body).optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val t = data.getJSONObject(0)
            Meta(
                title = t.optString("title_short").ifBlank { null },
                artist = t.optJSONObject("artist")?.optString("name")?.ifBlank { null },
                artworkUrl = t.optString("album").let { al ->
                    JSONObject(al).optString("cover_xl").ifBlank { JSONObject(al).optString("cover_medium") }
                        .ifBlank { null }
                },
                previewUrl = t.optString("preview").ifBlank { null },
                durationMs = t.optLong("duration", 0L) * 1000L
            )
        } catch (_: Exception) {
            null
        }
    }

    /** iTunes + fallback Deezer. */
    fun search(query: String): Meta? = searchItunes(query) ?: searchDeezer(query)
}

package com.bryan.corolladash.lyrics

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Fuente de letras intercambiable. Devuelve null si no encuentra la canción; lanza IOException sin red. */
interface LyricsProvider {
    val name: String
    /** Última respuesta (código HTTP, endpoint y tiempo) para Diagnóstico. */
    val lastResponse: String?
    @Throws(IOException::class)
    fun fetch(song: SongIdentity): ProviderLyrics?
}

/**
 * LRCLIB (https://lrclib.net): API pública y gratuita de letras, sin clave.
 * 1. /api/get con título, artista, álbum y duración (coincidencia exacta ±2 s).
 * 2. Si no hay resultado: /api/search y se elige la versión con duración más cercana (máx. 4 s),
 *    prefiriendo letra sincronizada. Se reintenta con el título "limpio" (sin feat., remaster, etc.).
 */
class LrclibProvider : LyricsProvider {
    override val name = "LRCLIB"
    @Volatile override var lastResponse: String? = null
        private set

    override fun fetch(song: SongIdentity): ProviderLyrics? {
        val durS = (song.durationMs / 1000).toInt()
        val artist = primaryArtist(song.artist)

        var lastError: Exception? = null
        var anyAnswer = false

        // 1) Coincidencia exacta (si la radio da álbum y duración). Un error aquí no detiene la búsqueda.
        if (song.album != null && durS > 0) {
            try {
                get("/api/get", mapOf(
                    "track_name" to song.title, "artist_name" to song.artist,
                    "album_name" to song.album, "duration" to durS.toString(),
                ))?.let { return toLyrics(JSONObject(it)) }
                anyAnswer = true
            } catch (e: Exception) {
                lastError = e
            }
        }
        // 2) Búsqueda: título completo y "limpio" (sin feat., Spotify Sessions…), artista completo y principal.
        val titles = listOf(song.title, cleanTitle(song.title)).distinct()
        for (t in titles) {
            for (a in listOf(song.artist, artist).distinct()) {
                try {
                    val body = get("/api/search", mapOf("track_name" to t, "artist_name" to a))
                    anyAnswer = true
                    if (body != null) pickBest(JSONArray(body), durS)?.let { return toLyrics(it) }
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        // 3) Último intento: texto libre "artista título".
        try {
            val body = get("/api/search", mapOf("q" to "$artist ${cleanTitle(song.title)}"))
            anyAnswer = true
            if (body != null) pickBest(JSONArray(body), durS)?.let { return toLyrics(it) }
        } catch (e: Exception) {
            lastError = e
        }
        // Si ninguna consulta obtuvo respuesta, es un problema de conexión: informarlo.
        if (!anyAnswer && lastError != null) throw IOException(describe(lastError), lastError)
        return null
    }

    private fun pickBest(arr: JSONArray, durS: Int): JSONObject? {
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = o.optDouble("duration", -1.0)
            val diff = if (durS > 0 && d > 0) abs(d - durS) else 0.0
            if (durS > 0 && d > 0 && diff > 4) continue
            val hasSynced = !o.isNull("syncedLyrics") && o.optString("syncedLyrics").isNotBlank()
            val score = diff + (if (hasSynced) 0.0 else 2.5)
            if (score < bestScore) { bestScore = score; best = o }
        }
        return best
    }

    private fun toLyrics(o: JSONObject): ProviderLyrics = ProviderLyrics(
        synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() },
        plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() },
        instrumental = o.optBoolean("instrumental", false),
    )

    /** GET con un reintento si falla la conexión (no si el servidor responde). */
    private fun get(path: String, params: Map<String, String>): String? = try {
        getOnce(path, params)
    } catch (e: HttpStatus) {
        throw e
    } catch (e: IOException) {
        Thread.sleep(1200)
        getOnce(path, params)
    }

    private class HttpStatus(code: Int, path: String) : IOException("HTTP $code en $path")

    /** GET → cuerpo (200) o null (404). Otros códigos: HttpStatus. */
    private fun getOnce(path: String, params: Map<String, String>): String? {
        val q = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        val conn = URL("$BASE$path?$q").openConnection() as HttpURLConnection
        val t0 = System.currentTimeMillis()
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Lrclib-Client", USER_AGENT)
            val code = conn.responseCode
            lastResponse = "HTTP $code · $path · ${System.currentTimeMillis() - t0} ms"
            Log.d(TAG, lastResponse.orEmpty())
            return when (code) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw HttpStatus(code, path)
            }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "CorollaLyrics"

        /** Motivo corto y comprensible para mostrar en pantalla. */
        fun describe(e: Exception): String = when (e) {
            is java.net.UnknownHostException -> "No se encontró el servidor (DNS)"
            is java.net.SocketTimeoutException -> "El servidor tardó demasiado"
            is javax.net.ssl.SSLException -> "Error de conexión segura (SSL)"
            is java.net.ConnectException -> "No se pudo conectar al servidor"
            else -> e.message ?: e.javaClass.simpleName
        }
        const val BASE = "https://lrclib.net"
        const val USER_AGENT = "CorollaDash/0.4 (Android head unit; personal use)"

        fun primaryArtist(a: String): String =
            a.split(Regex("""\s*(,|&|\bfeat\.?|\bft\.?|\bx\b|;)\s*""", RegexOption.IGNORE_CASE)).first().trim().ifBlank { a }

        fun cleanTitle(t: String): String = t
            .replace(Regex("""\s*[(\[](feat|ft|with|remaster|live|radio edit|version|from)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*(remaster|version|edit|live|mix).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { t }
    }
}

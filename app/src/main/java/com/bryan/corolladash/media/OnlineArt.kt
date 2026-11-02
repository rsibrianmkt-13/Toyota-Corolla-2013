package com.bryan.corolladash.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.bryan.corolladash.lyrics.SongIdentity
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections

/**
 * Último recurso para la carátula cuando Bluetooth no la envía: búsqueda por artista + título
 * en la API pública de iTunes Search (sin clave). Solo con internet.
 * Caché en disco (filesDir/art, máx. 150 imágenes JPEG de 512 px) y "no encontrada" en memoria,
 * así cada canción se consulta una sola vez.
 */
class OnlineArt(private val context: Context) {
    private val dir = File(context.filesDir, "art").apply { mkdirs() }
    private val misses = Collections.synchronizedSet(HashSet<String>())

    @Volatile var lastResult: String? = null
        private set

    /** Bloqueante: llamar desde IO. */
    fun find(title: String?, artist: String?): Bitmap? {
        if (title.isNullOrBlank()) return null
        val key = "${SongIdentity.normalize(title)}|${SongIdentity.normalize(artist.orEmpty())}"
        val file = file(key)
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            BitmapFactory.decodeFile(file.path)?.let { lastResult = "caché"; return it }
        }
        if (key in misses || !online()) return null

        // Dos fuentes públicas: iTunes y, si falla o no encuentra, Deezer.
        val t = cleanTitle(title)
        val a = primaryArtist(artist.orEmpty())
        val errors = mutableListOf<String>()
        var answered = false
        val sources: List<Pair<String, (String, String) -> String?>> = listOf(
            "iTunes" to { x, y -> searchItunes(x, y) },
            "Deezer" to { x, y -> searchDeezer(x, y) },
        )
        for ((source, finder) in sources) {
            try {
                val url = finder(t, a)
                answered = true
                if (url != null) {
                    val bmp = download(url) ?: continue
                    runCatching {
                        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        prune()
                    }
                    lastResult = source
                    return bmp
                }
            } catch (e: Exception) {
                errors += "$source: ${e.javaClass.simpleName}"
                Log.d(TAG, "Carátula en línea ($source) falló: $e")
            }
        }
        lastResult = if (answered) "no encontrada" else "error: ${errors.joinToString(" · ")}"
        if (answered) misses += key
        return null
    }

    /** Deezer: álbum de la canción (cover_xl, 1000 px). */
    private fun searchDeezer(title: String, artist: String): String? {
        val q = URLEncoder.encode("artist:\"$artist\" track:\"$title\"", "UTF-8")
        val body = get("https://api.deezer.com/search?q=$q&limit=8") ?: return null
        val data = JSONObject(body).optJSONArray("data") ?: return null
        val nt = SongIdentity.normalize(title)
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val rt = SongIdentity.normalize(o.optString("title"))
            if (rt == nt || rt.startsWith(nt) || nt.startsWith(rt)) {
                val album = o.optJSONObject("album") ?: continue
                return album.optString("cover_xl").ifBlank { album.optString("cover_big") }.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** iTunes: URL de la carátula (600 px) del resultado cuyo título y artista coinciden. */
    private fun searchItunes(title: String, artist: String): String? {
        val term = URLEncoder.encode("$artist $title".trim(), "UTF-8")
        val body = get("https://itunes.apple.com/search?term=$term&entity=song&limit=8") ?: return null
        val results = JSONObject(body).optJSONArray("results") ?: return null
        val nt = SongIdentity.normalize(title)
        val na = SongIdentity.normalize(artist)
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val rt = SongIdentity.normalize(o.optString("trackName"))
            val ra = SongIdentity.normalize(o.optString("artistName"))
            val titleOk = rt == nt || rt.startsWith(nt) || nt.startsWith(rt)
            val artistOk = na.isEmpty() || ra.contains(na) || na.contains(ra)
            if (titleOk && artistOk) {
                return o.optString("artworkUrl100").takeIf { it.isNotBlank() }?.replace("100x100bb", "600x600bb")
            }
        }
        return null
    }

    private fun download(url: String): Bitmap? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 512) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", UA)
            when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw java.io.IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun online(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    private fun prune() {
        val files = dir.listFiles() ?: return
        if (files.size > MAX_FILES) files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private fun file(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.jpg")
    }

    private companion object {
        const val TAG = "CorollaMedia"
        const val UA = "CorollaDash/0.9 (Android head unit; personal use)"
        const val MAX_FILES = 150

        fun primaryArtist(a: String): String =
            a.split(Regex("""\s*(,|&|\bfeat\.?|\bft\.?|\bx\b|;)\s*""", RegexOption.IGNORE_CASE)).first().trim()

        fun cleanTitle(t: String): String = t
            .replace(Regex("""\s*[(\[](feat|ft|with|remaster|live|radio edit|version|from)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*(remaster|version|edit|live|mix).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { t }
    }
}

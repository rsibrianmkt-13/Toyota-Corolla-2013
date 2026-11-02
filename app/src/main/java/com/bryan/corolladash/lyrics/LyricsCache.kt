package com.bryan.corolladash.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Caché de letras:
 *  - memoria: últimas 40 canciones (incluye "no encontrada" para no repetir la consulta en esta sesión);
 *  - disco: un JSON pequeño por canción en filesDir/lyrics, máximo 300 archivos (se borran los más antiguos).
 * Solo se guarda en disco lo encontrado (sincronizada, normal o instrumental).
 */
class LyricsCache(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    private val memory = object : LinkedHashMap<String, LyricsResult>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>?) = size > 40
    }

    @Synchronized
    fun memory(key: String): LyricsResult? = memory[key]

    @Synchronized
    fun putMemory(key: String, r: LyricsResult) { memory[key] = r }

    fun disk(key: String): LyricsResult? = runCatching {
        val f = file(key)
        if (!f.exists()) return null
        f.setLastModified(System.currentTimeMillis())
        val o = JSONObject(f.readText())
        when (o.optString("type")) {
            "synced" -> LyricsResult.Synced(LrcParser.parse(o.getString("lrc")))
            "plain" -> LyricsResult.Plain(o.getString("plain").lines())
            "instrumental" -> LyricsResult.Instrumental
            else -> null
        }
    }.getOrNull()

    fun putDisk(key: String, p: ProviderLyrics) = runCatching {
        val o = JSONObject()
        when {
            p.instrumental -> o.put("type", "instrumental")
            p.synced != null -> o.put("type", "synced").put("lrc", p.synced)
            p.plain != null -> o.put("type", "plain").put("plain", p.plain)
            else -> return@runCatching
        }
        file(key).writeText(o.toString())
        prune()
    }

    fun count(): Int = dir.listFiles()?.size ?: 0

    private fun prune() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private companion object {
        const val MAX_FILES = 300
    }
}

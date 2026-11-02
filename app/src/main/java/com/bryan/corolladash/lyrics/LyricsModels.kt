package com.bryan.corolladash.lyrics

import androidx.compose.runtime.Immutable

import java.text.Normalizer
import java.util.Locale

/** Una línea de letra sincronizada. */
@Immutable
data class LyricLine(val timeMs: Long, val text: String)

/** Resultado visible de la búsqueda de letra para la canción actual. */
@Immutable
sealed interface LyricsResult {
    /** Sin canción o función desactivada. */
    data object Idle : LyricsResult
    data object Loading : LyricsResult
    data class Synced(val lines: List<LyricLine>) : LyricsResult
    data class Plain(val lines: List<String>) : LyricsResult
    data object Instrumental : LyricsResult
    data object NotFound : LyricsResult
    data object Offline : LyricsResult
    /** [technical] solo se muestra en Diagnóstico, nunca en la interfaz normal. */
    data class Error(val technical: String) : LyricsResult
}

/** Identidad de la canción (MediaSession). La posición NO forma parte de la identidad. */
@Immutable
data class SongIdentity(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val mediaId: String?,
) {
    /**
     * Identidad y clave de caché: título + artista normalizados. La duración, el álbum o el Media ID
     * NO cuentan: algunas radios los reenvían o cambian mientras suena y eso reiniciaba la búsqueda.
     */
    val cacheKey: String get() = "${normalize(title)}|${normalize(artist)}"

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
    }
}

/** Estado completo publicado a la interfaz. */
@Immutable
data class LyricsState(
    val song: SongIdentity? = null,
    val result: LyricsResult = LyricsResult.Idle,
    val provider: String? = null,
    val fromCache: Boolean = false,
)

/** Lo que devuelve un proveedor cuando encuentra la canción. */
data class ProviderLyrics(
    val synced: String?,
    val plain: String?,
    val instrumental: Boolean,
)

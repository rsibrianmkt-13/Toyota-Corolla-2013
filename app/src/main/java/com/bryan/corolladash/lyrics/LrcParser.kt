package com.bryan.corolladash.lyrics

/**
 * Parser LRC propio.
 *  "[01:34.20] But I'll make it through" → LyricLine(94200, "But I'll make it through")
 * Soporta varias marcas por línea ([00:12.00][01:30.50] texto), fracciones de 1–3 dígitos,
 * la etiqueta [offset:±ms] e ignora metadatos como [ar:], [ti:], [al:], [by:], [length:].
 */
object LrcParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val META = Regex("""^\[([a-zA-Z#]+):(.*)]\s*$""")

    fun parse(lrc: String): List<LyricLine> {
        var offsetMs = 0L
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val stamps = TIME.findAll(line).toList()
            if (stamps.isEmpty()) {
                META.find(line)?.let { m ->
                    if (m.groupValues[1].equals("offset", ignoreCase = true)) {
                        offsetMs = m.groupValues[2].trim().toLongOrNull() ?: 0L
                    }
                }
                continue
            }
            // El texto es lo que queda después de la última marca de tiempo.
            val text = line.substring(stamps.last().range.last + 1).trim()
            for (s in stamps) {
                val min = s.groupValues[1].toLong()
                val sec = s.groupValues[2].toLong()
                val frac = s.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                // En LRC un offset positivo adelanta la letra.
                val t = (min * 60_000 + sec * 1000 + ms - offsetMs).coerceAtLeast(0)
                out += LyricLine(t, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Índice de la línea activa para [positionMs] (−1 = antes de la primera línea). */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

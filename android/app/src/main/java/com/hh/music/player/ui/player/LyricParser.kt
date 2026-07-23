package com.hh.music.player.ui.player

import com.hh.music.player.data.Lyric

/** A single time-tagged lyric line. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parse an LRC string (NetEase `lrc`/`tlyric`) into ordered [LyricLine]s.
 * Supports multiple time tags on one line, e.g. `[00:01.00][00:30.00]歌词`.
 */
object LyricParser {
    private val tagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        for (line in raw.lineSequence()) {
            val text = line.trim()
            if (text.isEmpty()) continue
            val matches = tagRegex.findAll(text).toList()
            if (matches.isEmpty()) {
                // Header metadata like [ti:...] [ar:...] — skip.
                if (text.startsWith("[") && text.contains("]:")) continue
                continue
            }
            val content = text.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val (min, sec, frac) = m.destructured
                val ms = (min.toLong() * 60_000) +
                    (sec.toLong() * 1_000) +
                    (frac.padEnd(3, '0').take(3).let { if (it.isBlank()) 0L else it.toLong() })
                lines += LyricLine(ms, content)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Translation lines keyed by time, merged from `tlyric`. */
    fun translations(raw: String?): Map<Long, String> =
        parse(raw).associate { it.timeMs to it.text }
}

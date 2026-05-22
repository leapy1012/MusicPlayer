package gd.app.musicplayer.feature.lyrics

import gd.app.lib.model.lrc.renderer.FocusedLyricRenderer
import gd.app.lib.model.lrc.renderer.ScrollingLyricRenderer
import gd.app.lib.model.lrc.renderer.StaticMessageLyricRenderer
import gd.app.lib.model.lrc.resource.LyricLine
import gd.app.lib.model.lrc.resource.LyricText
import gd.app.lib.model.lrc.resource.StringLyricResource
import gd.app.lib.model.lrc.view.LyricView
import java.util.WeakHashMap

private data class LyricCompatState(
    val hasTimedLyrics: Boolean
)

private val lyricCompatState = WeakHashMap<LyricView, LyricCompatState>()

fun LyricView.setLyricText(text: String?) {
    val normalized = text
        ?.replace("\uFEFF", "")
        ?.replace("\r\n", "\n")
        ?.trim()

    if (normalized.isNullOrBlank()) {
        setLyricRenderer(StaticMessageLyricRenderer(""))
        lyricCompatState[this] = LyricCompatState(hasTimedLyrics = false)
        return
    }

    val parseResult = parseLyrics(normalized)
    val lyricText = LyricText(StringLyricResource(normalized)).apply {
        setLines(parseResult.mode, parseResult.lines)
    }

    setLyricRenderer(
        if (getMaxLines() > 0) {
            FocusedLyricRenderer(lyricText)
        } else {
            ScrollingLyricRenderer(lyricText.copy())
        }
    )
    lyricCompatState[this] = LyricCompatState(hasTimedLyrics = parseResult.hasTimedLyrics)
}

fun LyricView.hasTimedLyrics(): Boolean =
    lyricCompatState[this]?.hasTimedLyrics == true

// Compatibility alias for old Java/Kotlin call sites.
fun LyricView.a(): Boolean = isScrollable()

private data class ParsedLyrics(
    val mode: Int,
    val lines: List<LyricLine>,
    val hasTimedLyrics: Boolean
)

private fun parseLyrics(raw: String): ParsedLyrics {
    val timestampRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    val timedLines = mutableListOf<LyricLine>()
    val plainLines = mutableListOf<LyricLine>()
    var hasTimed = false

    raw.lineSequence().forEach { line ->
        val matches = timestampRegex.findAll(line).toList()
        if (matches.isEmpty()) {
            plainLines += LyricLine(0L, line.trim())
            return@forEach
        }

        hasTimed = true
        val lyricText = timestampRegex.replace(line, "").trim()
        matches.forEach { match ->
            val minute = match.groupValues[1].toLongOrNull() ?: 0L
            val second = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionRaw = match.groupValues[3]
            val millis = when (fractionRaw.length) {
                0 -> 0L
                1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                else -> fractionRaw.take(3).toLongOrNull() ?: 0L
            }
            timedLines += LyricLine(minute * 60_000L + second * 1_000L + millis, lyricText)
        }
    }

    return if (hasTimed) {
        ParsedLyrics(
            mode = LyricText.MODE_NORMAL,
            lines = timedLines.sortedBy { it.startTime },
            hasTimedLyrics = true
        )
    } else {
        ParsedLyrics(
            mode = LyricText.MODE_AUTO_SCROLL,
            lines = plainLines,
            hasTimedLyrics = false
        )
    }
}

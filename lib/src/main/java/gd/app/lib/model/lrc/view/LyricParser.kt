package gd.app.lib.model.lrc.view

internal data class LyricLine(val timeMs: Long, val text: String)

internal object LyricParser {
    private val timestampRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = ArrayList<LyricLine>(64)
        raw.lineSequence().forEach { line ->
            val matches = timestampRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                result += LyricLine(0L, line.trim())
                return@forEach
            }
            val text = timestampRegex.replace(line, "").trim()
            matches.forEach { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: 0L
                val second = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val ms = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLongOrNull()?.times(100L) ?: 0L
                    2 -> fractionRaw.toLongOrNull()?.times(10L) ?: 0L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val timeMs = minute * 60_000L + second * 1_000L + ms
                result += LyricLine(timeMs, text)
            }
        }
        return result.sortedBy { it.timeMs }
    }
}

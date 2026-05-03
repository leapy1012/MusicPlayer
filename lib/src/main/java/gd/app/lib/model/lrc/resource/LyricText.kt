package gd.app.lib.model.lrc.resource

class LyricText(
    private val source: LyricResource
) {

    private var mode: Int = MODE_UNKNOWN
    private var selectedIndex: Int = -1

    private val lines: MutableList<LyricLine> = mutableListOf()

    fun copy(): LyricText {
        return LyricText(source).also { copy ->
            copy.mode = mode
            lines.forEach { line ->
                copy.lines.add(line.copy())
            }
        }
    }

    fun findLineIndexByTime(timeMs: Long): Int {
        return findLineByTime(timeMs, false)?.index ?: -1
    }

    fun findLineByTime(timeMs: Long, forceFirstInAutoMode: Boolean): LyricLine? {
        if (forceFirstInAutoMode && mode == MODE_AUTO_SCROLL) {
            return lines.firstOrNull()
        }

        if (lines.isEmpty() || mode != MODE_NORMAL) {
            return null
        }

        var previousTimedLine: LyricLine? = null

        for (line in lines) {
            if (line.startTime >= 0) {
                if (line.startTime > timeMs) {
                    return previousTimedLine ?: line
                }

                if (!line.isEmptyLine()) {
                    previousTimedLine = line
                }
            }
        }

        return lines.lastOrNull()
    }

    fun findLineByVerticalPosition(y: Float, allowNearestAbove: Boolean): LyricLine? {
        if (mode == MODE_AUTO_SCROLL) {
            return null
        }

        if (allowNearestAbove) {
            for (line in lines) {
                if (y <= line.bottom) {
                    return line
                }
            }
            return null
        }

        var previousLine: LyricLine? = null

        for (line in lines) {
            if (y < line.top) {
                break
            }

            if (y < line.bottom) {
                return line
            }

            previousLine = line
        }

        return previousLine
    }

    fun getMode(): Int = mode

    fun getSelectedIndex(): Int = selectedIndex

    fun getLine(index: Int): LyricLine = lines[index]

    fun getSource(): LyricResource = source

    fun isStaticOrInvalid(): Boolean {
        return mode != MODE_NORMAL
    }

    fun lineCount(): Int = lines.size

    fun setLines(mode: Int, lyricLines: List<LyricLine>?) {
        this.mode = mode

        if (!lyricLines.isNullOrEmpty()) {
            lyricLines.forEachIndexed { index, line ->
                line.index = index
            }

            lines.addAll(lyricLines)
        }
    }

    fun setSelectedIndex(index: Int) {
        selectedIndex = index
    }

    override fun toString(): String {
        return "LyricText(lines=$lines)"
    }

    companion object {
        const val MODE_NORMAL = 0
        const val MODE_UNKNOWN = 3
        const val MODE_AUTO_SCROLL = 5
    }
}
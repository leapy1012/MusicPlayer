package gd.app.musicplayer.ui.editor.model

data class AudioClipRange(
    val startMs: Int,
    val endMs: Int,
    val startFrame: Int? = null,
    val endFrame: Int? = null
) {
    val durationMs: Int
        get() = (endMs - startMs).coerceAtLeast(0)

    fun isValid(minDurationMs: Int = MIN_DURATION_MS): Boolean {
        return startMs >= 0 && endMs > startMs && durationMs >= minDurationMs
    }

    fun clampTo(durationMs: Int): AudioClipRange {
        val safeStart = startMs.coerceIn(0, durationMs)
        val safeEnd = endMs.coerceIn(safeStart, durationMs)
        return AudioClipRange(
            startMs = safeStart,
            endMs = safeEnd,
            startFrame = startFrame,
            endFrame = endFrame
        )
    }

    companion object {
        private const val MIN_DURATION_MS = 40

        fun fullDuration(durationMs: Int): AudioClipRange {
            return AudioClipRange(
                startMs = 0,
                endMs = durationMs.coerceAtLeast(0)
            )
        }
    }
}

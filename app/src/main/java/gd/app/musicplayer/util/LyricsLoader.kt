package gd.app.musicplayer.util

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LyricsLoadResult(
    val text: String?,
    val sourcePath: String?,
    val hasTimedLines: Boolean
) {
    val hasLyrics: Boolean
        get() = !text.isNullOrBlank()
}

object LyricsLoader {

    suspend fun load(
        context: android.content.Context,
        trackId: Long,
        audioPath: String?
    ): LyricsLoadResult = withContext(Dispatchers.IO) {
        val store = TrackLyricsStore.from(context)
        val customPath = store.getTrackLyricPath(trackId)
        val candidatePaths = buildList {
            customPath?.let(::add)
            audioPath?.let { path ->
                val file = File(path)
                val parent = file.parentFile
                val nameWithoutExtension = file.nameWithoutExtension
                if (parent != null && nameWithoutExtension.isNotBlank()) {
                    add(File(parent, "$nameWithoutExtension.lrc").absolutePath)
                    add(File(parent, "$nameWithoutExtension.txt").absolutePath)
                }
            }
        }.distinct()

        candidatePaths.forEach { candidate ->
            val file = File(candidate)
            if (!file.isFile) {
                if (candidate == customPath) {
                    store.setTrackLyricPath(trackId, null)
                }
                return@forEach
            }
            val text = runCatching { file.readText() }.getOrNull()?.normalizeLyrics()
            if (!text.isNullOrBlank()) {
                if (candidate != customPath) {
                    store.setTrackLyricPath(trackId, candidate)
                }
                return@withContext LyricsLoadResult(
                    text = text,
                    sourcePath = candidate,
                    hasTimedLines = text.containsTimestampTag()
                )
            }
        }

        val embedded = EmbeddedLyricsReader.read(audioPath)?.normalizeLyrics()
        return@withContext LyricsLoadResult(
            text = embedded,
            sourcePath = null,
            hasTimedLines = embedded?.containsTimestampTag() == true
        )
    }

    private fun String.normalizeLyrics(): String =
        replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .trim()

    private fun String.containsTimestampTag(): Boolean =
        TIMESTAMP_REGEX.containsMatchIn(this)

    private val TIMESTAMP_REGEX =
        Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
}

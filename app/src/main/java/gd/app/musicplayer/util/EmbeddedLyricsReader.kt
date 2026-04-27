package gd.app.musicplayer.util

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag

object EmbeddedLyricsReader {

    private val lyricFields = listOf(
        "LYRICS",
        "UNSYNCEDLYRICS",
        "UNSYNCED LYRICS",
        "USLT",
        "SYLT",
        "LYRIC",
        "LYRICIST",
        "©lyr",
        "----:com.apple.iTunes:LYRICS"
    )

    suspend fun read(path: String?): String? = withContext(Dispatchers.IO) {
        val safePath = path?.takeIf { it.isNotBlank() } ?: return@withContext null
        val file = File(safePath)
        if (!file.isFile) return@withContext null

        runCatching {
            val tag = AudioFileIO.read(file).tag ?: return@runCatching null
            extractLyrics(tag)
        }.getOrNull()
    }

    private fun extractLyrics(tag: Tag): String? {
        lyricFields.forEach { field ->
            runCatching {
                if (tag.hasField(field)) {
                    sanitize(tag.getFirst(field))?.let { return it }
                }
            }
        }
        return runCatching { sanitize(tag.getFirst(FieldKey.LYRICS)) }.getOrNull()
    }

    private fun sanitize(raw: String?): String? {
        val normalized = raw
            ?.replace("\u0000", "")
            ?.replace("\r\n", "\n")
            ?.trim()
        return normalized?.takeIf { it.isNotBlank() }
    }
}

package gd.app.musicplayer.playback.effects
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

data class ReplayGainInfo(
    val hasTrackGain: Boolean = false,
    val hasAlbumGain: Boolean = false,
    val trackGainDb: Float = 0f,
    val albumGainDb: Float = 0f
)

object ReplayGainParser {
    private val cache = ConcurrentHashMap<String, ReplayGainInfo>()

    fun parse(path: String?): ReplayGainInfo {
        val normalizedPath = path?.takeIf { it.isNotBlank() } ?: return ReplayGainInfo()
        return cache.getOrPut(normalizedPath) {
            runCatching { parseInternal(File(normalizedPath)) }.getOrDefault(ReplayGainInfo())
        }
    }

    fun clear(path: String? = null) {
        if (path.isNullOrBlank()) {
            cache.clear()
        } else {
            cache.remove(path)
        }
    }

    private fun parseInternal(file: File): ReplayGainInfo {
        if (!file.exists() || !file.isFile) return ReplayGainInfo()
        val tag = AudioFileIO.read(file).tag ?: return ReplayGainInfo()
        val fields = when (tag) {
            is VorbisCommentTag -> extractVorbisOrId3(tag)
            is FlacTag, is Mp4Tag -> extractContainerTag(tag)
            else -> extractVorbisOrId3(tag)
        }
        return ReplayGainInfo(
            hasTrackGain = fields.trackGain != null,
            hasAlbumGain = fields.albumGain != null,
            trackGainDb = fields.trackGain ?: 0f,
            albumGainDb = fields.albumGain ?: 0f
        )
    }

    private fun extractContainerTag(tag: Tag): GainFields =
        GainFields(
            trackGain = firstFloat(
                tag,
                "REPLAYGAIN_TRACK_GAIN",
                "----:com.apple.iTunes:REPLAYGAIN_TRACK_GAIN"
            ),
            albumGain = firstFloat(
                tag,
                "REPLAYGAIN_ALBUM_GAIN",
                "----:com.apple.iTunes:REPLAYGAIN_ALBUM_GAIN"
            )
        )

    private fun extractVorbisOrId3(tag: Tag): GainFields {
        val directTrack = firstFloat(tag, "REPLAYGAIN_TRACK_GAIN")
        val directAlbum = firstFloat(tag, "REPLAYGAIN_ALBUM_GAIN")
        if (directTrack != null || directAlbum != null) {
            return GainFields(trackGain = directTrack, albumGain = directAlbum)
        }

        val frameId = when {
            tag.hasField("TXXX") -> "TXXX"
            tag.hasField("RGAD") -> "RGAD"
            tag.hasField(ID3v24Frames.FRAME_ID_RELATIVE_VOLUME_ADJUSTMENT2) ->
                ID3v24Frames.FRAME_ID_RELATIVE_VOLUME_ADJUSTMENT2
            else -> return GainFields()
        }

        var trackGain: Float? = null
        var albumGain: Float? = null
        tag.getFields(frameId).forEach { field ->
            val parts = field.toString().split(";")
            if (parts.size < 2) return@forEach
            val rawName = parts[0]
            val name = rawName.substringAfter(':').substringAfter(':')
                .removePrefix("\"")
                .removeSuffix("\"")
                .uppercase()
            when {
                name.contains("TRACK") -> trackGain = parseGain(parts[1])
                name.contains("ALBUM") -> albumGain = parseGain(parts[1])
            }
        }
        return GainFields(trackGain = trackGain, albumGain = albumGain)
    }

    private fun firstFloat(tag: Tag, vararg keys: String): Float? =
        keys.firstNotNullOfOrNull { key ->
            if (tag.hasField(key)) parseGain(tag.getFirst(key)) else null
        }

    private fun parseGain(value: String?): Float? {
        val normalized = value
            ?.replace(Regex("[^0-9+\\-.]"), "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return normalized.toFloatOrNull()
    }

    private data class GainFields(
        val trackGain: Float? = null,
        val albumGain: Float? = null
    )
}

internal fun replayGainMultiplier(gainDb: Float): Float =
    10.0.pow(gainDb / 20.0).toFloat()

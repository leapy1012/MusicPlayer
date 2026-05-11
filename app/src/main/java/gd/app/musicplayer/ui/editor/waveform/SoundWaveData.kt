package gd.app.musicplayer.ui.editor.waveform

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

data class SoundWaveData(
    val sourcePath: String,
    val durationMs: Int,
    val mimeType: String?,
    val sampleTimesMs: IntArray,
    val sampleGains: IntArray
) {
    fun infoText(): String {
        val type = mimeType?.substringAfter('/')?.uppercase().orEmpty().ifBlank { "AUDIO" }
        return "$type - ${TimeEditText.formatTime(durationMs)}"
    }

    companion object {
        fun load(sourcePath: String): SoundWaveData? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(sourcePath)
                val trackIndex = findAudioTrack(extractor) ?: return null
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val durationMs = resolveDurationMs(sourcePath, format)
                val mimeType = format.getString(MediaFormat.KEY_MIME)

                val gains = ArrayList<Int>(2048)
                val times = ArrayList<Int>(2048)
                val buffer = ByteBuffer.allocateDirect(
                    format.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)?.coerceAtMost(512 * 1024)
                        ?: 128 * 1024
                )

                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTimeMs = (extractor.sampleTime / 1000L).coerceAtLeast(0L).toInt()
                    times += sampleTimeMs
                    gains += gainForBuffer(buffer, sampleSize)
                    extractor.advance()
                }

                if (gains.isEmpty()) {
                    gains += 1
                    times += 0
                }

                SoundWaveData(
                    sourcePath = sourcePath,
                    durationMs = durationMs.coerceAtLeast(times.lastOrNull() ?: 0),
                    mimeType = mimeType,
                    sampleTimesMs = times.toIntArray(),
                    sampleGains = gains.toIntArray()
                )
            } catch (_: Exception) {
                null
            } finally {
                runCatching { extractor.release() }
            }
        }

        private fun findAudioTrack(extractor: MediaExtractor): Int? {
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    return index
                }
            }
            return null
        }

        private fun resolveDurationMs(sourcePath: String, format: MediaFormat): Int {
            val formatDurationUs = format.getLongOrNull(MediaFormat.KEY_DURATION)
            if (formatDurationUs != null && formatDurationUs > 0L) {
                return (formatDurationUs / 1000L).toInt()
            }
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(sourcePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull()
                    ?: 0
            } finally {
                runCatching { retriever.release() }
            }
        }

        private fun gainForBuffer(buffer: ByteBuffer, sampleSize: Int): Int {
            if (sampleSize <= 0) return 1
            val step = max(1, sampleSize / 128)
            var gain = 0
            var index = 0
            while (index < sampleSize) {
                gain = max(gain, abs(buffer.get(index).toInt()))
                index += step
            }
            return gain.coerceAtLeast(1)
        }

        private fun MediaFormat.getIntegerOrNull(key: String): Int? =
            if (containsKey(key)) getInteger(key) else null

        private fun MediaFormat.getLongOrNull(key: String): Long? =
            if (containsKey(key)) getLong(key) else null
    }
}

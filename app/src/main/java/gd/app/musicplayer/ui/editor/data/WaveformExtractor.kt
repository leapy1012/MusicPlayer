package gd.app.musicplayer.ui.editor.data

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import gd.app.musicplayer.ui.editor.audio.CheapSoundFile
import gd.app.musicplayer.ui.editor.model.WaveformData
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Builds [WaveformData] for the audio editor.
 *
 * Prefers [CheapSoundFile] (Mp3/Wav/Aac/Extractor implementations). Falls back to
 * [MediaExtractor] for unsupported or unreadable files — no duplicated format parsers.
 */
object WaveformExtractor {

    fun load(sourcePath: String): WaveformData {
        require(sourcePath.isNotBlank()) {
            "Audio source path is empty"
        }

        loadWithCheapSoundFile(sourcePath)?.let { return it }
        return loadWithMediaExtractor(sourcePath)
    }

    private fun loadWithCheapSoundFile(sourcePath: String): WaveformData? {
        val file = File(sourcePath)
        val soundFile = runCatching { CheapSoundFile.create(file) }.getOrNull()
            ?: return null
        val sampleRateHz = soundFile.sampleRateHz
        val samplesPerFrame = soundFile.samplesPerFrame
        val frameCount = soundFile.frameCount
        val frameTimesMs = if (sampleRateHz > 0 && samplesPerFrame > 0) {
            IntArray(frameCount) { index ->
                ((index.toLong() * samplesPerFrame * 1000L) / sampleRateHz).toInt()
            }
        } else {
            IntArray(frameCount)
        }

        return WaveformData(
            sourcePath = sourcePath,
            durationMs = soundFile.durationMs,
            mimeType = soundFile.mimeType,
            sampleRateHz = sampleRateHz,
            bitRate = soundFile.bitRateKbps * 1000,
            samplesPerFrame = samplesPerFrame,
            frameTimesMs = frameTimesMs,
            frameGains = soundFile.frameGains
        )
    }

    private fun loadWithMediaExtractor(sourcePath: String): WaveformData {
        val extractor = MediaExtractor()

        return try {
            extractor.setDataSource(sourcePath)

            val trackIndex = findAudioTrack(extractor)
                ?: error("No audio track found")

            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val durationMs = resolveDurationMs(sourcePath, format)
            val mimeType = format.getString(MediaFormat.KEY_MIME)
            val sampleRateHz = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 0
            val bitRate = format.getIntegerOrNull(MediaFormat.KEY_BIT_RATE) ?: 0
            val channelCount = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1

            val gains = ArrayList<Int>(INITIAL_FRAME_CAPACITY)
            val times = ArrayList<Int>(INITIAL_FRAME_CAPACITY)

            val maxInputSize = format.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceAtMost(MAX_INPUT_BUFFER_SIZE)
                ?: DEFAULT_INPUT_BUFFER_SIZE

            val buffer = ByteBuffer.allocateDirect(maxInputSize)

            while (true) {
                buffer.clear()

                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeMs = (extractor.sampleTime / 1000L)
                    .coerceAtLeast(0L)
                    .toInt()

                times += if (sampleRateHz > 0 && isAacMime(mimeType)) {
                    ((gains.size.toLong() * AAC_SAMPLES_PER_FRAME * 1000L) / sampleRateHz).toInt()
                } else {
                    sampleTimeMs
                }
                gains += if (isAacMime(mimeType)) {
                    gainForMp4AacFrame(buffer, sampleSize)
                } else {
                    gainForEncodedBuffer(buffer, sampleSize)
                }

                extractor.advance()
            }

            if (gains.isEmpty()) {
                gains += 1
                times += 0
            }

            val safeDurationMs = if (sampleRateHz > 0 && isAacMime(mimeType)) {
                ((gains.size.toLong() * AAC_SAMPLES_PER_FRAME * 1000L) / sampleRateHz).toInt()
            } else {
                durationMs.coerceAtLeast(times.lastOrNull() ?: 0)
            }

            WaveformData(
                sourcePath = sourcePath,
                durationMs = safeDurationMs,
                mimeType = mimeType,
                sampleRateHz = sampleRateHz,
                bitRate = if (bitRate > 0) bitRate else bitrateForAac(sampleRateHz, channelCount),
                samplesPerFrame = if (isAacMime(mimeType)) AAC_SAMPLES_PER_FRAME else 0,
                frameTimesMs = times.toIntArray(),
                frameGains = gains.toIntArray()
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
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

        return resolveDurationMs(sourcePath)
    }

    private fun resolveDurationMs(sourcePath: String): Int {
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

    private fun gainForEncodedBuffer(buffer: ByteBuffer, sampleSize: Int): Int {
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

    private fun gainForMp4AacFrame(buffer: ByteBuffer, sampleSize: Int): Int {
        if (sampleSize < 4) return 0
        fun byteAt(index: Int): Int = buffer.get(index).toInt() and 0xFF
        val b0 = byteAt(0)
        val b1 = byteAt(1)
        val b2 = byteAt(2)
        val b3 = byteAt(3)
        return when ((b0 and 0xE0) shr 5) {
            0 -> ((b1 and 0xFE) shr 1) or ((b0 and 0x01) shl 7)
            1 -> {
                val bitStart = if (((b1 and 0x60) shr 5) == 2) {
                    val sectionCount = b1 and 0x0F
                    val scaleFactorMask = (b2 and 0xFE) shr 1
                    val grouping = ((b2 and 0x01) shl 1) or ((b3 and 0x80) shr 7)
                    var start = 25
                    if (grouping == 1) {
                        var zeroBits = 0
                        for (bit in 0 until 7) {
                            if (((1 shl bit) and scaleFactorMask) == 0) zeroBits++
                        }
                        start += sectionCount * (zeroBits + 1)
                    }
                    start
                } else {
                    val sectionCount = ((b1 and 0x0F) shl 2) or ((b2 and 0xC0) shr 6)
                    val grouping = (b2 and 0x18) shr 3
                    21 + if (grouping == 1) sectionCount * 8 else 0
                }
                readBits(buffer, sampleSize, bitStart, 8)
            }
            else -> 0
        }.coerceIn(0, 255)
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    private fun MediaFormat.getLongOrNull(key: String): Long? {
        return if (containsKey(key)) getLong(key) else null
    }

    private fun isAacMime(mimeType: String?): Boolean {
        return mimeType?.contains("aac", ignoreCase = true) == true ||
            mimeType?.contains("mp4a", ignoreCase = true) == true
    }

    private fun readBits(buffer: ByteBuffer, size: Int, bitOffset: Int, bitCount: Int): Int {
        var value = 0
        for (bit in 0 until bitCount) {
            val absoluteBit = bitOffset + bit
            val byteIndex = absoluteBit / 8
            if (byteIndex >= size) return 0
            val bitIndex = 7 - (absoluteBit % 8)
            val bitValue = (buffer.get(byteIndex).toInt() shr bitIndex) and 0x01
            value = (value shl 1) or bitValue
        }
        return value
    }

    private fun bitrateForAac(sampleRateHz: Int, channelCount: Int): Int {
        return if (sampleRateHz > 0 && channelCount > 0) {
            ((sampleRateHz * channelCount) / 4).coerceAtLeast(32_000)
        } else {
            0
        }
    }

    private const val INITIAL_FRAME_CAPACITY = 2048
    private const val DEFAULT_INPUT_BUFFER_SIZE = 128 * 1024
    private const val MAX_INPUT_BUFFER_SIZE = 512 * 1024
    private const val AAC_SAMPLES_PER_FRAME = 1024
}

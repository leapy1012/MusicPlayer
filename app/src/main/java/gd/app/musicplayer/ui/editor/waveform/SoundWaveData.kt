package gd.app.musicplayer.ui.editor.waveform

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

data class SoundWaveData(
    val sourcePath: String,
    val durationMs: Int,
    val mimeType: String?,
    val sampleRateHz: Int,
    val bitRate: Int,
    val sampleTimesMs: IntArray,
    val sampleGains: IntArray
) {
    fun infoText(): String {
        val builder = StringBuilder()
        val type = sourcePath.substringAfterLast('.', missingDelimiterValue = "")
            .uppercase()
            .ifBlank { mimeType?.substringAfter('/')?.uppercase().orEmpty() }
            .ifBlank { "AUDIO" }
        builder.append(type).append(',')
        if (sampleRateHz > 0) {
            builder.append(sampleRateHz).append("Hz,")
        }
        if (bitRate > 0) {
            builder.append(bitRate / 1000).append("kbps,")
        }
        val seconds = (durationMs / 100) / 10f
        if (seconds > 0f) {
            builder.append(seconds).append(" seconds")
        }
        return builder.toString()
    }

    companion object {
        fun load(sourcePath: String): SoundWaveData? {
            if (sourcePath.substringAfterLast('.', "").equals("mp3", ignoreCase = true)) {
                parseMp3(sourcePath)?.let { return it }
            }

            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(sourcePath)
                val trackIndex = findAudioTrack(extractor) ?: return null
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val durationMs = resolveDurationMs(sourcePath, format)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                val sampleRateHz = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 0
                val bitRate = format.getIntegerOrNull(MediaFormat.KEY_BIT_RATE) ?: 0

                val gains = ArrayList<Int>(2048)
                val times = ArrayList<Int>(2048)
                val buffer = ByteBuffer.allocateDirect(
                    format.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                        ?.coerceAtMost(512 * 1024)
                        ?: (128 * 1024)
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
                val compacted = compactFrames(
                    times = times,
                    gains = gains,
                    durationMs = durationMs
                )

                SoundWaveData(
                    sourcePath = sourcePath,
                    durationMs = durationMs.coerceAtLeast(times.lastOrNull() ?: 0),
                    mimeType = mimeType,
                    sampleRateHz = sampleRateHz,
                    bitRate = bitRate,
                    sampleTimesMs = compacted.first,
                    sampleGains = compacted.second
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

        private fun parseMp3(sourcePath: String): SoundWaveData? {
            val bytes = runCatching { File(sourcePath).readBytes() }.getOrNull() ?: return null
            if (bytes.size < MP3_MIN_FRAME_SIZE) return null

            val gains = ArrayList<Int>(4096)
            val times = ArrayList<Int>(4096)
            var sampleRateHz = 0
            var bitRateSum = 0L
            var bitRateCount = 0
            var offset = skipId3v2(bytes)

            while (offset + 4 < bytes.size) {
                val frame = readMp3Frame(bytes, offset)
                if (frame == null) {
                    offset++
                    continue
                }

                if (sampleRateHz == 0) {
                    sampleRateHz = frame.sampleRateHz
                }
                bitRateSum += frame.bitRateKbps
                bitRateCount++
                times += ((gains.size.toLong() * MP3_SAMPLES_PER_FRAME * 1000L) / frame.sampleRateHz)
                    .toInt()
                gains += frame.gain.coerceAtLeast(1)
                offset += frame.frameLength
            }

            if (gains.size < MP3_MIN_VALID_FRAMES || sampleRateHz <= 0) return null

            val parsedDurationMs = ((gains.size.toLong() * MP3_SAMPLES_PER_FRAME * 1000L) / sampleRateHz)
                .toInt()
            val durationMs = max(resolveDurationMs(sourcePath), parsedDurationMs)
            val compacted = compactFrames(
                times = times,
                gains = gains,
                durationMs = durationMs
            )

            return SoundWaveData(
                sourcePath = sourcePath,
                durationMs = durationMs,
                mimeType = "audio/mpeg",
                sampleRateHz = sampleRateHz,
                bitRate = if (bitRateCount > 0) ((bitRateSum / bitRateCount) * 1000L).toInt() else 0,
                sampleTimesMs = compacted.first,
                sampleGains = compacted.second
            )
        }

        private fun skipId3v2(bytes: ByteArray): Int {
            if (bytes.size < 10) return 0
            if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
                return 0
            }
            val size = ((bytes[6].toInt() and 0x7F) shl 21) or
                ((bytes[7].toInt() and 0x7F) shl 14) or
                ((bytes[8].toInt() and 0x7F) shl 7) or
                (bytes[9].toInt() and 0x7F)
            return (10 + size).coerceAtMost(bytes.size)
        }

        private fun readMp3Frame(bytes: ByteArray, offset: Int): Mp3Frame? {
            if (offset + 4 >= bytes.size) return null
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            val b2 = bytes[offset + 2].toInt() and 0xFF
            val b3 = bytes[offset + 3].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

            val versionBits = (b1 shr 3) and 0x03
            val layerBits = (b1 shr 1) and 0x03
            val bitRateIndex = (b2 shr 4) and 0x0F
            val sampleRateIndex = (b2 shr 2) and 0x03
            val padding = (b2 shr 1) and 0x01
            val channelMode = (b3 shr 6) and 0x03

            if (versionBits == 1 || layerBits != MP3_LAYER_III || bitRateIndex == 0 || bitRateIndex == 15 || sampleRateIndex == 3) {
                return null
            }

            val sampleRateHz = when (versionBits) {
                3 -> intArrayOf(44100, 48000, 32000)[sampleRateIndex]
                2 -> intArrayOf(22050, 24000, 16000)[sampleRateIndex]
                else -> intArrayOf(11025, 12000, 8000)[sampleRateIndex]
            }
            val bitRateKbps = if (versionBits == 3) {
                MP3_MPEG1_LAYER3_BITRATES[bitRateIndex]
            } else {
                MP3_MPEG2_LAYER3_BITRATES[bitRateIndex]
            }
            if (sampleRateHz <= 0 || bitRateKbps <= 0) return null

            val frameLength = if (versionBits == 3) {
                (144_000 * bitRateKbps) / sampleRateHz + padding
            } else {
                (72_000 * bitRateKbps) / sampleRateHz + padding
            }
            if (frameLength < MP3_MIN_FRAME_SIZE || offset + frameLength > bytes.size) return null

            val channels = if (channelMode == 3) 1 else 2
            val gain = readMp3GlobalGain(bytes, offset, versionBits == 3, channels)
            return Mp3Frame(
                frameLength = frameLength,
                sampleRateHz = sampleRateHz,
                bitRateKbps = bitRateKbps,
                gain = gain
            )
        }

        private fun readMp3GlobalGain(
            bytes: ByteArray,
            offset: Int,
            isMpeg1: Boolean,
            channels: Int
        ): Int {
            fun byteAt(relativeOffset: Int): Int = bytes[offset + relativeOffset].toInt() and 0xFF
            return runCatching {
                if (isMpeg1) {
                    if (channels == 1) {
                        ((byteAt(10) and 0x01) shl 7) + ((byteAt(11) and 0xFE) shr 1)
                    } else {
                        ((byteAt(17) and 0x7F) shl 1) + ((byteAt(18) and 0x80) shr 7)
                    }
                } else {
                    if (channels == 1) {
                        ((byteAt(9) and 0x03) shl 6) + ((byteAt(10) and 0xFC) shr 2)
                    } else {
                        ((byteAt(9) and 0x7F) shl 1) + ((byteAt(10) and 0x80) shr 7)
                    }
                }
            }.getOrDefault(1)
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

        private fun compactFrames(
            times: List<Int>,
            gains: List<Int>,
            durationMs: Int
        ): Pair<IntArray, IntArray> {
            if (times.size <= TARGET_FRAME_COUNT) {
                return times.toIntArray() to gains.toIntArray()
            }

            val bucketMs = (durationMs.coerceAtLeast(1).toFloat() / TARGET_FRAME_COUNT)
                .coerceAtLeast(MIN_BUCKET_MS.toFloat())
            val outTimes = ArrayList<Int>(TARGET_FRAME_COUNT)
            val outGains = ArrayList<Int>(TARGET_FRAME_COUNT)
            var bucketStartMs = 0f
            var bucketGain = 0

            times.forEachIndexed { index, timeMs ->
                while (timeMs >= bucketStartMs + bucketMs && outTimes.size < TARGET_FRAME_COUNT) {
                    outTimes += bucketStartMs.toInt()
                    outGains += bucketGain.coerceAtLeast(1)
                    bucketStartMs += bucketMs
                    bucketGain = 0
                }
                bucketGain = max(bucketGain, gains[index])
            }

            if (outTimes.isEmpty() || outTimes.last() < durationMs) {
                outTimes += bucketStartMs.toInt().coerceAtMost(durationMs)
                outGains += bucketGain.coerceAtLeast(1)
            }
            return outTimes.toIntArray() to outGains.toIntArray()
        }

        private fun MediaFormat.getIntegerOrNull(key: String): Int? =
            if (containsKey(key)) getInteger(key) else null

        private fun MediaFormat.getLongOrNull(key: String): Long? =
            if (containsKey(key)) getLong(key) else null

        private const val TARGET_FRAME_COUNT = 12_000
        private const val MIN_BUCKET_MS = 20
        private const val MP3_LAYER_III = 1
        private const val MP3_MIN_FRAME_SIZE = 24
        private const val MP3_MIN_VALID_FRAMES = 8
        private const val MP3_SAMPLES_PER_FRAME = 1152
        private val MP3_MPEG1_LAYER3_BITRATES = intArrayOf(
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
        )
        private val MP3_MPEG2_LAYER3_BITRATES = intArrayOf(
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
        )

        private data class Mp3Frame(
            val frameLength: Int,
            val sampleRateHz: Int,
            val bitRateKbps: Int,
            val gain: Int
        )
    }
}

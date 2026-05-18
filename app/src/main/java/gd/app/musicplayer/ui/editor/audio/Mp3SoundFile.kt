package gd.app.musicplayer.ui.editor.audio

import gd.app.musicplayer.core.common.extension.readFully
import gd.app.musicplayer.core.common.extension.skipFully
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class Mp3SoundFile : CheapSoundFile() {

    override val fileType: String = "MP3"
    override val mimeType: String = "audio/mpeg"

    override var frameCount: Int = 0
        private set

    override var sampleRateHz: Int = 0
        private set

    override val samplesPerFrame: Int = MP3_SAMPLES_PER_FRAME

    private var frameOffsets = IntArray(0)
    private var frameLengths = IntArray(0)

    override var frameGains: IntArray = IntArray(0)
        private set

    override var bitRateKbps: Int = 0
        private set

    override fun parse(
        inputStream: InputStream,
        fileSize: Long
    ) {
        if (fileSize < MIN_FILE_SIZE_BYTES) {
            throw IOException("File too small to parse")
        }

        val bytes = inputStream.readBytes()
        var offset = skipId3v2(bytes)

        val offsets = ArrayList<Int>()
        val lengths = ArrayList<Int>()
        val gains = ArrayList<Int>()

        var bitrateSum = 0L
        var bitrateCount = 0

        while (offset + 4 < bytes.size) {
            val frame = readFrame(bytes, offset)

            if (frame == null) {
                offset++
                continue
            }

            if (sampleRateHz == 0) {
                sampleRateHz = frame.sampleRateHz
            }

            bitrateSum += frame.bitRateKbps
            bitrateCount++

            offsets += offset
            lengths += frame.frameLength
            gains += frame.gain.coerceAtLeast(1)

            offset += frame.frameLength
        }

        if (offsets.isEmpty() || sampleRateHz <= 0) {
            throw IOException("Invalid MP3 file")
        }

        frameCount = offsets.size
        frameOffsets = offsets.toIntArray()
        frameLengths = lengths.toIntArray()
        frameGains = gains.toIntArray()
        bitRateKbps = if (bitrateCount > 0) (bitrateSum / bitrateCount).toInt() else 0
    }

    override fun writeFrames(
        inputStream: InputStream,
        outputStream: OutputStream,
        startFrame: Int,
        frameCount: Int
    ) {
        require(startFrame >= 0)
        require(frameCount > 0)

        val safeCount = frameCount.coerceAtMost(this.frameCount - startFrame)
        val maxFrameSize = (0 until safeCount).maxOf { frameLengths[startFrame + it] }

        val buffer = ByteArray(maxFrameSize)

        inputStream.skipFully(frameOffsets[startFrame].toLong())

        var currentOffset = frameOffsets[startFrame]

        for (i in 0 until safeCount) {
            val frameIndex = startFrame + i
            val skip = frameOffsets[frameIndex] - currentOffset

            if (skip > 0) {
                inputStream.skipFully(skip.toLong())
                currentOffset += skip
            }

            val length = frameLengths[frameIndex]
            inputStream.readFully(buffer, 0, length)
            outputStream.write(buffer, 0, length)

            currentOffset += length
        }
    }

    private fun readFrame(
        bytes: ByteArray,
        offset: Int
    ): Mp3Frame? {
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
        val crcSize = if ((b1 and 0x01) == 0) CRC_SIZE_BYTES else 0

        if (versionBits == 1) return null
        if (layerBits != LAYER_III) return null
        if (bitRateIndex == 0 || bitRateIndex == 15) return null
        if (sampleRateIndex == 3) return null

        val sampleRate = when (versionBits) {
            MPEG_VERSION_1 -> MPEG1_SAMPLE_RATES[sampleRateIndex]
            MPEG_VERSION_2 -> MPEG2_SAMPLE_RATES[sampleRateIndex]
            else -> MPEG25_SAMPLE_RATES[sampleRateIndex]
        }

        val bitrate = if (versionBits == MPEG_VERSION_1) {
            MPEG1_LAYER3_BITRATES[bitRateIndex]
        } else {
            MPEG2_LAYER3_BITRATES[bitRateIndex]
        }

        if (sampleRate <= 0 || bitrate <= 0) return null

        val frameLength = if (versionBits == MPEG_VERSION_1) {
            (144_000 * bitrate) / sampleRate + padding
        } else {
            (72_000 * bitrate) / sampleRate + padding
        }

        if (frameLength < MIN_MP3_FRAME_SIZE || offset + frameLength > bytes.size) {
            return null
        }

        val channels = if (channelMode == 3) 1 else 2

        return Mp3Frame(
            frameLength = frameLength,
            sampleRateHz = sampleRate,
            bitRateKbps = bitrate,
            gain = readGlobalGain(
                bytes = bytes,
                offset = offset,
                isMpeg1 = versionBits == MPEG_VERSION_1,
                channels = channels,
                crcSize = crcSize
            )
        )
    }

    private fun readGlobalGain(
        bytes: ByteArray,
        offset: Int,
        isMpeg1: Boolean,
        channels: Int,
        crcSize: Int
    ): Int {
        val sideInfoStartBit = (offset + MP3_HEADER_SIZE_BYTES + crcSize) * BITS_PER_BYTE
        val privateBits = if (isMpeg1) {
            if (channels == 1) 5 else 3
        } else {
            if (channels == 1) 1 else 2
        }
        val scfsiBits = if (isMpeg1) channels * 4 else 0
        val globalGainBit = sideInfoStartBit +
                (if (isMpeg1) 9 else 8) +
                privateBits +
                scfsiBits +
                PART2_3_LENGTH_BITS +
                BIG_VALUES_BITS

        return readBits(bytes, globalGainBit, GLOBAL_GAIN_BITS).coerceAtLeast(1)
    }

    private fun readBits(
        bytes: ByteArray,
        bitOffset: Int,
        bitCount: Int
    ): Int {
        var value = 0
        for (i in 0 until bitCount) {
            val absoluteBit = bitOffset + i
            val byteIndex = absoluteBit / BITS_PER_BYTE
            if (byteIndex !in bytes.indices) return value
            val bitIndex = 7 - (absoluteBit % BITS_PER_BYTE)
            value = (value shl 1) or ((bytes[byteIndex].toInt() shr bitIndex) and 1)
        }
        return value
    }

    private fun skipId3v2(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0

        val hasId3 =
            bytes[0] == 'I'.code.toByte() &&
                    bytes[1] == 'D'.code.toByte() &&
                    bytes[2] == '3'.code.toByte()

        if (!hasId3) return 0

        val size = ((bytes[6].toInt() and 0x7F) shl 21) or
                ((bytes[7].toInt() and 0x7F) shl 14) or
                ((bytes[8].toInt() and 0x7F) shl 7) or
                (bytes[9].toInt() and 0x7F)

        return (10 + size).coerceAtMost(bytes.size)
    }

    private data class Mp3Frame(
        val frameLength: Int,
        val sampleRateHz: Int,
        val bitRateKbps: Int,
        val gain: Int
    )

    private companion object {
        const val MIN_FILE_SIZE_BYTES = 128L
        const val MIN_MP3_FRAME_SIZE = 24
        const val MP3_HEADER_SIZE_BYTES = 4
        const val MP3_SAMPLES_PER_FRAME = 1152
        const val CRC_SIZE_BYTES = 2
        const val BITS_PER_BYTE = 8
        const val PART2_3_LENGTH_BITS = 12
        const val BIG_VALUES_BITS = 9
        const val GLOBAL_GAIN_BITS = 8

        const val MPEG_VERSION_1 = 3
        const val MPEG_VERSION_2 = 2
        const val LAYER_III = 1

        val MPEG1_LAYER3_BITRATES = intArrayOf(
            0, 32, 40, 48, 56, 64, 80, 96,
            112, 128, 160, 192, 224, 256, 320, 0
        )

        val MPEG2_LAYER3_BITRATES = intArrayOf(
            0, 8, 16, 24, 32, 40, 48, 56,
            64, 80, 96, 112, 128, 144, 160, 0
        )

        val MPEG1_SAMPLE_RATES = intArrayOf(44100, 48000, 32000, 0)
        val MPEG2_SAMPLE_RATES = intArrayOf(22050, 24000, 16000, 0)
        val MPEG25_SAMPLE_RATES = intArrayOf(11025, 12000, 8000, 0)
    }
}

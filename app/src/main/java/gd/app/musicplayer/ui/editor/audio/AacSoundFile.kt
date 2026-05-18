package gd.app.musicplayer.ui.editor.audio

import gd.app.musicplayer.core.common.extension.copyExactly
import gd.app.musicplayer.core.common.extension.skipFully
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class AacSoundFile : CheapSoundFile() {

    override val fileType: String = "AAC"
    override val mimeType: String = "audio/x-aac"

    override var frameCount: Int = 0
        private set

    override var sampleRateHz: Int = 0
        private set

    private var channelCount: Int = 0

    override val samplesPerFrame: Int = AAC_SAMPLES_PER_FRAME

    private var frameOffsets = IntArray(0)
    private var frameLengths = IntArray(0)

    override var frameGains: IntArray = IntArray(0)
        private set

    override val bitRateKbps: Int
        get() {
            if (sampleRateHz <= 0 || channelCount <= 0) return 0
            return ((sampleRateHz * channelCount) * 2) / 1024
        }

    override fun parse(
        inputStream: InputStream,
        fileSize: Long
    ) {
        if (fileSize < MIN_FILE_SIZE_BYTES) {
            throw IOException("File too small to parse")
        }

        val allBytes = inputStream.readBytes()

        if (looksLikeMp4(allBytes)) {
            throw IOException("Unknown AAC file format")
        }

        val offsets = ArrayList<Int>()
        val lengths = ArrayList<Int>()
        val gains = ArrayList<Int>()

        var offset = 0

        while (offset + ADTS_HEADER_SIZE <= allBytes.size) {
            val header = readAdtsHeader(allBytes, offset)

            if (header == null) {
                offset++
                continue
            }

            if (sampleRateHz == 0) {
                sampleRateHz = SAMPLE_RATES[header.sampleRateIndex]
                channelCount = header.channelConfig
            }

            val frameLength = header.frameLength
            if (frameLength <= ADTS_HEADER_SIZE || offset + frameLength > allBytes.size) {
                break
            }

            val gainByteIndex = offset + frameLength - 1
            val gain = (allBytes[gainByteIndex].toInt() + 128)
                .coerceIn(0, 255)

            offsets += offset
            lengths += frameLength
            gains += gain

            offset += frameLength
        }

        if (offsets.isEmpty()) {
            throw IOException("Invalid AAC format")
        }

        frameCount = offsets.size
        frameOffsets = offsets.toIntArray()
        frameLengths = lengths.toIntArray()
        frameGains = gains.toIntArray()
    }

    override fun writeFrames(
        inputStream: InputStream,
        outputStream: OutputStream,
        startFrame: Int,
        frameCount: Int
    ) {
        require(startFrame >= 0)
        require(frameCount > 0)
        require(startFrame < this.frameCount)

        val safeCount = frameCount.coerceAtMost(this.frameCount - startFrame)

        inputStream.skipFully(frameOffsets[startFrame].toLong())

        val lastFrame = startFrame + safeCount - 1
        val totalBytes = frameOffsets[lastFrame] -
                frameOffsets[startFrame] +
                frameLengths[lastFrame]

        copyExactly(
            inputStream = inputStream,
            outputStream = outputStream,
            byteCount = totalBytes
        )
    }

    private fun readAdtsHeader(
        bytes: ByteArray,
        offset: Int
    ): AdtsHeader? {
        if (offset + ADTS_HEADER_SIZE > bytes.size) return null

        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        val b4 = bytes[offset + 4].toInt() and 0xFF
        val b5 = bytes[offset + 5].toInt() and 0xFF
        val b6 = bytes[offset + 6].toInt() and 0xFF

        val sync = (b0 shl 4) or ((b1 and 0xF0) shr 4)
        val sampleRateIndex = (b2 and 0x3C) shr 2
        val channelConfig = ((b2 and 0x01) shl 2) or ((b3 and 0xC0) shr 6)
        val frameLength = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 and 0xE0) shr 5)

        if (sync != 0xFFF) return null
        if (sampleRateIndex !in SAMPLE_RATES.indices) return null
        if (channelConfig == 0) return null
        if (frameLength <= ADTS_HEADER_SIZE) return null

        return AdtsHeader(
            sampleRateIndex = sampleRateIndex,
            channelConfig = channelConfig,
            frameLength = frameLength
        )
    }

    private fun looksLikeMp4(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
                bytes[0].toInt() == 0 &&
                bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()
    }

    private data class AdtsHeader(
        val sampleRateIndex: Int,
        val channelConfig: Int,
        val frameLength: Int
    )

    private companion object {
        const val ADTS_HEADER_SIZE = 7
        const val AAC_SAMPLES_PER_FRAME = 1024
        const val MIN_FILE_SIZE_BYTES = 128L

        val SAMPLE_RATES = intArrayOf(
            96000,
            88200,
            64000,
            48000,
            44100,
            32000,
            24000,
            22050,
            16000,
            12000,
            11025,
            8000,
            7350
        )
    }
}
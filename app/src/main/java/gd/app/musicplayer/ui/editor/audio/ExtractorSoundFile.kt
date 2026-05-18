package gd.app.musicplayer.ui.editor.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import gd.app.musicplayer.core.common.extension.readFully
import gd.app.musicplayer.core.common.extension.skipFully
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

class ExtractorSoundFile(
    override val fileType: String,
    override val mimeType: String
) : CheapSoundFile() {

    override var frameCount: Int = 0
        private set

    override var sampleRateHz: Int = 0
        private set

    override var samplesPerFrame: Int = DEFAULT_SAMPLES_PER_FRAME
        private set

    override var bitRateKbps: Int = 0
        private set

    private var frameOffsets = IntArray(0)
    private var frameLengths = IntArray(0)

    override var frameGains: IntArray = IntArray(0)
        private set

    override fun parse(
        inputStream: InputStream,
        fileSize: Long
    ) {
        if (fileType.equals("M4A", ignoreCase = true) || fileType.equals("MP4", ignoreCase = true)) {
            parseMp4(inputStream.readBytes(), fileSize)
            return
        }

        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(sourceFile.absolutePath)

            val trackIndex = findAudioTrack(extractor)
                ?: error("No audio track found")

            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)

            sampleRateHz = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 0
            bitRateKbps = (format.getIntegerOrNull(MediaFormat.KEY_BIT_RATE) ?: 0) / 1000

            val maxInputSize = format.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceAtLeast(DEFAULT_BUFFER_SIZE)
                ?: DEFAULT_BUFFER_SIZE

            val buffer = ByteBuffer.allocateDirect(maxInputSize)

            val offsets = ArrayList<Int>()
            val sizes = ArrayList<Int>()
            val gains = ArrayList<Int>()

            while (true) {
                buffer.clear()

                val offset = extractor.sampleTime
                val size = extractor.readSampleData(buffer, 0)

                if (size < 0) break

                offsets += 0
                sizes += size
                gains += gainForEncodedBuffer(buffer, size)

                extractor.advance()
            }

            frameCount = sizes.size
            frameOffsets = rebuildOffsetsFromSizes(sizes)
            frameLengths = sizes.toIntArray()
            frameGains = gains.toIntArray()
        } finally {
            extractor.release()
        }
    }

    private fun parseMp4(bytes: ByteArray, fileSize: Long) {
        if (bytes.size < MIN_MP4_FILE_SIZE || bytes.readAscii(4, 4) != "ftyp") {
            throw IOException("Unknown file format")
        }

        val state = Mp4ParseState()
        parseMp4Atoms(bytes, 0, bytes.size, state)

        val sampleSizes = state.sampleSizes
            ?: throw IOException("Could not parse MP4 sample sizes")
        val mdatDataOffset = state.mdatDataOffset
        val mdatDataSize = state.mdatDataSize

        if (
            sampleSizes.isEmpty() ||
            mdatDataOffset <= 0 ||
            mdatDataSize <= 0 ||
            mdatDataOffset + mdatDataSize > bytes.size
        ) {
            throw IOException("Didn't find mdat")
        }

        frameCount = sampleSizes.size
        sampleRateHz = state.sampleRateHz.takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE_HZ
        samplesPerFrame = state.samplesPerFrame.takeIf { it > 0 } ?: DEFAULT_SAMPLES_PER_FRAME
        bitRateKbps = if (frameCount > 0 && sampleRateHz > 0 && samplesPerFrame > 0) {
            val durationSeconds = (frameCount * samplesPerFrame / sampleRateHz).coerceAtLeast(1)
            (fileSize / durationSeconds / 1000L).toInt()
        } else {
            0
        }

        frameOffsets = IntArray(frameCount)
        frameLengths = sampleSizes
        frameGains = IntArray(frameCount)

        var sampleOffset = mdatDataOffset
        val mdatEnd = mdatDataOffset + mdatDataSize

        for (index in 0 until frameCount) {
            val sampleSize = sampleSizes[index]
            frameOffsets[index] = sampleOffset
            if (sampleSize < 4 || sampleOffset + sampleSize > mdatEnd) {
                frameGains[index] = 0
            } else {
                frameGains[index] = gainForAacFrame(bytes, sampleOffset, sampleSize)
            }
            sampleOffset += sampleSize.coerceAtLeast(0)
        }
    }

    override fun writeFrames(
        inputStream: InputStream,
        outputStream: OutputStream,
        startFrame: Int,
        frameCount: Int
    ) {
        val safeCount = frameCount.coerceAtMost(this.frameCount - startFrame)
        val maxFrameSize = (0 until safeCount).maxOf { frameLengths[startFrame + it] }
        val buffer = ByteArray(maxFrameSize)

        val startOffset = frameOffsets[startFrame]
        inputStream.skipFully(startOffset.toLong())

        var currentOffset = startOffset

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

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return index
            }
        }

        return null
    }

    private fun gainForEncodedBuffer(
        buffer: ByteBuffer,
        size: Int
    ): Int {
        if (size <= 0) return 1

        val step = max(1, size / 128)
        var gain = 0
        var index = 0

        while (index < size) {
            gain = max(gain, abs(buffer.get(index).toInt()))
            index += step
        }

        return gain.coerceAtLeast(1)
    }

    private fun rebuildOffsetsFromSizes(sizes: List<Int>): IntArray {
        val offsets = IntArray(sizes.size)
        var offset = 0

        sizes.forEachIndexed { index, size ->
            offsets[index] = offset
            offset += size
        }

        return offsets
    }

    private fun parseMp4Atoms(
        bytes: ByteArray,
        start: Int,
        end: Int,
        state: Mp4ParseState
    ) {
        var offset = start
        while (offset + 8 <= end && offset + 8 <= bytes.size) {
            var atomSize = bytes.readBigEndianInt(offset)
            val atomType = bytes.readAscii(offset + 4, 4)
            var headerSize = 8

            if (atomSize == 1 && offset + 16 <= end) {
                atomSize = bytes.readBigEndianLong(offset + 8)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                headerSize = 16
            } else if (atomSize == 0) {
                atomSize = end - offset
            }

            if (atomSize < headerSize || offset + atomSize > end || offset + atomSize > bytes.size) {
                break
            }

            val payloadStart = offset + headerSize
            val payloadEnd = offset + atomSize

            when (atomType) {
                "moov", "trak", "mdia", "minf", "stbl" -> parseMp4Atoms(bytes, payloadStart, payloadEnd, state)
                "mdat" -> {
                    state.mdatDataOffset = payloadStart
                    state.mdatDataSize = payloadEnd - payloadStart
                }
                "stsd" -> parseMp4Stsd(bytes, payloadStart, payloadEnd, state)
                "stsz" -> parseMp4Stsz(bytes, payloadStart, payloadEnd, state)
                "stts" -> parseMp4Stts(bytes, payloadStart, payloadEnd, state)
            }

            offset += atomSize
        }
    }

    private fun parseMp4Stsd(bytes: ByteArray, start: Int, end: Int, state: Mp4ParseState) {
        if (start + 16 > end) return
        val entryCount = bytes.readBigEndianInt(start + 4)
        var offset = start + 8
        repeat(entryCount.coerceAtLeast(0)) {
            if (offset + 8 > end) return
            val entrySize = bytes.readBigEndianInt(offset)
            if (entrySize < 8 || offset + entrySize > end) return
            if (offset + 44 <= end) {
                state.channelCount = bytes.readBigEndianShort(offset + 24)
                state.sampleRateHz = bytes.readBigEndianInt(offset + 32) ushr 16
            }
            offset += entrySize
        }
    }

    private fun parseMp4Stsz(bytes: ByteArray, start: Int, end: Int, state: Mp4ParseState) {
        if (start + 12 > end) return
        val constantSize = bytes.readBigEndianInt(start + 4)
        val sampleCount = bytes.readBigEndianInt(start + 8).coerceAtLeast(0)
        if (constantSize > 0) {
            state.sampleSizes = IntArray(sampleCount) { constantSize }
            return
        }
        if (sampleCount > (end - start - 12) / 4) return
        state.sampleSizes = IntArray(sampleCount) { index ->
            bytes.readBigEndianInt(start + 12 + (index * 4))
        }
    }

    private fun parseMp4Stts(bytes: ByteArray, start: Int, end: Int, state: Mp4ParseState) {
        if (start + 16 > end) return
        val entryCount = bytes.readBigEndianInt(start + 4)
        if (entryCount <= 0) return
        val firstSampleDelta = bytes.readBigEndianInt(start + 12)
        if (firstSampleDelta > 0) {
            state.samplesPerFrame = firstSampleDelta
        }
    }

    private fun gainForAacFrame(bytes: ByteArray, offset: Int, sampleSize: Int): Int {
        if (sampleSize < 4 || offset < 0 || offset + sampleSize > bytes.size) return 0

        fun byteAt(index: Int): Int = bytes[offset + index].toInt() and 0xFF

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
                readBits(bytes, offset, sampleSize, bitStart, 8)
            }
            else -> 0
        }.coerceIn(0, 255)
    }

    private fun readBits(
        bytes: ByteArray,
        offset: Int,
        size: Int,
        bitOffset: Int,
        bitCount: Int
    ): Int {
        var value = 0
        for (i in 0 until bitCount) {
            val absoluteBit = bitOffset + i
            val byteIndex = absoluteBit / 8
            if (byteIndex >= size) return value
            val bitIndex = 7 - (absoluteBit % 8)
            value = (value shl 1) or ((bytes[offset + byteIndex].toInt() shr bitIndex) and 1)
        }
        return value
    }

    private fun ByteArray.readAscii(offset: Int, length: Int): String {
        if (offset < 0 || offset + length > size) return ""
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun ByteArray.readBigEndianShort(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 8) or
            (this[offset + 1].toInt() and 0xFF)
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.readBigEndianLong(offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (this[offset + index].toLong() and 0xFFL)
        }
        return value
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 256 * 1024
        const val DEFAULT_SAMPLES_PER_FRAME = 1024
        const val DEFAULT_SAMPLE_RATE_HZ = 44100
        const val MIN_MP4_FILE_SIZE = 128
    }

    private class Mp4ParseState {
        var sampleSizes: IntArray? = null
        var sampleRateHz: Int = 0
        var channelCount: Int = 0
        var samplesPerFrame: Int = 0
        var mdatDataOffset: Int = -1
        var mdatDataSize: Int = 0
    }
}

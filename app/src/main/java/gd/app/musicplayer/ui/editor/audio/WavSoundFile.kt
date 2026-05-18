package gd.app.musicplayer.ui.editor.audio

import gd.app.musicplayer.core.common.extension.readExact
import gd.app.musicplayer.core.common.extension.readFully
import gd.app.musicplayer.core.common.extension.readLittleEndianInt
import gd.app.musicplayer.core.common.extension.readLittleEndianShort
import gd.app.musicplayer.core.common.extension.skipFully
import gd.app.musicplayer.core.common.extension.writeAscii
import gd.app.musicplayer.core.common.extension.writeLittleEndianInt
import gd.app.musicplayer.core.common.extension.writeLittleEndianShort
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

class WavSoundFile : CheapSoundFile() {

    override val fileType: String = "WAV"
    override val mimeType: String = "audio/x-wav"

    override var frameCount: Int = 0
        private set

    override var sampleRateHz: Int = 0
        private set

    private var channelCount: Int = 0
    private var bytesPerFrame: Int = 0

    override val samplesPerFrame: Int
        get() = if (sampleRateHz > 0) sampleRateHz / FRAMES_PER_SECOND else 0

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

        var bytesRead = 0

        val riffHeader = inputStream.readExact(12)
        bytesRead += 12

        val isWave =
            riffHeader[0] == 'R'.code.toByte() &&
                    riffHeader[1] == 'I'.code.toByte() &&
                    riffHeader[2] == 'F'.code.toByte() &&
                    riffHeader[3] == 'F'.code.toByte() &&
                    riffHeader[8] == 'W'.code.toByte() &&
                    riffHeader[9] == 'A'.code.toByte() &&
                    riffHeader[10] == 'V'.code.toByte() &&
                    riffHeader[11] == 'E'.code.toByte()

        if (!isWave) {
            throw IOException("Not a WAV file")
        }

        while (bytesRead + 8 <= fileSize) {
            val chunkHeader = inputStream.readExact(8)
            bytesRead += 8

            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = chunkHeader.readLittleEndianInt(4)

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16 || chunkSize > 1024) {
                        throw IOException("WAV file has bad fmt chunk")
                    }

                    val fmt = inputStream.readExact(chunkSize)
                    bytesRead += chunkSize

                    channelCount = fmt.readLittleEndianShort(2)
                    sampleRateHz = fmt.readLittleEndianInt(4)
                }

                "data" -> {
                    if (channelCount == 0 || sampleRateHz == 0) {
                        throw IOException("Bad WAV file: data chunk before fmt chunk")
                    }

                    parseDataChunk(
                        inputStream = inputStream,
                        dataOffset = bytesRead,
                        dataSize = chunkSize
                    )

                    return
                }

                else -> {
                    inputStream.skipFully(chunkSize.toLong())
                    bytesRead += chunkSize
                }
            }
        }

        throw IOException("WAV data chunk not found")
    }

    private fun parseDataChunk(
        inputStream: InputStream,
        dataOffset: Int,
        dataSize: Int
    ) {
        bytesPerFrame = ((sampleRateHz * channelCount) / FRAMES_PER_SECOND) * BYTES_PER_SAMPLE
        if (bytesPerFrame <= 0) {
            throw IOException("Invalid WAV frame size")
        }

        frameCount = ((dataSize + bytesPerFrame - 1) / bytesPerFrame)

        frameOffsets = IntArray(frameCount)
        frameLengths = IntArray(frameCount)
        frameGains = IntArray(frameCount)

        val buffer = ByteArray(bytesPerFrame)

        var consumed = 0
        var frameIndex = 0

        while (consumed < dataSize && frameIndex < frameCount) {
            val remaining = dataSize - consumed
            val bytesToRead = bytesPerFrame.coerceAtMost(remaining)

            val read = inputStream.read(buffer, 0, bytesToRead)
            if (read <= 0) break

            var maxGain = 0
            var i = 1

            while (i < read) {
                maxGain = maxOf(maxGain, abs(buffer[i].toInt()))
                i += channelCount * BYTES_PER_SAMPLE
            }

            frameOffsets[frameIndex] = dataOffset + consumed
            frameLengths[frameIndex] = read
            frameGains[frameIndex] = maxGain

            consumed += read
            frameIndex++
        }

        if (frameIndex != frameCount) {
            frameCount = frameIndex
            frameOffsets = frameOffsets.copyOf(frameIndex)
            frameLengths = frameLengths.copyOf(frameIndex)
            frameGains = frameGains.copyOf(frameIndex)
        }
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
        val dataSize = (0 until safeCount).sumOf { index ->
            frameLengths[startFrame + index].toLong()
        }

        writeWavHeader(
            outputStream = outputStream,
            dataSize = dataSize
        )

        inputStream.skipFully(frameOffsets[startFrame].toLong())

        val buffer = ByteArray(bytesPerFrame)
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

    private fun writeWavHeader(
        outputStream: OutputStream,
        dataSize: Long
    ) {
        val riffSize = 36L + dataSize
        val byteRate = sampleRateHz * channelCount * BYTES_PER_SAMPLE
        val blockAlign = channelCount * BYTES_PER_SAMPLE

        outputStream.writeAscii("RIFF")
        outputStream.writeLittleEndianInt(riffSize.toInt())
        outputStream.writeAscii("WAVE")
        outputStream.writeAscii("fmt ")
        outputStream.writeLittleEndianInt(16)
        outputStream.writeLittleEndianShort(1)
        outputStream.writeLittleEndianShort(channelCount)
        outputStream.writeLittleEndianInt(sampleRateHz)
        outputStream.writeLittleEndianInt(byteRate)
        outputStream.writeLittleEndianShort(blockAlign)
        outputStream.writeLittleEndianShort(16)
        outputStream.writeAscii("data")
        outputStream.writeLittleEndianInt(dataSize.toInt())
    }

    private companion object {
        const val MIN_FILE_SIZE_BYTES = 128L
        const val FRAMES_PER_SECOND = 50
        const val BYTES_PER_SAMPLE = 2
    }
}
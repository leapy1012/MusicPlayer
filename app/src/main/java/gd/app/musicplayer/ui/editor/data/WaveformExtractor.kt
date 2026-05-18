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

object WaveformExtractor {

    fun load(sourcePath: String): WaveformData {
        require(sourcePath.isNotBlank()) {
            "Audio source path is empty"
        }

        loadWithCheapSoundFile(sourcePath)?.let { return it }

        if (sourcePath.substringAfterLast('.', "").equals("mp3", ignoreCase = true)) {
            parseMp3(sourcePath)?.let { return it }
        }
        if (sourcePath.substringAfterLast('.', "").equals("wav", ignoreCase = true)) {
            parseWav(sourcePath)?.let { return it }
        }
        if (sourcePath.substringAfterLast('.', "").lowercase() in setOf("m4a", "mp4")) {
            parseMp4Aac(sourcePath)?.let { return it }
        }
        if (sourcePath.substringAfterLast('.', "").equals("aac", ignoreCase = true)) {
            parseAdtsAac(sourcePath)?.let { return it }
        }

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

    private fun parseMp3(sourcePath: String): WaveformData? {
        val bytes = runCatching { File(sourcePath).readBytes() }.getOrNull()
            ?: return null

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

        if (gains.size < MP3_MIN_VALID_FRAMES || sampleRateHz <= 0) {
            return null
        }

        val parsedDurationMs = ((gains.size.toLong() * MP3_SAMPLES_PER_FRAME * 1000L) / sampleRateHz)
            .toInt()

            return WaveformData(
                sourcePath = sourcePath,
                durationMs = parsedDurationMs,
                mimeType = "audio/mpeg",
                sampleRateHz = sampleRateHz,
                bitRate = if (bitRateCount > 0) {
                ((bitRateSum / bitRateCount) * 1000L).toInt()
                } else {
                    0
                },
                samplesPerFrame = MP3_SAMPLES_PER_FRAME,
                frameTimesMs = times.toIntArray(),
                frameGains = gains.toIntArray()
            )
        }

    private fun parseAdtsAac(sourcePath: String): WaveformData? {
        val bytes = runCatching { File(sourcePath).readBytes() }.getOrNull() ?: return null
        val gains = ArrayList<Int>(INITIAL_FRAME_CAPACITY)
        val times = ArrayList<Int>(INITIAL_FRAME_CAPACITY)
        var offset = 0
        var sampleRateHz = 0
        var channelCount = 0

        while (offset + 7 <= bytes.size) {
            if ((bytes[offset].toInt() and 0xFF) != 0xFF ||
                (bytes[offset + 1].toInt() and 0xF0) != 0xF0
            ) {
                offset++
                continue
            }
            val sampleRateIndex = (bytes[offset + 2].toInt() and 0x3C) shr 2
            if (sampleRateIndex !in AAC_SAMPLE_RATES.indices) return null
            val frameLength = ((bytes[offset + 3].toInt() and 0x03) shl 11) or
                ((bytes[offset + 4].toInt() and 0xFF) shl 3) or
                ((bytes[offset + 5].toInt() and 0xE0) shr 5)
            if (frameLength <= 7 || offset + frameLength > bytes.size) break

            if (sampleRateHz == 0) {
                sampleRateHz = AAC_SAMPLE_RATES[sampleRateIndex]
                channelCount = ((bytes[offset + 2].toInt() and 0x01) shl 2) or
                    ((bytes[offset + 3].toInt() and 0xC0) shr 6)
            }
            times += ((gains.size.toLong() * AAC_SAMPLES_PER_FRAME * 1000L) / sampleRateHz).toInt()
            gains += (bytes[offset + frameLength - 1].toInt() + 128).coerceIn(0, 255)
            offset += frameLength
        }

        if (gains.isEmpty() || sampleRateHz <= 0) return null
        return WaveformData(
            sourcePath = sourcePath,
            durationMs = ((gains.size.toLong() * AAC_SAMPLES_PER_FRAME * 1000L) / sampleRateHz).toInt(),
            mimeType = "audio/x-aac",
            sampleRateHz = sampleRateHz,
            bitRate = bitrateForAac(sampleRateHz, channelCount),
            samplesPerFrame = AAC_SAMPLES_PER_FRAME,
            frameTimesMs = times.toIntArray(),
            frameGains = gains.toIntArray()
        )
    }

    private fun parseMp4Aac(sourcePath: String): WaveformData? {
        val bytes = runCatching { File(sourcePath).readBytes() }.getOrNull() ?: return null
        if (bytes.size < 128 || bytes.readAscii(4, 4) != "ftyp") return null

        val state = Mp4ParseState()
        parseMp4Atoms(bytes, 0, bytes.size, state)
        val sampleSizes = state.sampleSizes ?: return null
        val mdatStart = state.mdatDataOffset
        val mdatSize = state.mdatDataSize
        if (sampleSizes.isEmpty() || mdatStart <= 0 || mdatSize <= 0 || mdatStart + mdatSize > bytes.size) {
            return null
        }

        val sampleRateHz = state.sampleRateHz.takeIf { it > 0 } ?: 44100
        val samplesPerFrame = state.samplesPerFrame.takeIf { it > 0 } ?: AAC_SAMPLES_PER_FRAME
        val gains = IntArray(sampleSizes.size)
        val times = IntArray(sampleSizes.size)
        var sampleOffset = mdatStart
        val mdatEnd = mdatStart + mdatSize

        for (index in sampleSizes.indices) {
            val sampleSize = sampleSizes[index]
            if (sampleSize <= 0 || sampleOffset + sampleSize > mdatEnd) {
                gains[index] = 0
            } else {
                gains[index] = gainForAacFrame(bytes, sampleOffset, sampleSize)
            }
            times[index] = ((index.toLong() * samplesPerFrame * 1000L) / sampleRateHz).toInt()
            sampleOffset += sampleSize.coerceAtLeast(0)
        }

        return WaveformData(
            sourcePath = sourcePath,
            durationMs = ((sampleSizes.size.toLong() * samplesPerFrame * 1000L) / sampleRateHz).toInt(),
            mimeType = "audio/mp4",
            sampleRateHz = sampleRateHz,
            bitRate = if (state.fileSize > 0 && sampleSizes.isNotEmpty()) {
                state.fileSize / (sampleSizes.size * samplesPerFrame / sampleRateHz.coerceAtLeast(1)).coerceAtLeast(1)
            } else {
                bitrateForAac(sampleRateHz, state.channelCount)
            },
            samplesPerFrame = samplesPerFrame,
            frameTimesMs = times,
            frameGains = gains
        )
    }

    private fun parseWav(sourcePath: String): WaveformData? {
        val bytes = runCatching { File(sourcePath).readBytes() }.getOrNull() ?: return null
        if (bytes.size < 44 ||
            bytes.readAscii(0, 4) != "RIFF" ||
            bytes.readAscii(8, 4) != "WAVE"
        ) {
            return null
        }

        var offset = 12
        var channelCount = 0
        var sampleRateHz = 0
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.readAscii(offset, 4)
            val chunkSize = bytes.readLittleEndianInt(offset + 4)
            val payloadOffset = offset + 8
            if (payloadOffset + chunkSize > bytes.size) break
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16) {
                        channelCount = bytes.readLittleEndianShort(payloadOffset + 2)
                        sampleRateHz = bytes.readLittleEndianInt(payloadOffset + 4)
                    }
                }
                "data" -> {
                    dataOffset = payloadOffset
                    dataSize = chunkSize
                    break
                }
            }
            offset = payloadOffset + chunkSize + (chunkSize and 1)
        }

        if (dataOffset < 0 || dataSize <= 0 || channelCount <= 0 || sampleRateHz <= 0) return null
        val frameByteCount = (((sampleRateHz * channelCount) / 50) * 2).coerceAtLeast(2)
        val frameCount = ((dataSize - 1) + frameByteCount) / frameByteCount
        val samplesPerFrame = (sampleRateHz / 50).coerceAtLeast(1)
        val gains = IntArray(frameCount)
        val times = IntArray(frameCount)

        for (frame in 0 until frameCount) {
            val frameOffset = dataOffset + (frame * frameByteCount)
            val frameEnd = minOf(dataOffset + dataSize, frameOffset + frameByteCount)
            var gain = 0
            var sampleOffset = frameOffset + 1
            val step = channelCount * 4
            while (sampleOffset < frameEnd) {
                gain = max(gain, abs(bytes[sampleOffset].toInt()))
                sampleOffset += step
            }
            gains[frame] = gain
            times[frame] = ((frame.toLong() * samplesPerFrame * 1000L) / sampleRateHz).toInt()
        }

        return WaveformData(
            sourcePath = sourcePath,
            durationMs = ((frameCount.toLong() * samplesPerFrame * 1000L) / sampleRateHz).toInt(),
            mimeType = "audio/x-wav",
            sampleRateHz = sampleRateHz,
            bitRate = bitrateForPcm(sampleRateHz, channelCount),
            samplesPerFrame = samplesPerFrame,
            frameTimesMs = times,
            frameGains = gains
        )
    }

    private fun skipId3v2(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0

        val hasId3Header =
            bytes[0] == 'I'.code.toByte() &&
                    bytes[1] == 'D'.code.toByte() &&
                    bytes[2] == '3'.code.toByte()

        if (!hasId3Header) return 0

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
        val crcSize = if ((b1 and 0x01) == 0) MP3_CRC_SIZE_BYTES else 0

        if (
            versionBits == 1 ||
            layerBits != MP3_LAYER_III ||
            bitRateIndex == 0 ||
            bitRateIndex == 15 ||
            sampleRateIndex == 3
        ) {
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

        if (frameLength < MP3_MIN_FRAME_SIZE || offset + frameLength > bytes.size) {
            return null
        }

        val channels = if (channelMode == 3) 1 else 2

        return Mp3Frame(
            frameLength = frameLength,
            sampleRateHz = sampleRateHz,
            bitRateKbps = bitRateKbps,
            gain = readMp3GlobalGain(
                bytes = bytes,
                offset = offset,
                isMpeg1 = versionBits == 3,
                channels = channels,
                crcSize = crcSize
            )
        )
    }

    private fun readMp3GlobalGain(
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
                MP3_PART2_3_LENGTH_BITS +
                MP3_BIG_VALUES_BITS

        return readBits(bytes, globalGainBit, MP3_GLOBAL_GAIN_BITS).coerceAtLeast(1)
    }

    private fun readBits(bytes: ByteArray, bitOffset: Int, bitCount: Int): Int {
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

    /**
     * This is only an approximation for non-MP3 encoded samples.
     *
     * For production-perfect waveform quality on AAC/M4A/FLAC/OGG,
     * replace this with MediaCodec PCM decoding.
     */
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
        for (i in 0 until bitCount) {
            val absoluteBit = bitOffset + i
            val byteIndex = absoluteBit / 8
            if (byteIndex >= size) return value
            val bitIndex = 7 - (absoluteBit % 8)
            value = (value shl 1) or ((buffer.get(byteIndex).toInt() shr bitIndex) and 1)
        }
        return value
    }

    private fun readBits(bytes: ByteArray, offset: Int, size: Int, bitOffset: Int, bitCount: Int): Int {
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
                val largeSize = bytes.readBigEndianLong(offset + 8)
                atomSize = largeSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                headerSize = 16
            } else if (atomSize == 0) {
                atomSize = end - offset
            }
            if (atomSize < headerSize || offset + atomSize > end || offset + atomSize > bytes.size) break

            val payloadStart = offset + headerSize
            val payloadEnd = offset + atomSize
            when (atomType) {
                "moov", "trak", "mdia", "minf", "stbl" -> parseMp4Atoms(bytes, payloadStart, payloadEnd, state)
                "mdat" -> {
                    state.mdatDataOffset = payloadStart
                    state.mdatDataSize = payloadEnd - payloadStart
                    state.fileSize = bytes.size
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
                state.sampleRateHz = bytes.readBigEndianInt(offset + 32) shr 16
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
        if (start + 12 + (sampleCount * 4) > end) return
        state.sampleSizes = IntArray(sampleCount) { index ->
            bytes.readBigEndianInt(start + 12 + (index * 4))
        }
    }

    private fun parseMp4Stts(bytes: ByteArray, start: Int, end: Int, state: Mp4ParseState) {
        if (start + 16 > end) return
        val entryCount = bytes.readBigEndianInt(start + 4)
        if (entryCount <= 0) return
        val firstSampleDelta = bytes.readBigEndianInt(start + 12)
        if (firstSampleDelta > 0) state.samplesPerFrame = firstSampleDelta
    }

    private fun ByteArray.readAscii(offset: Int, length: Int): String {
        if (offset < 0 || offset + length > size) return ""
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun ByteArray.readLittleEndianShort(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readLittleEndianInt(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
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

    private fun bitrateForAac(sampleRateHz: Int, channelCount: Int): Int {
        return if (sampleRateHz > 0 && channelCount > 0) {
            ((sampleRateHz * channelCount * 2) / 1024) * 1000
        } else {
            0
        }
    }

    private fun bitrateForPcm(sampleRateHz: Int, channelCount: Int): Int {
        return if (sampleRateHz > 0 && channelCount > 0) {
            ((sampleRateHz * channelCount * 2) / 1024) * 1000
        } else {
            0
        }
    }

    private data class Mp3Frame(
        val frameLength: Int,
        val sampleRateHz: Int,
        val bitRateKbps: Int,
        val gain: Int
    )

    private class Mp4ParseState {
        var sampleSizes: IntArray? = null
        var sampleRateHz: Int = 0
        var channelCount: Int = 0
        var samplesPerFrame: Int = 0
        var mdatDataOffset: Int = -1
        var mdatDataSize: Int = 0
        var fileSize: Int = 0
    }

    private const val INITIAL_FRAME_CAPACITY = 2048
    private const val DEFAULT_INPUT_BUFFER_SIZE = 128 * 1024
    private const val MAX_INPUT_BUFFER_SIZE = 512 * 1024

    private const val MP3_LAYER_III = 1
    private const val MP3_MIN_FRAME_SIZE = 24
    private const val MP3_MIN_VALID_FRAMES = 8
    private const val MP3_HEADER_SIZE_BYTES = 4
    private const val MP3_SAMPLES_PER_FRAME = 1152
    private const val MP3_CRC_SIZE_BYTES = 2
    private const val BITS_PER_BYTE = 8
    private const val MP3_PART2_3_LENGTH_BITS = 12
    private const val MP3_BIG_VALUES_BITS = 9
    private const val MP3_GLOBAL_GAIN_BITS = 8
    private const val AAC_SAMPLES_PER_FRAME = 1024
    private val AAC_SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000,
        22050, 16000, 12000, 11025, 8000, 7350
    )

    private val MP3_MPEG1_LAYER3_BITRATES = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96,
        112, 128, 160, 192, 224, 256, 320, 0
    )

    private val MP3_MPEG2_LAYER3_BITRATES = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56,
        64, 80, 96, 112, 128, 144, 160, 0
    )
}

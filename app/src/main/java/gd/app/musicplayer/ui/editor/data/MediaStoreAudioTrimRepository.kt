package gd.app.musicplayer.ui.editor.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.ui.editor.audio.CheapSoundFile
import gd.app.musicplayer.ui.editor.model.AudioClipRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

class MediaStoreAudioTrimRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioTrimRepository {

    override suspend fun exportClip(
        sourcePath: String,
        displayNameWithoutExtension: String,
        range: AudioClipRange
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(sourcePath.isNotBlank()) {
                    "Audio source path is empty"
                }

                require(range.isValid()) {
                    "Invalid clip range: $range"
                }

                val exportSpec = resolveExportSpec(sourcePath)
                val tempFile = File.createTempFile("audio_trim_", ".${exportSpec.extension}", context.cacheDir)

                try {
                    val exportedSpec = trimToTempFile(
                        sourcePath = sourcePath,
                        outputFile = tempFile,
                        range = range,
                        exportSpec = exportSpec
                    )

                    insertIntoMediaStore(
                        tempFile = tempFile,
                        displayNameWithoutExtension = displayNameWithoutExtension,
                        extension = exportedSpec.extension,
                        mimeType = exportedSpec.mimeType
                    )
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    private fun resolveExportSpec(sourcePath: String): ExportSpec {
        val extension = File(sourcePath).extension.lowercase()
        return if (extension in FRAME_WRITER_EXTENSIONS) {
            ExportSpec(
                extension = extension,
                mimeType = MIME_TYPES_BY_EXTENSION[extension] ?: OUTPUT_MIME,
                useFrameWriter = true
            )
        } else {
            ExportSpec(
                extension = OUTPUT_EXTENSION,
                mimeType = OUTPUT_MIME,
                useFrameWriter = false
            )
        }
    }

    private fun trimToTempFile(
        sourcePath: String,
        outputFile: File,
        range: AudioClipRange,
        exportSpec: ExportSpec
    ): ExportSpec {
        if (exportSpec.useFrameWriter) {
            return trimWithCheapSoundFile(
                sourcePath = sourcePath,
                outputFile = outputFile,
                range = range,
                exportSpec = exportSpec
            )
        }

        trimWithMediaMuxer(
            sourcePath = sourcePath,
            outputFile = outputFile,
            range = range
        )
        return exportSpec
    }

    private fun trimWithCheapSoundFile(
        sourcePath: String,
        outputFile: File,
        range: AudioClipRange,
        exportSpec: ExportSpec
    ): ExportSpec {
        val soundFile = CheapSoundFile.create(File(sourcePath))
            ?: error("Unsupported audio source")

        val startFrame = range.startFrame
            ?: range.startMs.toFrameIndex(soundFile)
        val endFrame = range.endFrame
            ?: range.endMs.toFrameIndex(soundFile)

        val safeStartFrame = startFrame.coerceIn(0, (soundFile.frameCount - 1).coerceAtLeast(0))
        val safeEndFrame = endFrame.coerceIn(safeStartFrame + 1, soundFile.frameCount)
        val frameCount = (safeEndFrame - safeStartFrame).coerceAtLeast(1)

        outputFile.outputStream().use { output ->
            soundFile.exportFrames(
                outputStream = output,
                startFrame = safeStartFrame,
                frameCount = frameCount
            )
        }

        return exportSpec.copy(
            mimeType = soundFile.mimeType
        )
    }

    private fun Int.toFrameIndex(soundFile: CheapSoundFile): Int {
        val samplesPerFrame = soundFile.samplesPerFrame
        val sampleRateHz = soundFile.sampleRateHz
        if (samplesPerFrame <= 0 || sampleRateHz <= 0) return 0

        return ((this.toLong() * sampleRateHz) / (samplesPerFrame * 1000L))
            .toInt()
            .coerceIn(0, soundFile.frameCount)
    }

    private fun trimWithMediaMuxer(
        sourcePath: String,
        outputFile: File,
        range: AudioClipRange
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(sourcePath)

            val trackIndex = findAudioTrack(extractor)
                ?: error("No audio track found")

            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)

            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val outputTrackIndex = muxer.addTrack(inputFormat)
            muxer.start()
            muxerStarted = true

            val startUs = range.startMs * 1000L
            val endUs = range.endMs * 1000L

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val maxInputSize = inputFormat.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceAtLeast(DEFAULT_BUFFER_SIZE)
                ?: DEFAULT_BUFFER_SIZE

            val buffer = ByteBuffer.allocateDirect(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var firstSampleTimeUs = -1L
            var wroteSample = false

            while (true) {
                buffer.clear()

                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (firstSampleTimeUs < 0L) {
                        firstSampleTimeUs = sampleTimeUs
                    }

                    bufferInfo.set(
                        0,
                        sampleSize,
                        sampleTimeUs - firstSampleTimeUs,
                        extractor.sampleFlags
                    )

                    muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                    wroteSample = true
                }

                extractor.advance()
            }

            check(wroteSample) {
                "No audio samples found in selected range"
            }
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }

            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun insertIntoMediaStore(
        tempFile: File,
        displayNameWithoutExtension: String,
        extension: String,
        mimeType: String
    ): Long {
        val resolver = context.contentResolver

        val safeTitle = displayNameWithoutExtension
            .trim()
            .ifBlank { "Audio Clip" }

        val fileName = "$safeTitle.$extension"

        if (audioDisplayNameExists(fileName)) {
            throw AudioTrimNameExistsException(fileName)
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, safeTitle)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MusicPlayer")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "MusicPlayer"
                )
                directory.mkdirs()
                put(MediaStore.Audio.Media.DATA, File(directory, fileName).absolutePath)
            }
        }

        val uri = resolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Unable to create MediaStore entry")

        resolver.openOutputStream(uri, "w")?.use { output ->
            tempFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: error("Unable to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                },
                null,
                null
            )
        }

        return ContentUris.parseId(uri)
    }

    private fun audioDisplayNameExists(fileName: String): Boolean {
        val resolver = context.contentResolver
        val selection = "${MediaColumns.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
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

    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    private companion object {
        const val OUTPUT_EXTENSION = "m4a"
        const val OUTPUT_MIME = "audio/mp4"
        const val DEFAULT_BUFFER_SIZE = 256 * 1024

        val FRAME_WRITER_EXTENSIONS = setOf("mp3", "wav", "aac")
        val MIME_TYPES_BY_EXTENSION = mapOf(
            "mp3" to "audio/mpeg",
            "wav" to "audio/x-wav",
            "aac" to "audio/x-aac"
        )
    }

    private data class ExportSpec(
        val extension: String,
        val mimeType: String,
        val useFrameWriter: Boolean
    )
}

class AudioTrimNameExistsException(fileName: String) : IllegalStateException(fileName)

package gd.app.musicplayer.feature.editor

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
import java.io.File
import java.nio.ByteBuffer

object AudioTrimStore {
    private const val OUTPUT_MIME = "audio/mp4"

    fun exportClip(
        context: Context,
        sourcePath: String,
        displayNameWithoutExtension: String,
        startMs: Int,
        endMs: Int
    ): Long? {
        val tempFile = File.createTempFile("trim_", ".m4a", context.cacheDir)
        return try {
            trimToTempFile(sourcePath, tempFile, startMs, endMs) ?: return null
            insertIntoMediaStore(context, tempFile, displayNameWithoutExtension)
        } finally {
            tempFile.delete()
        }
    }

    private fun insertIntoMediaStore(
        context: Context,
        tempFile: File,
        displayNameWithoutExtension: String
    ): Long? {
        val resolver = context.contentResolver
        val fileName = displayNameWithoutExtension.trim().ifBlank { "Clip" } + ".m4a"
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, displayNameWithoutExtension)
            put(MediaStore.Audio.Media.MIME_TYPE, OUTPUT_MIME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MusicPlayer")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "MusicPlayer"
                )
                dir.mkdirs()
                put(MediaStore.Audio.Media.DATA, File(dir, fileName).absolutePath)
            }
        }) ?: return null

        resolver.openOutputStream(uri, "w")?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
        }

        return runCatching { ContentUris.parseId(uri) }.getOrNull()
    }

    private fun trimToTempFile(
        sourcePath: String,
        outputFile: File,
        startMs: Int,
        endMs: Int
    ): File? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(sourcePath)
            val trackIndex = findAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(inputFormat)
            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val maxInputSize = inputFormat.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceAtLeast(64 * 1024)
                ?: 256 * 1024
            val buffer = ByteBuffer.allocateDirect(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var firstPresentationTimeUs = -1L

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (firstPresentationTimeUs < 0L) {
                        firstPresentationTimeUs = sampleTimeUs
                    }
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = sampleTimeUs - firstPresentationTimeUs
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                }
                extractor.advance()
            }

            if (firstPresentationTimeUs < 0L) null else outputFile
        } catch (_: Exception) {
            null
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return index
        }
        return null
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null
}

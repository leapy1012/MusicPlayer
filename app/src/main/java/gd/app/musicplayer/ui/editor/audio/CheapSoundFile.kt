package gd.app.musicplayer.ui.editor.audio

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

abstract class CheapSoundFile {

    protected lateinit var sourceFile: File

    abstract val fileType: String
    abstract val mimeType: String
    abstract val frameCount: Int
    abstract val sampleRateHz: Int
    abstract val samplesPerFrame: Int
    abstract val frameGains: IntArray
    abstract val bitRateKbps: Int

    val durationMs: Int
        get() {
            if (sampleRateHz <= 0 || samplesPerFrame <= 0 || frameCount <= 0) return 0
            return ((samplesPerFrame * 1000f / sampleRateHz) * frameCount).toInt()
        }

    fun infoText(): String {
        return buildString {
            append(fileType.uppercase())
            append(",")

            if (sampleRateHz > 0) {
                append(sampleRateHz)
                append("Hz,")
            }

            if (bitRateKbps > 0) {
                append(bitRateKbps)
                append("kbps,")
            }

            val seconds = (durationMs / 100) / 10f
            if (seconds > 0f) {
                append(seconds)
                append(" seconds")
            }
        }
    }

    fun read(file: File) {
        require(file.exists()) {
            "File does not exist: ${file.absolutePath}"
        }

        sourceFile = file

        FileInputStream(file).use { input ->
            parse(input, file.length())
        }
    }

    fun exportFrames(
        outputStream: OutputStream,
        startFrame: Int,
        frameCount: Int
    ) {
        FileInputStream(sourceFile).use { input ->
            writeFrames(
                inputStream = input,
                outputStream = outputStream,
                startFrame = startFrame,
                frameCount = frameCount
            )
        }
    }

    protected abstract fun parse(
        inputStream: InputStream,
        fileSize: Long
    )

    protected abstract fun writeFrames(
        inputStream: InputStream,
        outputStream: OutputStream,
        startFrame: Int,
        frameCount: Int
    )

    companion object {
        private val factories: Map<String, () -> CheapSoundFile> = linkedMapOf(
            "m4a" to { ExtractorSoundFile("M4A", "audio/mp4") },
            "mp4" to { ExtractorSoundFile("MP4", "audio/mp4") },
            "3gp" to { ExtractorSoundFile("AMR", "audio/3gpp") },
            "3gpp" to { ExtractorSoundFile("AMR", "audio/3gpp") },
            "amr" to { ExtractorSoundFile("AMR", "audio/amr") },
            "mp3" to { Mp3SoundFile() },
            "wav" to { WavSoundFile() },
            "aac" to { AacSoundFile() }
        )

        fun isSupportedExtension(extension: String): Boolean {
            return factories.containsKey(extension.lowercase())
        }

        fun create(file: File): CheapSoundFile? {
            val extension = file.extension.lowercase()
            val preferredFactory = factories[extension]

            if (preferredFactory != null) {
                runCatching {
                    return preferredFactory().apply { read(file) }
                }
            }

            factories.values.forEach { factory ->
                runCatching {
                    return factory().apply { read(file) }
                }
            }

            return null
        }
    }
}

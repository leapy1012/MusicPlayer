package gd.app.musicplayer.ui.editor.model

class WaveformData(
    val sourcePath: String,
    val durationMs: Int,
    val mimeType: String?,
    val sampleRateHz: Int,
    val bitRate: Int,
    val samplesPerFrame: Int = 0,
    val frameTimesMs: IntArray,
    val frameGains: IntArray
) {
    fun infoText(): String {
        val type = sourcePath.substringAfterLast('.', missingDelimiterValue = "")
            .uppercase()
            .ifBlank { mimeType?.substringAfter('/')?.uppercase().orEmpty() }
            .ifBlank { "AUDIO" }

        return buildString {
            append(type)

            if (sampleRateHz > 0) {
                append(',')
                append(sampleRateHz)
                append("Hz")
            }

            if (bitRate > 0) {
                append(',')
                append(bitRate / 1000)
                append("kbps")
            }

            if (durationMs > 0) {
                append(',')
                append((durationMs / 100) / 10f)
                append(" seconds")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformData) return false

        return sourcePath == other.sourcePath &&
                durationMs == other.durationMs &&
                mimeType == other.mimeType &&
            sampleRateHz == other.sampleRateHz &&
                bitRate == other.bitRate &&
                samplesPerFrame == other.samplesPerFrame &&
                frameTimesMs.contentEquals(other.frameTimesMs) &&
                frameGains.contentEquals(other.frameGains)
    }

    override fun hashCode(): Int {
        var result = sourcePath.hashCode()
        result = 31 * result + durationMs
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + sampleRateHz
        result = 31 * result + bitRate
        result = 31 * result + samplesPerFrame
        result = 31 * result + frameTimesMs.contentHashCode()
        result = 31 * result + frameGains.contentHashCode()
        return result
    }
}

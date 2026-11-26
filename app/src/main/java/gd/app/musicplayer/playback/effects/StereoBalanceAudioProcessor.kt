package gd.app.musicplayer.playback.effects
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class StereoBalanceAudioProcessor private constructor(
    private val gains: ChannelGains
) : BaseAudioProcessor() {
    constructor() : this(ChannelGains())

    private class ChannelGains {
        @Volatile var left: Float = 1f
        @Volatile var right: Float = 1f
    }

    // Each player owns its format/buffers, while new crossfade streams inherit live gains.
    fun forNewStream(): StereoBalanceAudioProcessor = StereoBalanceAudioProcessor(gains)

    fun setChannelBalance(
        enabled: Boolean,
        left: Float,
        right: Float
    ) {
        if (enabled) {
            gains.left = left.coerceIn(0f, 1f)
            gains.right = right.coerceIn(0f, 1f)
        } else {
            gains.left = 1f
            gains.right = 1f
        }
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount in 1..CHANNEL_COUNT_STEREO
        ) {
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, CHANNEL_COUNT_STEREO,
                inputAudioFormat.encoding)
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()

        if (inputSize == 0) {
            return
        }

        val mono = inputAudioFormat.channelCount == 1
        val outputBuffer = replaceOutputBuffer(if (mono) inputSize * 2 else inputSize)
        outputBuffer.order(ByteOrder.nativeOrder())

        val left = gains.left
        val right = gains.right

        if (!mono && left == 1f && right == 1f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        while (inputBuffer.remaining() >= if (mono) 2 else BYTES_PER_STEREO_FRAME) {
            val inputLeft = inputBuffer.short.toInt()
            val inputRight = if (mono) inputLeft else inputBuffer.short.toInt()

            outputBuffer.putShort(scaleSample(inputLeft, left))
            outputBuffer.putShort(scaleSample(inputRight, right))
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    private fun scaleSample(
        sample: Int,
        gain: Float
    ): Short {
        if (gain == 1f) {
            return sample.toShort()
        }

        return (sample * gain)
            .roundToInt()
            .coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            )
            .toShort()
    }

    private companion object {
        private const val CHANNEL_COUNT_STEREO = 2
        private const val BYTES_PER_STEREO_FRAME = 4
    }
}
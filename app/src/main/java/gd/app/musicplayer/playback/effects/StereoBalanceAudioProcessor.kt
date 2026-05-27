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
class StereoBalanceAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var leftGain: Float = 1f

    @Volatile
    private var rightGain: Float = 1f

    fun setChannelBalance(
        enabled: Boolean,
        left: Float,
        right: Float
    ) {
        if (enabled) {
            leftGain = left.coerceIn(0f, 1f)
            rightGain = right.coerceIn(0f, 1f)
        } else {
            leftGain = 1f
            rightGain = 1f
        }
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount == CHANNEL_COUNT_STEREO
        ) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()

        if (inputSize == 0) {
            return
        }

        val outputBuffer = replaceOutputBuffer(inputSize)
        outputBuffer.order(ByteOrder.nativeOrder())

        val left = leftGain
        val right = rightGain

        while (inputBuffer.remaining() >= BYTES_PER_STEREO_FRAME) {
            val inputLeft = inputBuffer.short.toInt()
            val inputRight = inputBuffer.short.toInt()

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
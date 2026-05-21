package gd.app.musicplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class ReverbAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var config: ReverbConfig = ReverbConfig.DISABLED

    private var sampleRateHz: Int = 44_100
    private var leftDelay = FloatArray(0)
    private var rightDelay = FloatArray(0)
    private var writeIndex: Int = 0

    fun setPreset(index: Int) {
        config = ReverbConfig.fromIndex(index)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount == CHANNELS_STEREO
        ) {
            sampleRateHz = inputAudioFormat.sampleRate
            ensureCapacity()
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        clearState()
    }

    override fun onReset() {
        clearState()
        leftDelay = FloatArray(0)
        rightDelay = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        val current = config
        if (!current.enabled) {
            val output = replaceOutputBuffer(inputSize)
            output.put(inputBuffer)
            output.flip()
            return
        }

        ensureCapacity()
        val output = replaceOutputBuffer(inputSize)
        output.order(ByteOrder.nativeOrder())

        val delayA = current.delaySamplesA(sampleRateHz)
        val delayB = current.delaySamplesB(sampleRateHz)
        val delayC = current.delaySamplesC(sampleRateHz)
        val size = leftDelay.size.coerceAtLeast(1)

        while (inputBuffer.remaining() >= BYTES_PER_STEREO_FRAME) {
            val inL = inputBuffer.short.toInt().toFloat()
            val inR = inputBuffer.short.toInt().toFloat()

            val wetL =
                tap(leftDelay, writeIndex, delayA, size) * current.tapA +
                tap(leftDelay, writeIndex, delayB, size) * current.tapB +
                tap(leftDelay, writeIndex, delayC, size) * current.tapC +
                tap(rightDelay, writeIndex, delayB, size) * current.crossMix

            val wetR =
                tap(rightDelay, writeIndex, delayA, size) * current.tapA +
                tap(rightDelay, writeIndex, delayB, size) * current.tapB +
                tap(rightDelay, writeIndex, delayC, size) * current.tapC +
                tap(leftDelay, writeIndex, delayB, size) * current.crossMix

            output.putShort(((inL * current.dryMix) + (wetL * current.wetMix)).toPcm16())
            output.putShort(((inR * current.dryMix) + (wetR * current.wetMix)).toPcm16())

            leftDelay[writeIndex] = (inL + (wetL * current.feedback)).coerceIn(SHORT_MIN, SHORT_MAX)
            rightDelay[writeIndex] = (inR + (wetR * current.feedback)).coerceIn(SHORT_MIN, SHORT_MAX)

            writeIndex += 1
            if (writeIndex >= size) writeIndex = 0
        }

        while (inputBuffer.hasRemaining()) {
            output.put(inputBuffer.get())
        }

        output.flip()
    }

    private fun ensureCapacity() {
        val required = ((sampleRateHz * MAX_DELAY_MS) / 1_000f)
            .roundToInt()
            .coerceAtLeast(1)
        if (leftDelay.size == required && rightDelay.size == required) return
        leftDelay = FloatArray(required)
        rightDelay = FloatArray(required)
        writeIndex = 0
    }

    private fun clearState() {
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        writeIndex = 0
    }

    private fun tap(buffer: FloatArray, index: Int, delay: Int, size: Int): Float {
        val delayed = (index - delay).mod(size)
        return buffer[delayed]
    }

    private fun Float.toPcm16(): Short {
        return roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private data class ReverbConfig(
        val enabled: Boolean,
        val delayMsA: Float,
        val delayMsB: Float,
        val delayMsC: Float,
        val dryMix: Float,
        val wetMix: Float,
        val feedback: Float,
        val tapA: Float,
        val tapB: Float,
        val tapC: Float,
        val crossMix: Float
    ) {
        fun delaySamplesA(rate: Int): Int = ((rate * delayMsA) / 1_000f).roundToInt()
        fun delaySamplesB(rate: Int): Int = ((rate * delayMsB) / 1_000f).roundToInt()
        fun delaySamplesC(rate: Int): Int = ((rate * delayMsC) / 1_000f).roundToInt()

        companion object {
            val DISABLED = ReverbConfig(false, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)

            fun fromIndex(index: Int): ReverbConfig {
                return when (index) {
                    1 -> preset(22f, 36f, 58f, 0.84f, 0.18f, 0.22f, 0.65f, 0.25f, 0.15f, 0.08f)
                    2 -> preset(30f, 48f, 72f, 0.82f, 0.22f, 0.28f, 0.70f, 0.32f, 0.18f, 0.10f)
                    3 -> preset(42f, 64f, 96f, 0.80f, 0.26f, 0.34f, 0.76f, 0.40f, 0.22f, 0.12f)
                    4 -> preset(58f, 82f, 118f, 0.78f, 0.30f, 0.42f, 0.86f, 0.48f, 0.26f, 0.14f)
                    5 -> preset(72f, 104f, 146f, 0.74f, 0.36f, 0.52f, 0.92f, 0.60f, 0.32f, 0.18f)
                    6 -> preset(18f, 28f, 44f, 0.80f, 0.24f, 0.30f, 0.72f, 0.26f, 0.12f, 0.04f)
                    else -> DISABLED
                }
            }

            private fun preset(
                delayA: Float,
                delayB: Float,
                delayC: Float,
                dryMix: Float,
                wetMix: Float,
                feedback: Float,
                tapA: Float,
                tapB: Float,
                tapC: Float,
                crossMix: Float
            ): ReverbConfig {
                return ReverbConfig(
                    enabled = true,
                    delayMsA = delayA,
                    delayMsB = delayB,
                    delayMsC = delayC,
                    dryMix = dryMix,
                    wetMix = wetMix,
                    feedback = feedback,
                    tapA = tapA,
                    tapB = tapB,
                    tapC = tapC,
                    crossMix = crossMix
                )
            }
        }
    }

    private companion object {
        private const val CHANNELS_STEREO = 2
        private const val BYTES_PER_STEREO_FRAME = 4
        private const val MAX_DELAY_MS = 180f
        private const val SHORT_MIN = -32768f
        private const val SHORT_MAX = 32767f
    }
}

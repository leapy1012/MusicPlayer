package gd.app.musicplayer.playback.effects

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class StereoBalanceAudioProcessorTest {
    private fun process(channels:Int, enabled:Boolean, left:Float, right:Float,
        vararg samples:Int):List<Int> {
        val processor=StereoBalanceAudioProcessor()
        val format=processor.configure(AudioProcessor.AudioFormat(44100,channels,C.ENCODING_PCM_16BIT))
        assertEquals(2,format.channelCount)
        processor.flush()
        processor.setChannelBalance(enabled,left,right)
        val buffer=ByteBuffer.allocateDirect(samples.size*2).order(ByteOrder.nativeOrder())
        samples.forEach { buffer.putShort(it.toShort()) }
        buffer.flip()
        processor.queueInput(buffer)
        val output=processor.output.order(ByteOrder.nativeOrder())
        return buildList { while(output.remaining()>=2) add(output.short.toInt()) }
    }
    @Test fun independentStereoChannelsRetainTheirOwnGain() {
        assertEquals(listOf(0,-1000,0,2000),process(2,true,0f,1f,1000,-1000,-2000,2000))
        assertEquals(listOf(1000,0),process(2,true,1f,0f,1000,-1000))
    }
    @Test fun monoIsExpandedSoLeftAndRightStillWork() {
        assertEquals(listOf(0,1000,0,-2000),process(1,true,0f,1f,1000,-2000))
    }
    @Test fun disablingBalanceRestoresBothChannels() {
        assertEquals(listOf(1000,-1000),process(2,false,0f,0f,1000,-1000))
    }
    @Test fun crossfadeStreamsHaveIndependentBuffersAndShareLiveGainUpdates() {
        val controls=StereoBalanceAudioProcessor()
        val first=controls.forNewStream()
        val second=controls.forNewStream()
        val format=AudioProcessor.AudioFormat(44100,2,C.ENCODING_PCM_16BIT)
        for (stream in listOf(first,second)) { stream.configure(format); stream.flush() }
        controls.setChannelBalance(true,0f,1f)
        fun feed(stream:StereoBalanceAudioProcessor):ByteBuffer {
            val input=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            input.putShort(1000.toShort()).putShort(2000.toShort()).flip()
            stream.queueInput(input)
            return stream.output.order(ByteOrder.nativeOrder())
        }
        val firstOutput=feed(first)
        val secondOutput=feed(second)
        assertNotSame(firstOutput,secondOutput)
        assertEquals(0,firstOutput.short.toInt())
        assertEquals(2000,firstOutput.short.toInt())
        assertEquals(0,secondOutput.short.toInt())
        assertEquals(2000,secondOutput.short.toInt())
        first.reset()
        controls.setChannelBalance(true,1f,0f)
        val continuing=feed(second)
        assertEquals(1000,continuing.short.toInt())
        assertEquals(0,continuing.short.toInt())
    }

}

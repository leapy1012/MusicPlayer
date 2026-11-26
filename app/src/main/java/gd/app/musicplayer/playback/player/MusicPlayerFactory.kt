package gd.app.musicplayer.playback.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerFactory @Inject constructor() {

    /** Both normal and crossfade playback support independent channel gains from first play. */
    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    ): ExoPlayer {
        // ExoPlayer has only a master gain; independent channels require the PCM processor.
        return createWithBalanceProcessor(context, stereoBalanceAudioProcessor)
    }

    @OptIn(UnstableApi::class)
    fun createWithBalanceProcessor(
        context: Context,
        stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(
                        arrayOf<AudioProcessor>(
                            stereoBalanceAudioProcessor.forNewStream()
                        )
                    )
                    .build()
            }
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()
    }
}

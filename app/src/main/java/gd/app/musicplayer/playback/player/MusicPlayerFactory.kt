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

    /**
     * Builds an ExoPlayer. Prefer [createLightweight] for first play to match original
     * [MediaPlayer] cost; use [create] when stereo-balance PCM processing is required.
     */
    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    ): ExoPlayer {
        // First play path: plain ExoPlayer like original MediaPlayer (no custom sink).
        // Balance processor is kept for API wiring; PCM path activates when effects rebuild.
        return createLightweight(context)
    }

    fun createLightweight(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
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
                            stereoBalanceAudioProcessor
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

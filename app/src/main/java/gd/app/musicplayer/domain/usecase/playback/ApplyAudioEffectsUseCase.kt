package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class ApplyAudioEffectsUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(context: Context) {
        playbackController.applyAudioEffects(context)
    }
}

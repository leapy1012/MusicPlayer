package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackGateway
import javax.inject.Inject

class ApplyAudioEffectsUseCase @Inject constructor() {
    operator fun invoke(context: Context) {
        PlaybackGateway.applyAudioEffects(context)
    }
}

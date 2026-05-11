package gd.app.musicplayer.domain.usecase.equalizer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ApplyAudioEffectsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    operator fun invoke() {
        /*
         * Move your actual audio-effect applying logic here.
         *
         * Do not keep this depending on PlayerViewModel long-term.
         * PlayerViewModel should not be a dependency of a domain use case.
         *
         * For now, copy the logic from:
         * playerViewModel.applyAudioEffects(requireContext())
         * into a proper audio engine/controller/repository.
         */
    }
}
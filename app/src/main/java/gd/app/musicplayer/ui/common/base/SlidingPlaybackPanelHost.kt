package gd.app.musicplayer.ui.common.base

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import gd.app.lib.view.panel.SlidingPanelBannerCrossfadeListener
import gd.app.lib.view.panel.SlidingUpPanelLayout
import gd.app.musicplayer.playback.MusicPlaybackController
import kotlinx.coroutines.launch

internal class SlidingPlaybackPanelHost(
    private val lifecycleOwner: LifecycleOwner,
    private val panel: SlidingUpPanelLayout,
    rootView: View,
    onSlideOffsetChanged: ((Float) -> Unit)? = null
) {
    private val crossfadeListener = SlidingPanelBannerCrossfadeListener(
        onSlideOffsetChanged = onSlideOffsetChanged,
        rootView = rootView
    )

    fun bind() {
        panel.addPanelSlideListener(crossfadeListener)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MusicPlaybackController.state.collect(::render)
            }
        }
    }

    private fun render(state: gd.app.musicplayer.playback.MusicPlaybackState) {
        panel.setTouchEnabled(true)
        if (panel.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
            panel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED)
        }
    }
}

package gd.app.musicplayer.ui.common.base

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior

abstract class BasePlayerSheetActivity : BaseActivity() {

    companion object {
        protected const val STATE_PLAYBACK_SHEET = "playback_sheet_state"
    }

    protected abstract val playerSheetView: View
    protected abstract val collapsedPlayerView: View
    protected abstract val expandedPlayerView: View

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var playbackPanelHost: BottomSheetPlaybackPanelHost

    protected fun setupPlayerSheet(
        savedInstanceState: Bundle?,
        onSlideOffsetChanged: ((Float) -> Unit)? = null,
        onCollapsedBannerClick: (() -> Unit)? = null
    ) {
        bottomSheetBehavior = BottomSheetBehavior.from(playerSheetView)
        playbackPanelHost = BottomSheetPlaybackPanelHost(
            behavior = bottomSheetBehavior,
            collapsedBannerView = collapsedPlayerView,
            expandedBannerView = expandedPlayerView,
            onSlideOffsetChanged = onSlideOffsetChanged
        ).also { it.bind(onCollapsedBannerClick) }

//        when (savedInstanceState?.getInt(STATE_PLAYBACK_SHEET)) {
//            BottomSheetBehavior.STATE_EXPANDED,
//            BottomSheetBehavior.STATE_HALF_EXPANDED -> playbackPanelHost.expand()
//            else -> playbackPanelHost.collapse()
//        }
    }

    protected fun installCollapseOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPlayerSheetExpanded()) {
                    collapsePlayerSheet()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    protected fun collapsePlayerSheet() {
        if (::playbackPanelHost.isInitialized) {
            playbackPanelHost.collapse()
        }
    }

    protected fun expandPlayerSheet() {
        if (::playbackPanelHost.isInitialized) {
            playbackPanelHost.expand()
        }
    }

    protected fun isPlayerSheetExpanded(): Boolean {
        return ::playbackPanelHost.isInitialized && playbackPanelHost.isExpanded()
    }

    protected fun savePlayerSheetState(outState: Bundle) {
        if (::bottomSheetBehavior.isInitialized) {
            outState.putInt(STATE_PLAYBACK_SHEET, bottomSheetBehavior.state)
        }
    }
}

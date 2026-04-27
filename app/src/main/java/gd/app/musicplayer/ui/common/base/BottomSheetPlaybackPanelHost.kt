package gd.app.musicplayer.ui.common.base

import android.view.View
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import gd.app.musicplayer.R

internal class BottomSheetPlaybackPanelHost(
    private val behavior: BottomSheetBehavior<View>,
    private val collapsedBannerView: View,
    private val expandedBannerView: View,
    private val onSlideOffsetChanged: ((Float) -> Unit)? = null
) {
    fun bind(onCollapsedBannerClick: (() -> Unit)? = null) {
        behavior.apply {
            isHideable = false
            isFitToContents = true
            skipCollapsed = false
            peekHeight = collapsedBannerView.resources.getDimensionPixelSize(R.dimen.main_control_banner_height)
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        collapsedBannerView.doOnLayout { view ->
            if (view.height > 0) {
                behavior.peekHeight = view.height
                if (!isExpanded()) {
                    behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }

        collapsedBannerView.setOnClickListener {
            onCollapsedBannerClick?.invoke() ?: expand()
        }


        showCollapsedPlayer()
        onSlideOffsetChanged?.invoke(0f)
    }

    fun collapse() {
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun expand() {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun isExpanded(): Boolean {
        return behavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED
    }

    private fun showCollapsedPlayer() {
        collapsedBannerView.visibility = View.VISIBLE
        collapsedBannerView.alpha = 1f
        collapsedBannerView.isClickable = true

        expandedBannerView.visibility = View.GONE
        expandedBannerView.alpha = 0f
        expandedBannerView.isClickable = false
    }

    private fun showExpandedPlayer() {
        collapsedBannerView.visibility = View.GONE
        collapsedBannerView.alpha = 0f
        collapsedBannerView.isClickable = false

        expandedBannerView.visibility = View.VISIBLE
        expandedBannerView.alpha = 1f
        expandedBannerView.isClickable = true
    }
}

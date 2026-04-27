package gd.app.lib.view.panel

import android.view.View

class SlidingPanelBannerCrossfadeListener(
    private val onSlideOffsetChanged: ((Float) -> Unit)?,
    rootView: View
) : SlidingUpPanelLayout.PanelSlideListener {

    private val collapsedBannerView: View? =
        rootView.findViewById(
            rootView.resources.getIdentifier(
                "main_fragment_banner",
                "id",
                rootView.context.packageName
            )
        )
    private val expandedBannerView: View? =
        rootView.findViewById(
            rootView.resources.getIdentifier(
                "main_fragment_banner_2",
                "id",
                rootView.context.packageName
            )
        )

    private var currentSlideOffset: Float = 0f

    init {
        collapsedBannerView?.visibility = View.VISIBLE
        expandedBannerView?.visibility = View.INVISIBLE
    }

    override fun onPanelSlide(view: View, slideOffset: Float) {
        currentSlideOffset = slideOffset
        collapsedBannerView?.visibility = View.VISIBLE
        expandedBannerView?.visibility = View.VISIBLE
        collapsedBannerView?.alpha = 1f - slideOffset
        expandedBannerView?.alpha = slideOffset
        onSlideOffsetChanged?.invoke(slideOffset)
    }

    override fun onPanelStateChanged(
        view: View,
        previousState: SlidingUpPanelLayout.PanelState,
        newState: SlidingUpPanelLayout.PanelState
    ) {
        when (newState) {
            SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                collapsedBannerView?.visibility = View.VISIBLE
                expandedBannerView?.visibility = View.INVISIBLE
                currentSlideOffset = 0f
                onSlideOffsetChanged?.invoke(0f)
            }

            SlidingUpPanelLayout.PanelState.EXPANDED -> {
                collapsedBannerView?.visibility = View.INVISIBLE
                expandedBannerView?.visibility = View.VISIBLE
                currentSlideOffset = 1f
                onSlideOffsetChanged?.invoke(1f)
            }

            else -> Unit
        }
    }

    fun getCurrentSlideOffset(): Float = currentSlideOffset
}

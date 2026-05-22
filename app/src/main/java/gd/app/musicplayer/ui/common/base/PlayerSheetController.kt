package gd.app.musicplayer.ui.common.base

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import gd.app.musicplayer.R
import kotlin.math.roundToInt

class PlayerSheetController(
    private val resources: Resources,
    private val playerSheet: FrameLayout,
    private val miniPlayer: View,
    private val fullPlayer: View,
    private val insetTarget: View? = null,
    private val onMiniPlayerClick: () -> Unit
) {

    val behavior: BottomSheetBehavior<FrameLayout> =
        BottomSheetBehavior.from(playerSheet)

    private val collapsedHeight: Int
        get() = resources.getDimensionPixelSize(R.dimen.main_control_banner_height)

    private val expandedHeight: Int
        get() = resources.getDimensionPixelSize(R.dimen.player_sheet_height)

    fun setup() {
        setupBehavior()
        setupInsets()
        setupClicks()
        renderCollapsed()
    }

    fun expand() {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun collapse() {
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupBehavior() {
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        behavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> renderCollapsed()
                        BottomSheetBehavior.STATE_EXPANDED -> renderExpanded()

                        BottomSheetBehavior.STATE_DRAGGING,
                        BottomSheetBehavior.STATE_SETTLING -> {
                            miniPlayer.visibility = View.VISIBLE
                            fullPlayer.visibility = View.VISIBLE
                        }

                        BottomSheetBehavior.STATE_HIDDEN,
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> Unit
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    renderSlide(slideOffset.coerceIn(0f, 1f))
                }
            }
        )
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(playerSheet) { _, insets ->
            val navBottom = insets
                .getInsets(WindowInsetsCompat.Type.systemBars())
                .bottom

            val collapsedSheetHeight = collapsedHeight + navBottom
            val expandedSheetHeight = expandedHeight + navBottom

            playerSheet.updateLayoutParams {
                height = expandedSheetHeight
            }

            fullPlayer.updateLayoutParams {
                height = expandedSheetHeight
            }

            miniPlayer.updateLayoutParams {
                height = collapsedSheetHeight
            }

            behavior.peekHeight = collapsedSheetHeight

            updateInsetTarget(progress = getCurrentProgress())

            insets
        }

        ViewCompat.requestApplyInsets(playerSheet)
    }

    private fun setupClicks() {
        miniPlayer.setOnClickListener {
            onMiniPlayerClick()
        }
    }

    private fun renderCollapsed() {
        renderProgress(progress = 0f)

        miniPlayer.visibility = View.VISIBLE
        fullPlayer.visibility = View.GONE

        miniPlayer.isClickable = true
        fullPlayer.isClickable = false
    }

    private fun renderExpanded() {
        renderProgress(progress = 1f)

        miniPlayer.visibility = View.GONE
        fullPlayer.visibility = View.VISIBLE

        miniPlayer.isClickable = false
        fullPlayer.isClickable = true
    }

    private fun renderSlide(progress: Float) {
        miniPlayer.visibility = View.VISIBLE
        fullPlayer.visibility = View.VISIBLE

        renderProgress(progress)

        miniPlayer.isClickable = progress < CLICK_THRESHOLD
        fullPlayer.isClickable = progress >= CLICK_THRESHOLD
    }

    private fun renderProgress(progress: Float) {
        miniPlayer.alpha = 1f - progress
        fullPlayer.alpha = progress

        updateInsetTarget(progress)
    }

    private fun updateInsetTarget(progress: Float) {
        val target = insetTarget ?: return

        val extraOffset = ((expandedHeight - collapsedHeight) * progress).roundToInt()
        val bottomMargin = collapsedHeight + extraOffset

        target.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            if (this.bottomMargin != bottomMargin) {
                this.bottomMargin = bottomMargin
            }
        }
    }

    private fun getCurrentProgress(): Float {
        return when (behavior.state) {
            BottomSheetBehavior.STATE_EXPANDED -> 1f
            BottomSheetBehavior.STATE_COLLAPSED -> 0f
            else -> 0f
        }
    }

    companion object {
        private const val CLICK_THRESHOLD = 0.5f
    }
}
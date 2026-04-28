package gd.app.musicplayer.ui.common.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.view.PlayerSheetInsetHost
import gd.app.musicplayer.core.ui.view.MusicRecyclerView

abstract class BasePlayerSheetActivity : BaseActivity(), PlayerSheetInsetHost {

    companion object {
        const val EXTRA_EXPAND_PLAYER = "gd.app.musicplayer.extra.EXPAND_PLAYER"
    }

    protected lateinit var playerSheetBehavior: BottomSheetBehavior<FrameLayout>
        private set

    protected abstract val playerSheet: FrameLayout
    protected abstract val miniPlayer: View
    protected abstract val fullPlayer: View

    protected open fun setupPlayerSheet() {
        setupPlayerSheetInsets()

        playerSheetBehavior = BottomSheetBehavior.from(playerSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        renderPlayerSheetForState(playerSheetBehavior.state)

        playerSheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    renderPlayerSheetForState(newState)
                    updateMusicRecyclerViewPlayerInset()
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val progress = slideOffset.coerceIn(0f, 1f)

                    miniPlayer.alpha = 1f - progress
                    fullPlayer.alpha = progress
                    updateMusicRecyclerViewPlayerInset()
                }
            }
        )

        playerSheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateMusicRecyclerViewPlayerInset()
        }
        window.decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateMusicRecyclerViewPlayerInset()
        }
        playerSheet.post {
            updateMusicRecyclerViewPlayerInset()
        }

        miniPlayer.setOnClickListener {
            expandPlayerPanel()
        }
    }

    fun collapsePlayerPanel() {
        playerSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    protected fun expandPlayerPanel() {
        playerSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    protected fun handlePlayerSheetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_EXPAND_PLAYER, false) != true) return

        playerSheet.post {
            expandPlayerPanel()
        }

        intent.removeExtra(EXTRA_EXPAND_PLAYER)
    }

    private fun setupPlayerSheetInsets() {
        val normalSheetHeight = resources.getDimensionPixelSize(R.dimen.player_sheet_height)
        val normalMiniHeight = resources.getDimensionPixelSize(R.dimen.main_control_banner_height)

        ViewCompat.setOnApplyWindowInsetsListener(playerSheet) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            playerSheet.updateLayoutParams {
                height = normalSheetHeight + navBottom
            }

            fullPlayer.updateLayoutParams {
                height = normalSheetHeight + navBottom
            }

            miniPlayer.updateLayoutParams {
                height = normalMiniHeight + navBottom
            }

            playerSheetBehavior.peekHeight = normalMiniHeight + navBottom
            updateMusicRecyclerViewPlayerInset()

            insets
        }

        ViewCompat.requestApplyInsets(playerSheet)
    }

    private fun renderPlayerSheetForState(state: Int) {
        when (state) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                miniPlayer.visibility = View.VISIBLE
                miniPlayer.alpha = 1f

                fullPlayer.visibility = View.VISIBLE
                fullPlayer.alpha = 0f
                fullPlayer.isClickable = false
            }

            BottomSheetBehavior.STATE_EXPANDED -> {
                miniPlayer.visibility = View.VISIBLE
                miniPlayer.alpha = 0f

                fullPlayer.visibility = View.VISIBLE
                fullPlayer.alpha = 1f
                fullPlayer.isClickable = true
            }

            BottomSheetBehavior.STATE_DRAGGING,
            BottomSheetBehavior.STATE_SETTLING -> {
                miniPlayer.visibility = View.VISIBLE
                fullPlayer.visibility = View.VISIBLE
            }
        }
    }

    private fun updateMusicRecyclerViewPlayerInset() {
        val visibleSheetHeight = currentPlayerSheetVisibleHeight()

        window.decorView.findMusicRecyclerViews()
            .filterNot { recyclerView -> recyclerView.isDescendantOf(playerSheet) }
            .forEach { recyclerView ->
                recyclerView.setPlayerSheetBottomInset(visibleSheetHeight)
            }
    }

    override fun currentPlayerSheetVisibleHeight(): Int {
        if (!::playerSheetBehavior.isInitialized) return 0
        val parent = playerSheet.parent as? View ?: return 0
        return (parent.height - playerSheet.top).coerceAtLeast(0)
    }

    private fun View.findMusicRecyclerViews(): List<MusicRecyclerView> {
        val matches = mutableListOf<MusicRecyclerView>()

        fun collect(view: View) {
            if (view is MusicRecyclerView) {
                matches += view
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    collect(view.getChildAt(index))
                }
            }
        }

        collect(this)
        return matches
    }

    private fun View.isDescendantOf(parentView: View): Boolean {
        var currentParent = parent

        while (currentParent is View) {
            if (currentParent == parentView) return true
            currentParent = currentParent.parent
        }

        return false
    }
}

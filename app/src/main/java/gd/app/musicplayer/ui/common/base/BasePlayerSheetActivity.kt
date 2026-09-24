package gd.app.musicplayer.ui.common.base

import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import gd.app.musicplayer.R
import gd.app.musicplayer.feature.player.bottomsheet.BottomPlayerFragment
import gd.app.musicplayer.feature.player.mini.BottomMiniPlayerFragment

abstract class BasePlayerSheetActivity : BaseActivity() {

    companion object {
        const val EXTRA_EXPAND_PLAYER = "gd.app.musicplayer.extra.EXPAND_PLAYER"
        private const val MINI_PLAYER_TAG = "player_sheet_mini"
        private const val FULL_PLAYER_TAG = "player_sheet_full"
    }

    private lateinit var playerSheetController: PlayerSheetController
    private var playerFragmentsAttached = false

    protected abstract val playerSheet: FrameLayout
    protected abstract val miniPlayer: View
    protected abstract val fullPlayer: View

    protected open val playerSheetInsetTarget: View?
        get() = null

    protected open val miniPlayerContainerId: Int
        get() = R.id.miniPlayer

    protected open val fullPlayerContainerId: Int
        get() = R.id.bottomPlayer

    /**
     * Original ActivityAlbum: banners already in the same commit() as content.
     * Only wire BottomSheetBehavior — do not force another fragment attach here.
     */
    protected open fun setupPlayerSheet() {
        if (::playerSheetController.isInitialized) return

        playerSheetController = PlayerSheetController(
            resources = resources,
            playerSheet = playerSheet,
            miniPlayer = miniPlayer,
            fullPlayer = fullPlayer,
            insetTarget = playerSheetInsetTarget,
            onMiniPlayerClick = ::expandPlayerPanel
        )

        playerSheetController.setup()
        playerFragmentsAttached =
            supportFragmentManager.findFragmentById(miniPlayerContainerId) != null &&
                supportFragmentManager.findFragmentById(fullPlayerContainerId) != null
    }

    protected fun setupPlayerSheetDeferred() {
        ensurePlayerFragments()
        setupPlayerSheet()
    }

    protected fun ensurePlayerFragments() {
        if (playerFragmentsAttached) return
        if (supportFragmentManager.isStateSaved) return

        val miniExists = supportFragmentManager.findFragmentById(miniPlayerContainerId) != null
        val fullExists = supportFragmentManager.findFragmentById(fullPlayerContainerId) != null
        if (miniExists && fullExists) {
            playerFragmentsAttached = true
            return
        }

        supportFragmentManager.beginTransaction().apply {
            if (!miniExists) {
                replace(miniPlayerContainerId, createMiniPlayerFragment(), MINI_PLAYER_TAG)
            }
            if (!fullExists) {
                replace(fullPlayerContainerId, createFullPlayerFragment(), FULL_PLAYER_TAG)
            }
        }.commitNowAllowingStateLoss()
        playerFragmentsAttached = true
    }

    protected open fun createMiniPlayerFragment(): Fragment {
        return BottomMiniPlayerFragment.newInstance()
    }

    protected open fun createFullPlayerFragment(): Fragment {
        return BottomPlayerFragment()
    }

    fun collapsePlayerPanel() {
        if (!::playerSheetController.isInitialized) return
        playerSheetController.collapse()
    }

    /**
     * Original [com.ijoysoft.music.view.panel.SlidingUpPanelLayout.w]:
     * if expanded, collapse and report handled.
     */
    fun collapsePlayerPanelIfExpanded(): Boolean {
        if (!::playerSheetController.isInitialized) return false
        if (!playerSheetController.isExpanded()) return false
        playerSheetController.collapse()
        return true
    }

    protected fun expandPlayerPanel() {
        ensurePlayerFragments()
        if (!::playerSheetController.isInitialized) {
            setupPlayerSheet()
        }
        playerSheetController.expand()
    }

    protected fun handlePlayerSheetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_EXPAND_PLAYER, false) != true) return

        playerSheet.post {
            expandPlayerPanel()
        }

        intent.removeExtra(EXTRA_EXPAND_PLAYER)
    }
}

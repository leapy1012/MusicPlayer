package gd.app.musicplayer.ui.common.base

import android.content.Intent
import android.view.View
import android.widget.FrameLayout

abstract class BasePlayerSheetActivity : BaseActivity() {

    companion object {
        const val EXTRA_EXPAND_PLAYER = "gd.app.musicplayer.extra.EXPAND_PLAYER"
    }

    private lateinit var playerSheetController: PlayerSheetController

    protected abstract val playerSheet: FrameLayout
    protected abstract val miniPlayer: View
    protected abstract val fullPlayer: View

    protected open val playerSheetInsetTarget: View?
        get() = null

    protected open fun setupPlayerSheet() {
        playerSheetController = PlayerSheetController(
            resources = resources,
            playerSheet = playerSheet,
            miniPlayer = miniPlayer,
            fullPlayer = fullPlayer,
            insetTarget = playerSheetInsetTarget,
            onMiniPlayerClick = ::expandPlayerPanel
        )

        playerSheetController.setup()
    }

    fun collapsePlayerPanel() {
        playerSheetController.collapse()
    }

    protected fun expandPlayerPanel() {
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
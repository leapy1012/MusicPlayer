package gd.app.musicplayer.ui.shell

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.databinding.ActivityMainBinding
import gd.app.musicplayer.ui.home.MainFragment
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.ui.common.base.BasePlayerSheetActivity

@AndroidEntryPoint
class MainActivity : BasePlayerSheetActivity() {

    companion object {
        private const val STATE_SHOW_MENU = "show_menu"
        const val EXTRA_EXPAND_PLAYER = "gd.app.musicplayer.extra.EXPAND_PLAYER"

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, MainActivity::class.java))
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navigationDrawer: DrawerLayout

    override val playerSheet: FrameLayout
        get() = binding.playerSheet

    override val miniPlayer: View
        get() = binding.miniPlayer

    override val fullPlayer: View
        get() = binding.bottomPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeMainUi(savedInstanceState)
        setupPlayerSheet()
        handleShortcutIntent(intent)
        handlePlayerSheetIntent(intent)
    }

    fun openDrawer() {
        navigationDrawer.openDrawer(GravityCompat.START)
    }

    fun drawerLayout(): DrawerLayout = navigationDrawer


    private fun initializeMainUi(savedInstanceState: Bundle?) {
        navigationDrawer = binding.mainDrawerLayout

        installBackHandler()
        setupDrawerWidth()
        setupFragments(savedInstanceState)
        setupFragmentCallbacks()
        updateDrawerLockMode()

        if (savedInstanceState?.getBoolean(STATE_SHOW_MENU) == true) {
            binding.root.post { openDrawer() }
        }
    }


    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    navigationDrawer.isDrawerOpen(GravityCompat.START) -> {
                        navigationDrawer.closeDrawer(GravityCompat.START)
                    }

                    supportFragmentManager.backStackEntryCount > 0 -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun setupDrawerWidth() {
        val drawerWidthRatio = if (isTablet()) 0.4f else 0.8f
        binding.mainMenu.layoutParams = DrawerLayout.LayoutParams(
            (screenWidth * drawerWidthRatio).toInt(),
            DrawerLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = GravityCompat.START
        }
        navigationDrawer.setDrawerElevation(resources.displayMetrics.density * 4f)
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        supportFragmentManager.beginTransaction()
            .replace(binding.mainMenu.id, MoreFragment(), MoreFragment::class.java.simpleName)
            .replace(
                binding.mainFragmentContainer.id,
                MainFragment(),
                MainFragment::class.java.simpleName
            )
            .commitNow()
    }

    private fun setupFragmentCallbacks() {
        supportFragmentManager.addOnBackStackChangedListener {
            updateDrawerLockMode()
        }
    }

    private fun updateDrawerLockMode() {
        navigationDrawer.setDrawerLockMode(
            if (supportFragmentManager.backStackEntryCount == 0) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU || supportFragmentManager.backStackEntryCount != 0) {
            return super.onKeyDown(keyCode, event)
        }

        return if (navigationDrawer.isDrawerOpen(GravityCompat.START)) {
            navigationDrawer.closeDrawer(GravityCompat.START)
            true
        } else {
            navigationDrawer.openDrawer(GravityCompat.START)
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
        handlePlayerSheetIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SHOW_MENU, navigationDrawer.isDrawerOpen(GravityCompat.START))
//        savePlayerSheetState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun handleShortcutIntent(intent: Intent?) {
//        val musicSet = MusicSetShortcutHelper.consumeShortcutIntent(intent) ?: return
//        setIntent(Intent(this, MainActivity::class.java))
//        binding.root.post {
//            AlbumMusicActivity.start(this, musicSet)
//        }
    }

}

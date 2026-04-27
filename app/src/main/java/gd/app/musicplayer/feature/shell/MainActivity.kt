package gd.app.musicplayer.feature.shell

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivityMainBinding
import gd.app.musicplayer.ui.main.MainFragment
import gd.app.musicplayer.core.ui.extension.isTablet
import gd.app.musicplayer.core.ui.extension.screenWidth
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    companion object {
        private const val STATE_SHOW_MENU = "show_menu"
        const val EXTRA_EXPAND_PLAYER = "gd.app.musicplayer.extra.EXPAND_PLAYER"

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, MainActivity::class.java))
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navigationDrawer: DrawerLayout


    private lateinit var behavior: BottomSheetBehavior<FrameLayout>

    private lateinit var playerSheet: FrameLayout
    private lateinit var miniPlayer: View
    private lateinit var fullPlayer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeMainUi(savedInstanceState)
        setupPlayerSheetInsets()
        handleShortcutIntent(intent)
        handleNotificationIntent(intent)
    }

    fun openDrawer() {
        navigationDrawer.openDrawer(GravityCompat.START)
    }

    private fun setupPlayerSheetInsets() {
        val sheet = binding.playerSheet
        val miniPlayer = binding.miniPlayer
        val bottomPlayer = binding.bottomPlayer
        val behavior = BottomSheetBehavior.from(sheet)

        val normalSheetHeight = resources.getDimensionPixelSize(R.dimen.player_sheet_height)
        val normalMiniHeight = resources.getDimensionPixelSize(R.dimen.main_control_banner_height)

        ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            sheet.updateLayoutParams {
                height = normalSheetHeight + navBottom
            }

            bottomPlayer.updateLayoutParams {
                height = normalSheetHeight + navBottom
            }

            miniPlayer.updateLayoutParams {
                height = normalMiniHeight + navBottom
            }

            behavior.peekHeight = normalMiniHeight + navBottom

            insets
        }

        ViewCompat.requestApplyInsets(sheet)
    }

    fun drawerLayout(): DrawerLayout = navigationDrawer

    fun collapsePlayerPanel() {
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun expandPlayerPanel() {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

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

        playerSheet = findViewById(R.id.playerSheet)
        miniPlayer = findViewById(R.id.miniPlayer)
        fullPlayer = findViewById(R.id.bottomPlayer)

        behavior = BottomSheetBehavior.from(playerSheet).apply {
            isHideable = false
            skipCollapsed = false


            // mini player height
            peekHeight = resources.getDimensionPixelSize(R.dimen.main_control_banner_height)

            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        renderForState(behavior.state)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                renderForState(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional smooth transition
                val progress = slideOffset.coerceIn(0f, 1f)

                miniPlayer.alpha = 1f - progress
                fullPlayer.alpha = progress
            }
        })

        miniPlayer.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

    }


    private fun renderForState(state: Int) {
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
        handleNotificationIntent(intent)
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

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_EXPAND_PLAYER, false) != true) return
        binding.root.post {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        intent.removeExtra(EXTRA_EXPAND_PLAYER)
    }

    private fun applyPlaybackPanelNavigationBarColor(slideOffset: Float) {
        val expandedOverlay = ColorUtils.setAlphaComponent(
            Color.BLACK,
            (slideOffset.coerceIn(0f, 1f) * 51f).roundToInt()
        )
        val collapsedOverlay = ColorUtils.setAlphaComponent(
            Color.BLACK,
            ((1f - slideOffset.coerceIn(0f, 1f)) * 26f).roundToInt()
        )
        window.navigationBarColor = ColorUtils.compositeColors(expandedOverlay, collapsedOverlay)
    }
}

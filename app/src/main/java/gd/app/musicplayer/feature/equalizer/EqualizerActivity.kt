package gd.app.musicplayer.feature.equalizer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.designsystem.dialog.BaseDialog
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.designsystem.theme.CouiAccentOverlay
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.messageColor
import gd.app.musicplayer.core.designsystem.theme.titleColor
import gd.app.musicplayer.databinding.ActivityEqualizerBinding
import gd.app.musicplayer.domain.usecase.equalizer.LoadAudioEffectSettingsUseCase
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.ui.common.CouiTabLayoutMediator
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.applyCouiLeftTitle
import gd.app.musicplayer.ui.common.base.inflateThemedMenu
import gd.app.musicplayer.ui.common.installEqualWidthTabs
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EqualizerActivity : BaseActivity() {

    @Inject lateinit var loadAudioEffectSettingsUseCase: LoadAudioEffectSettingsUseCase
    @Inject lateinit var soundEffectPreferences: SoundEffectPreferences

    private val playerViewModel: PlayerViewModel by viewModels()
    private lateinit var binding: ActivityEqualizerBinding
    private val equalizerFragment = EqualizerFragment()
    private val soundEffectFragment = SoundEffectFragment()
    private var tabMediator: CouiTabLayoutMediator? = null
    private var equalWidthListener: View.OnLayoutChangeListener? = null
    private lateinit var tipGuard: EqualizerEnableTipGuard
    private var picturedStyle: Boolean = false

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, EqualizerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        picturedStyle = EqualizerUiStyle.isPictured(themeRepo.getCorePalette())
        if (picturedStyle) {
            // Skeuomorphic EQ/VOL only on Pictured — lock the original dark plate.
            CouiAccentOverlay.forceDarkChrome(this)
            CouiAccentOverlay.apply(this, themeRepo.getAccentColor())
        }

        binding = ActivityEqualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tipGuard = EqualizerEnableTipGuard(this)

        setupInsets()
        setupPager()
        setupActions()

        applyTheme()
        if (picturedStyle) paintEqualizerDarkPlate()
    }

    override fun prefersLightSystemBars(palette: ThemePalette): Boolean {
        return if (EqualizerUiStyle.isPictured(palette)) {
            false
        } else {
            super.prefersLightSystemBars(palette)
        }
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        val nowPictured = EqualizerUiStyle.isPictured(palette)
        if (nowPictured != picturedStyle) {
            recreate()
            return
        }
        if (nowPictured) {
            CouiAccentOverlay.forceDarkChrome(this)
            CouiAccentOverlay.apply(this, themeRepo.getAccentColor())
        }
        super.onThemeChanged(palette)
        if (::binding.isInitialized && nowPictured) {
            paintEqualizerDarkPlate()
        }
    }

    override fun onResume() {
        super.onResume()
        equalizerFragment.reloadFromSettings()
    }

    override fun onDestroy() {
        tabMediator?.detach()
        tabMediator = null
        equalWidthListener?.let(binding.equalizerTabLayout::removeOnLayoutChangeListener)
        equalWidthListener = null
        tipGuard.dismissTip()
        tipGuard.clearShieldViews()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        tipGuard.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    fun equalizerTipGuard(): EqualizerEnableTipGuard = tipGuard

    fun requestPagerDisallowInterceptTouchEvent(disallow: Boolean) {
        if (!::binding.isInitialized) return
        binding.equalizerViewPager.requestDisallowInterceptTouchEvent(disallow)
    }

    fun usesPicturedStyle(): Boolean = picturedStyle

    private fun applyTheme() {
        applyThemeTo(binding.root)
    }

    private fun paintEqualizerDarkPlate() {
        val plate = ContextCompat.getColor(this, R.color.equalizer_activity_background)
        binding.root.setBackgroundColor(plate)
        window?.setBackgroundDrawable(plate.toDrawable())
        refreshSystemBarAppearance()
    }

    private fun setupInsets() {
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
    }

    private fun setupPager() {
        binding.equalizerViewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int) = if (position == 0) {
                equalizerFragment
            } else {
                soundEffectFragment
            }
        }

        tabMediator = CouiTabLayoutMediator(
            tabLayout = binding.equalizerTabLayout,
            viewPager = binding.equalizerViewPager
        ) { tab, position ->
            tab.text = if (position == 0) "EQ" else "VOL"
        }.also { mediator ->
            binding.equalizerTabLayout.visibility = View.INVISIBLE
            mediator.attach()
        }
        equalWidthListener = binding.equalizerTabLayout.installEqualWidthTabs()

        lifecycleScope.launch {
            binding.equalizerViewPager.setCurrentItem(
                soundEffectPreferences.getEqualizerLastTab().coerceIn(0, 1),
                false
            )
            binding.equalizerViewPager.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        lifecycleScope.launch {
                            soundEffectPreferences.setEqualizerLastTab(position)
                        }
                    }
                }
            )
        }
    }

    private fun setupActions() = with(binding.toolbar) {
        applyCouiLeftTitle()
        setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        inflateThemedMenu(R.menu.menu_equalizer, ::applyThemeTo)
        menu.findItem(R.id.menu_equalizer_band_type)?.isVisible =
            SoundEffectPreferences.supportsTenBandEqualizer()
        setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_equalizer_band_type) {
                showBandTypeDialog()
                true
            } else {
                false
            }
        }
    }

    private fun showBandTypeDialog() {
        if (!SoundEffectPreferences.supportsTenBandEqualizer()) return

        lifecycleScope.launch {
            val settings = loadAudioEffectSettingsUseCase()
            val selected = if (settings.useTenBand) 1 else 0
            val items = listOf(getString(R.string.use_five_band), getString(R.string.use_ten_band))

            val config = themedListDialogConfig(items).apply {
                titleText = getString(R.string.equalizer)
                selectedItemIndex = selected
                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    if (which == selected) return@OnItemClickListener
                    BaseDialog.dismissAll(this@EqualizerActivity)
                    lifecycleScope.launch {
                        soundEffectPreferences.setEqualizerBandMode(
                            if (which == 1) {
                                SoundEffectPreferences.TEN_BAND_MODE
                            } else {
                                SoundEffectPreferences.FIVE_BAND_MODE
                            }
                        )
                        playerViewModel.applyAudioEffects(this@EqualizerActivity)
                        equalizerFragment.reloadFromSettings()
                    }
                }
            }
            OptionsListDialog.show(this@EqualizerActivity, config)
        }
    }

    private fun themedListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = themeRepo.getCorePalette()
        val topCornerRadius = dpToPx(16f).toFloat()
        return OptionsListDialog.Config.create(this, items).apply {
            dimAmount = 0.5f
            cancelable = true
            canceledOnTouchOutside = true
            navigationBarColor = Color.TRANSPARENT
            backgroundDrawable = palette.getDialogSurfaceDrawable(this@EqualizerActivity)

            cornerRadii = floatArrayOf(
                topCornerRadius,
                topCornerRadius,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f
            )
            contentTopPaddingPx = 0

            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.accentColor
            itemIconRes = R.drawable.vector_single_check_selector
            itemIconPlacement = 1
        }
    }
}

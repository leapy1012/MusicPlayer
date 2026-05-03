package gd.app.musicplayer.ui.feature.equalizer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.AdapterView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.core.theme.accentColor
import gd.app.musicplayer.core.theme.messageColor
import gd.app.musicplayer.core.theme.titleColor
import gd.app.musicplayer.databinding.ActivityEqualizerBinding
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.SoundEffectPreferences
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.core.ui.dialog.BaseDialog
import gd.app.musicplayer.core.ui.dialog.OptionsListDialog
import gd.app.musicplayer.util.PreferenceUtil

@AndroidEntryPoint
class EqualizerActivity : BaseActivity() {

    private lateinit var binding: ActivityEqualizerBinding
    private val equalizerFragment = EqualizerFragment()
    private val soundEffectFragment = SoundEffectFragment()

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, EqualizerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEqualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupPager()
        setupActions()

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        equalizerFragment.reloadFromSettings()
    }

    private fun applyTheme() {
        applyThemeTo(binding.root)
    }

    private fun setupInsets() {
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
    }

    private fun setupPager() {
        val prefs = PreferenceUtil.getInstance(this)
        val lastTab = prefs.getEqualizerLastTab().coerceIn(0, 1)

        binding.equalizerViewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int) = if (position == 0) {
                equalizerFragment
            } else {
                soundEffectFragment
            }
        }

        TabLayoutMediator(binding.equalizerTabLayout, binding.equalizerViewPager) { tab, position ->
            tab.text = if (position == 0) "EQ" else "VOL"
        }.attach()

        binding.equalizerViewPager.setCurrentItem(lastTab, false)
        binding.equalizerTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                prefs.setEqualizerLastTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupActions() {
        binding.equalizerBack.navigateBack(this)
        binding.equalizerType.setOnClickListener {
            if (SoundEffectPreferences.supportsTenBandEqualizer()) {
                showBandTypeDialog()
            }
        }
    }

    private fun showBandTypeDialog() {
        if (!SoundEffectPreferences.supportsTenBandEqualizer()) return

        val settings = AudioEffectsManager.loadSettings(this)
        val selected = if (settings.useTenBand) 1 else 0
        val items = listOf(getString(R.string.use_five_band), getString(R.string.use_ten_band))

        val config = themedListDialogConfig(items).apply {
            titleText = getString(R.string.equalizer)
            selectedItemIndex = selected
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                if (which == selected) return@OnItemClickListener
                BaseDialog.dismissAll(this@EqualizerActivity)
                val updated = settings.copy(useTenBand = which == 1)
                AudioEffectsManager.saveSettings(this@EqualizerActivity, updated)
                PlaybackGateway.applyAudioEffects(this@EqualizerActivity)
                equalizerFragment.reloadFromSettings()
            }
        }
        OptionsListDialog.show(this, config)
    }

    private fun themedListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = appDependencies.themeRepo.getCorePalette(this)
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
            contentTopPaddingPx  = 0

            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.accentColor
            itemIconRes = R.drawable.vector_single_check_selector
            itemIconPlacement = 1
        }
    }
}


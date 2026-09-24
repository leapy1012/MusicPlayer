package gd.app.musicplayer.ui.shell

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.view.translucent.NavigationBarColorHost
import gd.app.musicplayer.databinding.FragmentMoreBinding
import gd.app.musicplayer.feature.equalizer.EqualizerActivity
import gd.app.musicplayer.feature.library.hidden.HiddenFoldersActivity
import gd.app.musicplayer.feature.setting.SettingActivity
import gd.app.musicplayer.feature.widget.WidgetActivity
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.drivemode.DriveModeLauncher
import gd.app.musicplayer.ui.scan.ScanMusicActivity
import gd.app.musicplayer.ui.sleep.SleepActivity
import gd.app.musicplayer.ui.theme.ThemeActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MoreFragment : ViewBindingFragment<FragmentMoreBinding>(), DrawerLayout.DrawerListener {
    private val viewModel: MoreViewModel by viewModels()
    @Inject lateinit var driveModeLauncher: DriveModeLauncher

    private var drawerLayout: DrawerLayout? = null

    override fun onCreateBinding(inflater: LayoutInflater): FragmentMoreBinding =
        FragmentMoreBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentMoreBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        drawerLayout = (activity as? MainActivity)?.drawerLayout()?.also {
            it.addDrawerListener(this)
        }

        // Original MoreFragment (l5.j0): center the "Music Player" logo with topMargin =
        // status-bar height so it sits visually correct under the system bar.
        positionDrawerTitle(binding)

        binding.slidingmenuScan.setOnClickListener {
            ScanMusicActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuEqualizer.setOnClickListener {
            EqualizerActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuSkin.setOnClickListener {
            ThemeActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuWidget.setOnClickListener {
            WidgetActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuSleep.setOnClickListener {
            SleepActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuDriveMode.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                driveModeLauncher.start(requireContext())
            }
            closeDrawer()
        }
        binding.slidingmenuHiddenFolders.setOnClickListener {
            HiddenFoldersActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuSetting.setOnClickListener {
            SettingActivity.start(requireContext())
            closeDrawer()
        }
        binding.slidingmenuQuit.setOnClickListener {
            closeDrawer()
            (activity as? MainActivity)?.showQuitConfirmDialog()
        }
        binding.slidingmenuModel.setOnClickListener {
            viewModel.onPlayModeClicked()
        }

        observeDrawerState()
        updateNavigationBarOverlay()
    }

    override fun onDestroyBinding(binding: FragmentMoreBinding) {
        drawerLayout?.removeDrawerListener(this)
        drawerLayout = null
    }

    override fun onThemeChanged(palette: gd.app.musicplayer.core.designsystem.theme.ThemePalette?) {
        super.onThemeChanged(palette)
        updateNavigationBarOverlay()
    }

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit

    override fun onDrawerOpened(drawerView: View) = Unit

    override fun onDrawerClosed(drawerView: View) = Unit

    override fun onDrawerStateChanged(newState: Int) = Unit

    private fun observeDrawerState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MoreUiState) {
        val binding = requireBinding()
        binding.slidingmenuModelImage.setImageResource(state.playModeIconRes)
        binding.slidingmenuModelText.setText(state.playModeLabelRes)
        binding.slidingmenuSleepTime.text = state.sleepSummary
        binding.slidingmenuHiddenFolders.isVisible = state.isHiddenFoldersVisible
        binding.slidingmenuEqualizerText.text = state.equalizerSummary
    }

    private fun closeDrawer() {
        drawerLayout?.closeDrawer(GravityCompat.START)
    }

    private fun positionDrawerTitle(binding: FragmentMoreBinding) {
        val horizontalMargin = (resources.displayMetrics.density * 16f).toInt()
        val titleHeight = (resources.displayMetrics.density * 40f).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainMoreContent) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            binding.slidingmenuIcon.updateLayoutParams<FrameLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = titleHeight
                gravity = Gravity.CENTER
                topMargin = statusBarHeight
                leftMargin = horizontalMargin
                rightMargin = horizontalMargin
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.mainMoreContent)
    }

    private fun updateNavigationBarOverlay() {
        val palette = themeEngine.currentTheme()
        val overlayColor = if (palette.isContentSurfaceLight()) 0 else 0x1A000000
        (binding?.mainMoreContent as? NavigationBarColorHost)?.setNavigationBarColor(overlayColor)
    }
}

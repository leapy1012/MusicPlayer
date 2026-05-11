package gd.app.musicplayer.ui.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import androidx.core.view.isVisible
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import gd.app.musicplayer.databinding.FragmentMoreBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.drivemode.DriveModeLauncher
import gd.app.musicplayer.ui.equalizer.EqualizerActivity
import gd.app.musicplayer.ui.scan.ScanMusicActivity
import gd.app.musicplayer.ui.setting.SettingActivity
import gd.app.musicplayer.ui.library.hidden.HiddenFoldersActivity
import gd.app.musicplayer.ui.sleep.SleepActivity
import gd.app.musicplayer.ui.theme.ThemeActivity
import gd.app.musicplayer.ui.widget.WidgetActivity
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
            requireActivity().finishAffinity()
        }
        binding.slidingmenuModel.setOnClickListener {
            viewModel.onPlayModeClicked()
        }

        observeDrawerState()
    }


    override fun onDestroyBinding(binding: FragmentMoreBinding) {
        drawerLayout?.removeDrawerListener(this)
        drawerLayout = null
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

}

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
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.FragmentMoreBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.drivemode.DriveModeActivity
import gd.app.musicplayer.feature.equalizer.EqualizerActivity
import gd.app.musicplayer.feature.scan.ScanMusicActivity
import gd.app.musicplayer.feature.setting.SettingActivity
import gd.app.musicplayer.feature.sleep.SleepActivity
import gd.app.musicplayer.feature.widget.WidgetActivity
import gd.app.musicplayer.ui.hidden.HiddenFoldersActivity
import gd.app.musicplayer.ui.theme.ThemeActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MoreFragment : ViewBindingFragment<FragmentMoreBinding>(), DrawerLayout.DrawerListener {
    private val viewModel: MoreViewModel by viewModels()

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
            DriveModeActivity.start(requireContext())
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
        binding.slidingmenuModelImage.setImageResource(R.drawable.vector_mode_order)
        binding.slidingmenuModelText.setText(state.playModeLabelRes)
        binding.slidingmenuSleepTime.text = state.sleepSummary
        binding.slidingmenuHiddenFolders.isVisible = state.isHiddenFoldersVisible
        binding.slidingmenuEqualizerText.text = state.equalizerSummary
    }

    private fun closeDrawer() {
        drawerLayout?.closeDrawer(GravityCompat.START)
    }

}

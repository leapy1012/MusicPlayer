package gd.app.musicplayer.ui.shell

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
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
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.lib.view.translucent.NavigationBarColorHost
import gd.app.musicplayer.databinding.FragmentMoreBinding
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.drivemode.DriveModeLauncher
import gd.app.musicplayer.feature.equalizer.EqualizerActivity
import gd.app.musicplayer.ui.scan.ScanMusicActivity
import gd.app.musicplayer.feature.setting.SettingActivity
import gd.app.musicplayer.feature.library.hidden.HiddenFoldersActivity
import gd.app.musicplayer.ui.sleep.SleepActivity
import gd.app.musicplayer.ui.theme.ThemeActivity
import gd.app.musicplayer.feature.widget.WidgetActivity
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MoreFragment : ViewBindingFragment<FragmentMoreBinding>(), DrawerLayout.DrawerListener {
    private val viewModel: MoreViewModel by viewModels()
    @Inject lateinit var driveModeLauncher: DriveModeLauncher
    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

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
            closeDrawer()
            showQuitDialog()
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

    private fun showQuitDialog() {
        val activity = requireActivity()
        val config = materialDialogConfigFactory
            .createMaterialMessageDialogConfig(activity)
            .apply {
                messageText = getString(R.string.adv_quit_message)
                positiveButtonText = getString(R.string.adv_quit_confirm)
                negativeButtonText = getString(R.string.adv_quit_cancel)
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    dialog.dismiss()
                    quitApplication()
                }
            }

        MessageDialog.show(activity, config)
    }

    private fun updateNavigationBarOverlay() {
        val palette = themeEngine.currentTheme()
        val overlayColor = if (palette.isContentSurfaceLight()) 0 else 0x1A000000
        (binding?.mainMoreContent as? NavigationBarColorHost)?.setNavigationBarColor(overlayColor)
    }

    private fun quitApplication() {
        val activity = requireActivity()
        val appContext = activity.applicationContext
        val serviceIntent = Intent(appContext, MusicPlaybackService::class.java).apply {
            action = PlaybackServiceActions.ACTION_EXIT
        }

        appContext.startService(serviceIntent)
        activity.finishAffinity()

        Handler(Looper.getMainLooper()).postDelayed(
            {
                Process.killProcess(Process.myPid())
            },
            150L
        )
    }
}

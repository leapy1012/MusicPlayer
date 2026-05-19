package gd.app.musicplayer.ui.setting

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.dialog.DialogRegistry
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.core.designsystem.view.PreferenceItemView
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.databinding.ActivitySettingBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.duplicate.ActivityDuplicatedFinder
import gd.app.musicplayer.ui.lyrics.StatusBarLyricsActivity
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import gd.app.musicplayer.ui.theme.SelectAccentColorDialog
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingBinding

    private val viewModel: SettingsViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private var pendingBluetoothAutoStartEnable = false
    private var pendingNotificationBarEnable = false
    private var pendingOldNotificationEnable = false
    private var suppressFadeSeekCallback = false

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingBluetoothAutoStartEnable) {
                viewModel.setBluetoothAutoStartEnabled(true)
            } else {
                binding.preferenceBluetoothAutoStart.isSelected = false
                if (pendingBluetoothAutoStartEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    showBluetoothPermissionSettingsDialog()
                }
            }
            pendingBluetoothAutoStartEnable = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupFragmentResultListeners()
        setupClickListeners()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        binding.preferenceShowDeskLrc.resumeDesktopLyricsAfterOverlayPermissionChange()
        binding.preferenceShowDeskLrc.disableDesktopLyricsIfOverlayPermissionWasRevoked()
    }

    override fun onNotificationPermissionResult() {
        super.onNotificationPermissionResult()

        val granted = hasNotificationPermission()
        when {
            pendingNotificationBarEnable -> viewModel.setNotificationBarEnabled(granted)
            pendingOldNotificationEnable && granted -> viewModel.setOldNotificationEnabled(true)
        }

        if (!granted) {
            binding.preferenceUseNotification.isSelected = false
            binding.preferenceUseOldNotification.isSelected = false
            openAppNotificationSettings()
        }

        pendingNotificationBarEnable = false
        pendingOldNotificationEnable = false
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.skinLayout,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.skinLayout,
            toolbar = binding.toolbar,
            titleRes = R.string.settings
        )
    }

    private fun setupFragmentResultListeners() {
        supportFragmentManager.setFragmentResultListener(
            ShakeLevelDialogFragment.RESULT_KEY,
            this
        ) { _, bundle ->
            viewModel.setShakeLevel(bundle.getFloat(ShakeLevelDialogFragment.KEY_SHAKE_LEVEL))
        }

        supportFragmentManager.setFragmentResultListener(
            SelectAccentColorDialog.RESULT_KEY,
            this
        ) { _, bundle ->
            if (bundle.containsKey(SelectAccentColorDialog.RESULT_COLOR)) {
                themeRepo.updateAccentColor(bundle.getInt(SelectAccentColorDialog.RESULT_COLOR))
            }
        }

        supportFragmentManager.setFragmentResultListener(
            ReplayGainPreampDialogFragment.RESULT_KEY,
            this
        ) { _, bundle ->
            viewModel.setReplayGainPreamp(
                withTag = bundle.getFloat(ReplayGainPreampDialogFragment.KEY_WITH_TAG),
                withoutTag = bundle.getFloat(ReplayGainPreampDialogFragment.KEY_WITHOUT_TAG)
            )
            playerViewModel.applyPlaybackTuning(this)
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: SettingsUiState) {
        binding.preferenceUseTenBands.isSelected = state.useTenBand
        setEnabledState(binding.preferenceUseTenBands, state.useTenBandAvailable)
        binding.preferenceShowHiddenFolders.isSelected = state.showHiddenFolders
        binding.preferenceDarkMode.isSelected = state.darkModeEnabled
        binding.preferenceShowForwardBackward.isSelected = state.showForwardBackward
        binding.preferenceTimeForwardBackward.setSummaryOn(formatSecondsLabel(state.forwardBackwardSeconds))
        setEnabledState(binding.preferenceTimeForwardBackward, state.showForwardBackward)
        binding.preferenceQueueForSearching.setSummaryOn(state.queueForSearchingLabel)

        binding.preferenceBluetoothLyric.isSelected = state.bluetoothLyricEnabled

        binding.preferenceShakeChangeMusic.isSelected = state.shakeEnabled
        binding.preferenceShakeLevel.visibility = if (state.shakeEnabled) View.VISIBLE else View.GONE
        binding.preferenceShakeLevelDivider.visibility = if (state.shakeEnabled) View.VISIBLE else View.GONE
        binding.preferenceShakeLevel.setSummaryOn(state.shakeLevelLabel)

//        binding.preferenceSwipeChangeSongs.isSelected = state.swipeChangeSongsEnabled
        binding.preferenceSimultaneousPlay.isSelected = state.simultaneousPlayEnabled
        binding.preferenceVolumeFade.isSelected = state.volumeFadeEnabled
        binding.preferenceGaplessPlayback.isSelected = state.gaplessPlaybackEnabled
        binding.preferenceCrossFade.isSelected = state.crossFadeEnabled
        renderFadeControls(state.crossFadeEnabled)
        binding.preferenceFadeSeekBar.setMax(11)
        suppressFadeSeekCallback = true
        binding.preferenceFadeSeekBar.setProgress(state.fadeDurationSeconds - 1)
        suppressFadeSeekCallback = false
        binding.preferenceFadeSeekText.text = formatSecondsLabel(state.fadeDurationSeconds)
        binding.preferenceTrackClickOperation.isSelected = state.trackClickOperationEnabled
        binding.preferenceReplaySong.isSelected = state.replaySongEnabled

        binding.preferenceReplayGainMode.setSummaryOn(state.replayGainModeLabel)
        binding.preferenceReplayGainPreamp.setSummaryOn(state.replayGainPreampLabel)

        binding.preferenceClickAddQueue.isSelected = state.clickAddQueueEnabled
        binding.preferencePlaylistAddPosition.setTips(state.playlistAddPositionLabel)
        binding.preferencePlaylistTrackLimit.setTips(state.playlistTrackLimitLabel)

        binding.preferenceUseNotification.isSelected = state.notificationBarEnabled
        binding.preferenceUseOldNotification.isSelected = state.oldNotificationEnabled
        binding.preferenceUseColorNotification.isSelected = state.colorNotificationEnabled
        binding.preferenceShowDeskLrc.render(state.desktopLyricPreference)
        binding.preferenceStatusBarLyrics.setTips(
            if (state.statusBarLyricPreference.enabled) {
                R.string.sbar_lyric_opened
            } else {
                R.string.sbar_lyric_closed
            }
        )
        setEnabledState(
            binding.preferenceUseColorNotification,
            state.colorNotificationEnabledAvailable
        )

        binding.preferenceLockScreen.isSelected = state.lockScreenEnabled
        binding.preferenceLockBackground.setSummaryOn(state.lockBackgroundLabel)

        binding.preferenceHeadsetInPlay.isSelected = state.headsetInPlayEnabled
        binding.preferenceHeadsetOutStop.isSelected = state.headsetOutStopEnabled
        binding.preferenceBluetoothAutoStart.isSelected = state.bluetoothAutoStartEnabled
        binding.preferenceBluetoothAutoStop.isSelected = state.bluetoothAutoStopEnabled
        binding.preferenceHeadsetControlAllow.isSelected = state.headsetControlAllowed
    }

    private fun setupClickListeners() {
        binding.preferenceUseTenBands.onPreferenceChanged {
            viewModel.setUseTenBand(it)
        }
        binding.preferenceShowHiddenFolders.onPreferenceChanged {
            viewModel.setShowHiddenFolders(it)
        }
        binding.preferenceDarkMode.onPreferenceChanged {
            viewModel.setDarkModeEnabled(it)
        }
        binding.preferenceShowForwardBackward.onPreferenceChanged {
            viewModel.setShowForwardBackward(it)
        }
        binding.preferenceTimeForwardBackward.setOnClickListener {
            showForwardBackwardDialog()
        }
        binding.preferenceKeepAliveBackground.setOnClickListener {
            openKeepAliveSettings()
        }
        binding.preferenceQueueForSearching.setOnClickListener {
            showQueueForSearchingDialog()
        }
        binding.preferenceAccentColor.setOnClickListener {
            SelectAccentColorDialog.newInstance(
                themeRepo.getAccentColor()
            ).show(supportFragmentManager, SelectAccentColorDialog.TAG)
        }
        binding.preferenceLibraryOrder.setOnClickListener {
            LibraryTabManagerDialog().show(
                supportFragmentManager,
                LibraryTabManagerDialog::class.java.simpleName
            )
        }
        binding.preferenceFindDuplicate.setOnClickListener {
            ActivityDuplicatedFinder.start(this)
        }

        binding.preferenceBluetoothLyric.onPreferenceChanged {
            viewModel.setBluetoothLyricEnabled(it)
        }
        binding.preferenceStatusBarLyrics.setOnClickListener {
            StatusBarLyricsActivity.start(this)
        }

        binding.preferenceShakeChangeMusic.onPreferenceChanged {
            viewModel.setShakeEnabled(it)
        }
        binding.preferenceShakeLevel.setOnClickListener {
            ShakeLevelDialogFragment
                .newInstance(viewModel.uiState.value.shakeLevel)
                .show(supportFragmentManager, ShakeLevelDialogFragment::class.java.simpleName)
        }
//        binding.preferenceSwipeChangeSongs.onPreferenceChanged {
//            viewModel.setSwipeChangeSongsEnabled(it)
//        }
        binding.preferenceSimultaneousPlay.onPreferenceChanged {
            viewModel.setSimultaneousPlayEnabled(it)
            playerViewModel.applyPlaybackTuning(this)
        }
        binding.preferenceVolumeFade.onPreferenceChanged {
            viewModel.setVolumeFadeEnabled(it)
            playerViewModel.applyPlaybackTuning(this)
        }
        binding.preferenceGaplessPlayback.onPreferenceChanged {
            viewModel.setGaplessPlaybackEnabled(it)
            playerViewModel.applyPlaybackTuning(this)
        }
        binding.preferenceCrossFade.onPreferenceChanged {
            viewModel.setCrossFadeEnabled(it)
            playerViewModel.applyPlaybackTuning(this)
        }
        binding.preferenceFadeSeekBar.setOnSeekBarChangeListener(fadeSeekListener)
        binding.preferenceTrackClickOperation.onPreferenceChanged {
            viewModel.setTrackClickOperationEnabled(it)
        }
        binding.preferenceReplaySong.onPreferenceChanged {
            viewModel.setReplaySongEnabled(it)
        }

        binding.preferenceReplayGainMode.setOnClickListener {
            showReplayGainModeDialog()
        }
        binding.preferenceReplayGainPreamp.setOnClickListener {
            ReplayGainPreampDialogFragment.newInstance(
                withTag = viewModel.uiState.value.replayGainPreampWithTag,
                withoutTag = viewModel.uiState.value.replayGainPreampWithoutTag
            ).show(
                supportFragmentManager,
                ReplayGainPreampDialogFragment::class.java.simpleName
            )
        }

        binding.preferenceClickAddQueue.onPreferenceChanged {
            viewModel.setClickAddQueueEnabled(it)
        }
        binding.preferencePlaylistAddPosition.setOnClickListener {
            showPlaylistAddPositionDialog()
        }
        binding.preferencePlaylistTrackLimit.setOnClickListener {
            showSmartPlaylistLimitDialog()
        }

        binding.preferenceUseNotification.onPreferenceChanged(::onNotificationBarChanged)
        binding.preferenceUseOldNotification.onPreferenceChanged(::onOldNotificationChanged)
        binding.preferenceUseColorNotification.onPreferenceChanged {
            viewModel.setColorNotificationEnabled(it)
            playerViewModel.refreshNotificationStyle(this)
        }
        binding.preferenceShowDeskLrc.onVisibleChanged = { visible ->
            viewModel.setDesktopLyricsVisible(visible)
            playerViewModel.refreshNotificationStyle(this)
        }
        binding.preferenceShowDeskLrc.onLockedChanged = { locked ->
            viewModel.setDesktopLyricsLocked(locked)
            playerViewModel.refreshNotificationStyle(this)
        }
        binding.preferenceShowDeskLrc.onPendingEnableAfterPermissionChanged = { pending ->
            viewModel.setDesktopLyricsPendingEnableAfterPermission(pending)
        }

        binding.preferenceLockScreen.onPreferenceChanged {
            viewModel.setLockScreenEnabled(it)
        }
        binding.preferenceLockBackground.setOnClickListener {
            showLockBackgroundDialog()
        }

        binding.preferenceHeadsetInPlay.onPreferenceChanged {
            viewModel.setHeadsetInPlayEnabled(it)
        }
        binding.preferenceHeadsetOutStop.onPreferenceChanged {
            viewModel.setHeadsetOutStopEnabled(it)
        }
        binding.preferenceBluetoothAutoStart.onPreferenceChanged(::onBluetoothAutoStartChanged)
        binding.preferenceBluetoothAutoStop.onPreferenceChanged {
            viewModel.setBluetoothAutoStopEnabled(it)
        }
        binding.preferenceHeadsetControlAllow.onPreferenceChanged {
            viewModel.setHeadsetControlAllowed(it)
        }
    }

    private val fadeSeekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            val seconds = progress + 1
            binding.preferenceFadeSeekText.text = formatSecondsLabel(seconds)
            if (fromUser && !suppressFadeSeekCallback) {
                viewModel.setFadeDurationSeconds(seconds)
                playerViewModel.applyPlaybackTuning(this@SettingActivity)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    private fun onNotificationBarChanged(enabled: Boolean) {
        if (!enabled) {
            viewModel.setNotificationBarEnabled(false)
            return
        }

        if (!hasNotificationPermission()) {
            pendingNotificationBarEnable = true
            binding.preferenceUseNotification.isSelected = false
            requestNotificationPermission()
            return
        }

        viewModel.setNotificationBarEnabled(true)
    }

    private fun onOldNotificationChanged(enabled: Boolean) {
        if (!enabled) {
            viewModel.setOldNotificationEnabled(false)
            playerViewModel.refreshNotificationStyle(this)
            return
        }

        if (!hasNotificationPermission()) {
            pendingOldNotificationEnable = true
            binding.preferenceUseOldNotification.isSelected = false
            requestNotificationPermission()
            return
        }

        viewModel.setOldNotificationEnabled(true)
        playerViewModel.refreshNotificationStyle(this)
    }

    private fun onBluetoothAutoStartChanged(enabled: Boolean) {
        if (!enabled) {
            viewModel.setBluetoothAutoStartEnabled(false)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
            pendingBluetoothAutoStartEnable = true
            binding.preferenceBluetoothAutoStart.isSelected = false
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        viewModel.setBluetoothAutoStartEnabled(true)
    }

    private fun showForwardBackwardDialog() {
        val values = intArrayOf(5, 10, 15, 20, 30, 60)
        val labels = values.map(::formatSecondsLabel)
        val checkedIndex = values.indexOf(viewModel.uiState.value.forwardBackwardSeconds)
            .takeIf { it >= 0 } ?: 2

        showSingleChoiceDialog(
            title = getString(R.string.select_time),
            labels = labels,
            checkedIndex = checkedIndex
        ) { which ->
            viewModel.setForwardBackwardSeconds(values[which])
        }
    }

    private fun showQueueForSearchingDialog() {
        showSingleChoiceDialog(
            title = getString(R.string.queue_for_searching),
            labels = listOf(
                getString(R.string.queue_all_songs),
                getString(R.string.queue_search_result)
            ),
            checkedIndex = viewModel.uiState.value.queueForSearchingMode.coerceIn(0, 1)
        ) { which ->
            viewModel.setQueueForSearchingMode(which)
        }
    }

    private fun showReplayGainModeDialog() {
        showSingleChoiceDialog(
            title = getString(R.string.replay_gain_mode),
            labels = listOf(
                getString(R.string.replay_gain_none),
                getString(R.string.replay_gain_track),
                getString(R.string.replay_gain_album)
            ),
            checkedIndex = viewModel.uiState.value.replayGainMode.coerceIn(0, 2)
        ) { which ->
            viewModel.setReplayGainMode(which)
            playerViewModel.applyPlaybackTuning(this)
        }
    }

    private fun showPlaylistAddPositionDialog() {
        showSingleChoiceDialog(
            title = getString(R.string.add_music_position),
            labels = listOf(
                getString(R.string.add_music_position_top),
                getString(R.string.add_music_position_end)
            ),
            checkedIndex = viewModel.uiState.value.playlistAddPosition.coerceIn(0, 1)
        ) { which ->
            viewModel.setPlaylistAddPosition(which)
        }
    }

    private fun showSmartPlaylistLimitDialog() {
        val labels = listOf(
            getString(R.string.playlist_limit_day),
            getString(R.string.playlist_limit_week),
            getString(R.string.playlist_limit_month),
            getString(R.string.playlist_limit_month_3),
            getString(R.string.playlist_limit_month_6),
            getString(R.string.playlist_limit_year),
            getString(R.string.playlist_limit_forever),
            getString(R.string.playlist_limit_custom)
        )

        showSingleChoiceDialog(
            title = getString(R.string.playlist_track_limit),
            labels = labels,
            checkedIndex = viewModel.uiState.value.smartPlaylistSelectionIndex.coerceIn(0, 7)
        ) { which ->
            if (which == 7) {
                showSmartPlaylistCustomLimitDialog()
            } else {
                viewModel.setSmartPlaylistSelection(which)
            }
        }
    }

    private fun showSmartPlaylistCustomLimitDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(viewModel.uiState.value.smartPlaylistCustomLimit.takeIf { it > 0 }?.toString().orEmpty())
            setSelection(text?.length ?: 0)
        }

        showMessageDialog(
            createMessageDialogConfig(
                title = getString(R.string.playlist_limit_custom),
                customView = input,
                negativeText = getString(android.R.string.cancel),
                positiveText = getString(android.R.string.ok),
                positiveClickListener = { dialog, _ ->
                    val limit = input.text?.toString()?.toIntOrNull()
                    if (limit == null || limit <= 0) {
                        ToastUtil.show(this, Toast.LENGTH_SHORT, getString(R.string.input_error))
                    } else {
                        dialog.dismiss()
                        viewModel.setSmartPlaylistSelection(7, limit)
                    }
                }
            )
        )
    }

    private fun showLockBackgroundDialog() {
        showSingleChoiceDialog(
            title = getString(R.string.lock_screen_background),
            labels = listOf(
                getString(R.string.lock_screen_theme),
                getString(R.string.lock_screen_artwork)
            ),
            checkedIndex = viewModel.uiState.value.lockBackgroundMode.coerceIn(0, 1)
        ) { which ->
            viewModel.setLockBackgroundMode(which)
        }
    }

    private fun showSingleChoiceDialog(
        title: String,
        labels: List<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val config = materialDialogConfigFactory
            .createMaterialListDialogConfig(this, labels)
            .apply {
                titleText = title
                selectedItemIndex = checkedIndex
                itemIconRes = R.drawable.vector_single_check_selector
                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    DialogRegistry.dismissAll(this@SettingActivity)
                    onSelected(which)
                }
            }

        OptionsListDialog.show(this, config)
    }

    private fun openKeepAliveSettings() {
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetailsSettings()
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ToastUtil.show(this, Toast.LENGTH_SHORT, getString(R.string.permission_open_failed))
        }
    }

    private fun showBluetoothPermissionSettingsDialog() {
        openAppDetailsSettings()
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun renderFadeControls(enabled: Boolean) {
        binding.preferenceFadeSeekLayout.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.preferenceFadeSeekBar.isEnabled = enabled
    }

    private fun setEnabledState(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
    }

    private fun formatSecondsLabel(seconds: Int): String {
        return "$seconds${getString(R.string.seconds)}"
    }

    private fun PreferenceItemView.onPreferenceChanged(block: (Boolean) -> Unit) {
        setOnPreferenceChangedListener { _, enabled -> block(enabled) }
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, SettingActivity::class.java))
        }
    }
}

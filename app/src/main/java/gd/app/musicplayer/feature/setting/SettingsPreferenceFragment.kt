package gd.app.musicplayer.feature.setting

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.darkmode.COUIDarkModeUtil
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.coui.appcompat.preference.COUIJumpPreference
import com.coui.appcompat.preference.COUIPreferenceFragment
import com.coui.appcompat.preference.COUISwitchPreference
import com.coui.appcompat.reddot.COUIHintRedDot
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.CouiAlertDialogSurface
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.feature.lyrics.StatusBarLyricsActivity
import gd.app.musicplayer.feature.setting.preference.DesktopLyricsPreference
import gd.app.musicplayer.feature.setting.preference.FadeSeekPreference
import gd.app.musicplayer.feature.setting.preference.SettingsMenuPreference
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.playback.lock.LockScreenController
import gd.app.musicplayer.ui.duplicate.DuplicateFinderActivity
import gd.app.musicplayer.ui.theme.SelectAccentColorDialog
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsPreferenceFragment : COUIPreferenceFragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject lateinit var lockScreenController: LockScreenController
    @Inject lateinit var themeRepo: ThemeRepo
    @Inject lateinit var playbackController: PlaybackController

    private var pendingBluetoothAutoStartEnable = false
    private var pendingNotificationBarEnable = false
    private var pendingOldNotificationEnable = false
    private var pendingLockScreenEnable = false
    private var appliedState: SettingsUiState? = null
    private var ignoringBatteryOptimizations = false

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pref = findPreference<COUISwitchPreference>(KEY_BLUETOOTH_AUTO_START)
            if (granted && pendingBluetoothAutoStartEnable) {
                viewModel.setBluetoothAutoStartEnabled(true)
            } else {
                pref?.isChecked = false
                if (pendingBluetoothAutoStartEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openAppDetailsSettings()
                }
            }
            pendingBluetoothAutoStartEnable = false
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        // The adapter is created in super.onViewCreated. Everything set before that —
        // menu entries, visibility, checked state — lands in the first bind, with no
        // hierarchy rebuild, switch animation or second layout pass.
        bindPreferenceListeners()
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations()
        renderNotificationPermissionPrompt()
        viewModel.initialStateBlocking(INITIAL_STATE_TIMEOUT_MS)?.let(::applyState)
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        // Default COUI preference list (same as DuraSpeed / COUIPreferenceFragment).
        val recyclerView = inflater.inflate(
            com.coui.appcompat.R.layout.coui_preference_recyclerview,
            parent,
            false
        ) as COUIRecyclerView
        recyclerView.layoutManager = onCreateLayoutManager()
        COUIDarkModeUtil.setForceDarkAllow(recyclerView, false)
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFragmentResultListeners()
        observeUiState()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        // DuraSpeed binds after activity + list are attached.
        (activity as? SettingActivity)?.bindCouiDivider(listView)
    }

    override fun onResume() {
        super.onResume()
        renderPostAnimationResumeWork()
    }

    fun onNotificationPermissionResult() {
        val granted = hasNotificationPermission()
        when {
            pendingNotificationBarEnable -> viewModel.setNotificationBarEnabled(granted)
            pendingOldNotificationEnable && granted -> viewModel.setOldNotificationEnabled(true)
        }
        if (!granted) {
            findPreference<COUISwitchPreference>(KEY_USE_NOTIFICATION)?.isChecked = false
            findPreference<COUISwitchPreference>(KEY_OLD_NOTIFICATION)?.isChecked = false
            openAppNotificationSettings()
        }
        pendingNotificationBarEnable = false
        pendingOldNotificationEnable = false
    }

    private fun bindPreferenceListeners() {
        switchPref(KEY_USE_TEN_BAND)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setUseTenBand(newValue as Boolean)
            true
        }
        switchPref(KEY_SHOW_HIDDEN_FOLDERS)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setShowHiddenFolders(newValue as Boolean)
            true
        }
        switchPref(KEY_DARK_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setDarkModeEnabled(newValue as Boolean)
            true
        }
        switchPref(KEY_SHOW_FORWARD_BACKWARD)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setShowForwardBackward(newValue as Boolean)
            true
        }
        bindMenuPreferences()
        jumpPref(KEY_KEEP_ALIVE)?.setOnPreferenceClickListener {
            onKeepAliveBackgroundClicked()
            true
        }
        jumpPref(KEY_ACCENT_COLOR)?.setOnPreferenceClickListener {
            SelectAccentColorDialog.newInstance(themeRepo.getAccentColor())
                .show(parentFragmentManager, SelectAccentColorDialog.TAG)
            true
        }
        jumpPref(KEY_LIBRARY_ORDER)?.setOnPreferenceClickListener {
            LibraryTabManagerDialog().show(
                parentFragmentManager,
                LibraryTabManagerDialog::class.java.simpleName
            )
            true
        }
        jumpPref(KEY_FIND_DUPLICATE)?.setOnPreferenceClickListener {
            DuplicateFinderActivity.start(requireContext())
            true
        }

        deskLrcPref()?.apply {
            onVisibleChanged = { visible ->
                viewModel.setDesktopLyricsVisible(visible)
                playbackController.refreshNotificationStyle()
            }
            onLockedChanged = { locked ->
                viewModel.setDesktopLyricsLocked(locked)
                playbackController.refreshNotificationStyle()
            }
            onPendingEnableAfterPermissionChanged = { pending ->
                viewModel.setDesktopLyricsPendingEnableAfterPermission(pending)
            }
        }
        switchPref(KEY_BLUETOOTH_LYRIC)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setBluetoothLyricEnabled(newValue as Boolean)
            true
        }
        jumpPref(KEY_STATUS_BAR_LYRICS)?.setOnPreferenceClickListener {
            StatusBarLyricsActivity.start(requireContext())
            true
        }

        switchPref(KEY_SHAKE)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setShakeEnabled(newValue as Boolean)
            true
        }
        jumpPref(KEY_SHAKE_LEVEL)?.setOnPreferenceClickListener {
            ShakeLevelDialogFragment
                .newInstance(currentUiState().shakeLevel)
                .show(parentFragmentManager, ShakeLevelDialogFragment::class.java.simpleName)
            true
        }
        switchPref(KEY_SIMULTANEOUS)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setSimultaneousPlayEnabled(newValue as Boolean)
            playbackController.applyPlaybackTuning()
            true
        }
        switchPref(KEY_VOLUME_FADE)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setVolumeFadeEnabled(newValue as Boolean)
            playbackController.applyPlaybackTuning()
            true
        }
        switchPref(KEY_GAPLESS)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setGaplessPlaybackEnabled(newValue as Boolean)
            playbackController.applyPlaybackTuning()
            true
        }
        switchPref(KEY_CROSS_FADE)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setCrossFadeEnabled(newValue as Boolean)
            playbackController.applyPlaybackTuning()
            true
        }
        fadeSeekPref()?.onFadeDurationChanged =
            FadeSeekPreference.OnFadeDurationChangedListener { seconds ->
                viewModel.setFadeDurationSeconds(seconds)
                playbackController.applyPlaybackTuning()
            }
        switchPref(KEY_TRACK_CLICK)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setTrackClickOperationEnabled(newValue as Boolean)
            true
        }
        switchPref(KEY_REPLAY_SONG)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setReplaySongEnabled(newValue as Boolean)
            true
        }

        jumpPref(KEY_REPLAY_GAIN_PREAMP)?.setOnPreferenceClickListener {
            ReplayGainPreampDialogFragment.newInstance(
                withTag = currentUiState().replayGainPreampWithTag,
                withoutTag = currentUiState().replayGainPreampWithoutTag
            ).show(parentFragmentManager, ReplayGainPreampDialogFragment::class.java.simpleName)
            true
        }

        switchPref(KEY_CLICK_ADD_QUEUE)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setClickAddQueueEnabled(newValue as Boolean)
            true
        }
        jumpPref(KEY_PLAYLIST_TRACK_LIMIT)?.setOnPreferenceClickListener {
            showSmartPlaylistLimitDialog()
            true
        }

        switchPref(KEY_USE_NOTIFICATION)?.setOnPreferenceChangeListener { _, newValue ->
            onNotificationBarChanged(newValue as Boolean)
            false
        }
        switchPref(KEY_OLD_NOTIFICATION)?.setOnPreferenceChangeListener { _, newValue ->
            onOldNotificationChanged(newValue as Boolean)
            false
        }
        switchPref(KEY_COLOR_NOTIFICATION)?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                viewModel.setColorNotificationEnabled(newValue as Boolean).join()
                playbackController.refreshNotificationStyle()
            }
            true
        }

        switchPref(KEY_LOCK_SCREEN)?.setOnPreferenceChangeListener { _, newValue ->
            onLockScreenPreferenceChanged(newValue as Boolean)
            false
        }

        switchPref(KEY_HEADSET_IN_PLAY)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setHeadsetInPlayEnabled(newValue as Boolean)
            true
        }
        switchPref(KEY_HEADSET_OUT_STOP)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setHeadsetOutStopEnabled(newValue as Boolean)
            true
        }
        switchPref(KEY_BLUETOOTH_AUTO_START)?.setOnPreferenceChangeListener { _, newValue ->
            onBluetoothAutoStartChanged(newValue as Boolean)
            false
        }
        switchPref(KEY_BLUETOOTH_AUTO_STOP)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setBluetoothAutoStopEnabled(newValue as Boolean)
            true
        }
        switchPref(KEY_HEADSET_CONTROL)?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setHeadsetControlAllowed(newValue as Boolean)
            true
        }
    }

    private fun setupFragmentResultListeners() {
        parentFragmentManager.setFragmentResultListener(
            ShakeLevelDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            viewModel.setShakeLevel(bundle.getFloat(ShakeLevelDialogFragment.KEY_SHAKE_LEVEL))
        }
        parentFragmentManager.setFragmentResultListener(
            SelectAccentColorDialog.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.containsKey(SelectAccentColorDialog.RESULT_COLOR)) {
                val color = bundle.getInt(SelectAccentColorDialog.RESULT_COLOR)
                // Dialog already persisted; ensure repo is in sync then recreate so
                // CouiAccentOverlay.applyStyle runs before the next setContentView.
                themeRepo.updateAccentColor(color)
                activity?.recreate()
            }
        }
        parentFragmentManager.setFragmentResultListener(
            ReplayGainPreampDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            viewModel.setReplayGainPreamp(
                withTag = bundle.getFloat(ReplayGainPreampDialogFragment.KEY_WITH_TAG),
                withoutTag = bundle.getFloat(ReplayGainPreampDialogFragment.KEY_WITHOUT_TAG)
            )
            playbackController.applyPlaybackTuning()
        }
        parentFragmentManager.setFragmentResultListener(
            SmartPlaylistLimitDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            viewModel.setSmartPlaylistSelection(
                selectionIndex = bundle.getInt(SmartPlaylistLimitDialogFragment.KEY_SELECTION_INDEX),
                customLimit = bundle.getInt(SmartPlaylistLimitDialogFragment.KEY_CUSTOM_LIMIT)
            )
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state != null) applyState(state)
                }
            }
        }
    }

    private fun applyState(state: SettingsUiState) {
        if (state == appliedState) return
        appliedState = state
        switchPref(KEY_USE_TEN_BAND)?.apply {
            isChecked = state.useTenBand
            isEnabled = state.useTenBandAvailable
        }
        switchPref(KEY_SHOW_HIDDEN_FOLDERS)?.isChecked = state.showHiddenFolders
        switchPref(KEY_DARK_MODE)?.isChecked = state.darkModeEnabled
        switchPref(KEY_SHOW_FORWARD_BACKWARD)?.isChecked = state.showForwardBackward
        menuPref(KEY_TIME_FORWARD_BACKWARD)?.apply {
            isEnabled = state.showForwardBackward
            setValue(state.forwardBackwardSeconds.toString())
            assignment = formatSecondsLabel(state.forwardBackwardSeconds)
        }
        menuPref(KEY_QUEUE_FOR_SEARCHING)?.apply {
            setValue(state.queueForSearchingMode.coerceIn(0, 1).toString())
            assignment = state.queueForSearchingLabel
        }

        switchPref(KEY_BLUETOOTH_LYRIC)?.isChecked = state.bluetoothLyricEnabled
        switchPref(KEY_SHAKE)?.isChecked = state.shakeEnabled
        jumpPref(KEY_SHAKE_LEVEL)?.apply {
            isVisible = state.shakeEnabled
            assignment = state.shakeLevelLabel
        }

        switchPref(KEY_SIMULTANEOUS)?.isChecked = state.simultaneousPlayEnabled
        switchPref(KEY_VOLUME_FADE)?.isChecked = state.volumeFadeEnabled
        switchPref(KEY_GAPLESS)?.isChecked = state.gaplessPlaybackEnabled
        switchPref(KEY_CROSS_FADE)?.isChecked = state.crossFadeEnabled
        fadeSeekPref()?.apply {
            setFadeDurationSeconds(state.fadeDurationSeconds)
            setFadeControlsEnabled(state.crossFadeEnabled)
        }
        switchPref(KEY_TRACK_CLICK)?.isChecked = state.trackClickOperationEnabled
        switchPref(KEY_REPLAY_SONG)?.isChecked = state.replaySongEnabled

        menuPref(KEY_REPLAY_GAIN_MODE)?.apply {
            setValue(state.replayGainMode.coerceIn(0, 2).toString())
            assignment = state.replayGainModeLabel
        }
        jumpPref(KEY_REPLAY_GAIN_PREAMP)?.assignment = state.replayGainPreampLabel

        switchPref(KEY_CLICK_ADD_QUEUE)?.isChecked = state.clickAddQueueEnabled
        menuPref(KEY_PLAYLIST_ADD_POSITION)?.apply {
            setValue(state.playlistAddPosition.coerceIn(0, 1).toString())
            assignment = state.playlistAddPositionLabel
        }
        jumpPref(KEY_PLAYLIST_TRACK_LIMIT)?.assignment = state.playlistTrackLimitLabel

        switchPref(KEY_OLD_NOTIFICATION)?.isChecked = state.oldNotificationEnabled
        switchPref(KEY_COLOR_NOTIFICATION)?.apply {
            isChecked = state.colorNotificationEnabled
            isEnabled = state.colorNotificationEnabledAvailable
        }

        deskLrcPref()?.render(state.desktopLyricPreference)
        jumpPref(KEY_STATUS_BAR_LYRICS)?.assignment = getString(
            if (state.statusBarLyricEnabled) {
                R.string.sbar_lyric_opened
            } else {
                R.string.sbar_lyric_closed
            }
        )

        switchPref(KEY_LOCK_SCREEN)?.isChecked = state.lockScreenEnabled
        menuPref(KEY_LOCK_BACKGROUND)?.apply {
            setValue(state.lockBackgroundMode.coerceIn(0, 1).toString())
            assignment = state.lockBackgroundLabel
        }

        switchPref(KEY_HEADSET_IN_PLAY)?.isChecked = state.headsetInPlayEnabled
        switchPref(KEY_HEADSET_OUT_STOP)?.isChecked = state.headsetOutStopEnabled
        switchPref(KEY_BLUETOOTH_AUTO_START)?.isChecked = state.bluetoothAutoStartEnabled
        switchPref(KEY_BLUETOOTH_AUTO_STOP)?.isChecked = state.bluetoothAutoStopEnabled
        switchPref(KEY_HEADSET_CONTROL)?.isChecked = state.headsetControlAllowed

        renderKeepAlive(state.showKeepAliveDot)
    }

    private fun renderPostAnimationResumeWork() {
        renderNotificationPermissionPrompt()
        deskLrcPref()?.resumeDesktopLyricsAfterOverlayPermissionChange()
        deskLrcPref()?.disableDesktopLyricsIfOverlayPermissionWasRevoked()
        resumeLockScreenAfterOverlayPermissionChange()
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations()
        renderKeepAlive(currentUiState().showKeepAliveDot)
    }

    private fun onNotificationBarChanged(enabled: Boolean) {
        if (!hasNotificationPermission()) {
            pendingNotificationBarEnable = true
            switchPref(KEY_USE_NOTIFICATION)?.isChecked = false
            requestNotificationPermission()
            return
        }
        viewModel.setNotificationBarEnabled(enabled)
        switchPref(KEY_USE_NOTIFICATION)?.isChecked = enabled
    }

    private fun onOldNotificationChanged(enabled: Boolean) {
        if (!enabled) {
            lifecycleScope.launch {
                viewModel.setOldNotificationEnabled(false).join()
                playbackController.refreshNotificationStyle()
            }
            switchPref(KEY_OLD_NOTIFICATION)?.isChecked = false
            return
        }
        if (!hasNotificationPermission()) {
            pendingOldNotificationEnable = true
            switchPref(KEY_OLD_NOTIFICATION)?.isChecked = false
            requestNotificationPermission()
            return
        }
        lifecycleScope.launch {
            viewModel.setOldNotificationEnabled(true).join()
            playbackController.refreshNotificationStyle()
        }
        switchPref(KEY_OLD_NOTIFICATION)?.isChecked = true
    }

    private fun onBluetoothAutoStartChanged(enabled: Boolean) {
        if (!enabled) {
            viewModel.setBluetoothAutoStartEnabled(false)
            switchPref(KEY_BLUETOOTH_AUTO_START)?.isChecked = false
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
            pendingBluetoothAutoStartEnable = true
            switchPref(KEY_BLUETOOTH_AUTO_START)?.isChecked = false
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        viewModel.setBluetoothAutoStartEnabled(true)
        switchPref(KEY_BLUETOOTH_AUTO_START)?.isChecked = true
    }

    private fun onLockScreenPreferenceChanged(enabled: Boolean) {
        if (enabled && needsLockScreenOverlayPermission()) {
            switchPref(KEY_LOCK_SCREEN)?.isChecked = false
            pendingLockScreenEnable = true
            showLockScreenPermissionDialog()
            return
        }
        pendingLockScreenEnable = false
        viewModel.setLockScreenEnabled(enabled)
        lockScreenController.refresh(enabled)
        switchPref(KEY_LOCK_SCREEN)?.isChecked = enabled
    }

    private fun bindMenuPreferences() {
        val forwardValues = FORWARD_BACKWARD_SECONDS
        menuPref(KEY_TIME_FORWARD_BACKWARD)?.apply {
            setEntries(forwardValues.map { formatSecondsLabel(it) }.toTypedArray())
            setEntryValues(forwardValues.map { it.toString() }.toTypedArray())
            setOnPreferenceChangeListener { pref, newValue ->
                val seconds = (newValue as? String)?.toIntOrNull()
                    ?: return@setOnPreferenceChangeListener false
                viewModel.setForwardBackwardSeconds(seconds)
                (pref as SettingsMenuPreference).assignment = formatSecondsLabel(seconds)
                true
            }
        }

        bindIndexMenuPreference(
            key = KEY_QUEUE_FOR_SEARCHING,
            entriesRes = R.array.settings_queue_for_searching_entries,
            valuesRes = R.array.settings_queue_for_searching_values
        ) { index, label ->
            viewModel.setQueueForSearchingMode(index)
            label
        }

        bindIndexMenuPreference(
            key = KEY_REPLAY_GAIN_MODE,
            entriesRes = R.array.settings_replay_gain_mode_entries,
            valuesRes = R.array.settings_replay_gain_mode_values
        ) { index, label ->
            viewModel.setReplayGainMode(index)
            playbackController.applyPlaybackTuning()
            label
        }

        bindIndexMenuPreference(
            key = KEY_PLAYLIST_ADD_POSITION,
            entriesRes = R.array.settings_playlist_add_position_entries,
            valuesRes = R.array.settings_playlist_add_position_values
        ) { index, label ->
            viewModel.setPlaylistAddPosition(index)
            label
        }

        bindIndexMenuPreference(
            key = KEY_LOCK_BACKGROUND,
            entriesRes = R.array.settings_lock_background_entries,
            valuesRes = R.array.settings_lock_background_values
        ) { index, label ->
            viewModel.setLockBackgroundMode(index)
            label
        }
    }

    private fun bindIndexMenuPreference(
        key: String,
        entriesRes: Int,
        valuesRes: Int,
        onSelected: (index: Int, label: String) -> String
    ) {
        val pref = menuPref(key) ?: return
        // String[] only — COUIMenuPreference casts entry titles to String.
        val entries = resources.getStringArray(entriesRes)
        pref.setEntries(entries)
        pref.setEntryValues(resources.getStringArray(valuesRes))
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val index = (newValue as? String)?.toIntOrNull()
                ?: return@setOnPreferenceChangeListener false
            val label = entries.getOrNull(index).orEmpty()
            (preference as SettingsMenuPreference).assignment = onSelected(index, label)
            true
        }
    }

    private fun showSmartPlaylistLimitDialog() {
        SmartPlaylistLimitDialogFragment
            .newInstance(
                selectedIndex = currentUiState().smartPlaylistSelectionIndex,
                customLimit = currentUiState().smartPlaylistCustomLimit
            )
            .show(parentFragmentManager, SmartPlaylistLimitDialogFragment::class.java.simpleName)
    }

    private fun needsLockScreenOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !Settings.canDrawOverlays(requireContext())
    }

    private fun showLockScreenPermissionDialog() {
        val message = getString(
            R.string.permission_lock_screen,
            getString(R.string.permission_des_lock_screen)
        )
        val dialog = COUIAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_screen)
            .setMessage(message)
            .setNegativeButton(R.string.cancel) { _, _ ->
                pendingLockScreenEnable = false
            }
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                if (!openOverlayPermissionSettings()) {
                    pendingLockScreenEnable = false
                    ToastUtil.show(
                        requireContext(),
                        Toast.LENGTH_SHORT,
                        getString(R.string.open_permission_failed)
                    )
                }
            }
            .show()
        CouiAlertDialogSurface.apply(dialog)
    }

    private fun resumeLockScreenAfterOverlayPermissionChange() {
        if (!pendingLockScreenEnable) return
        if (needsLockScreenOverlayPermission()) return
        pendingLockScreenEnable = false
        switchPref(KEY_LOCK_SCREEN)?.isChecked = true
        viewModel.setLockScreenEnabled(true)
        lockScreenController.refresh(true)
    }

    private fun openOverlayPermissionSettings(): Boolean {
        return runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }.isSuccess
    }

    private fun onKeepAliveBackgroundClicked() {
        viewModel.setKeepAliveTipSeen()
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations()
        renderKeepAlive(showDot = false)
        if (ignoringBatteryOptimizations) {
            ToastUtil.show(requireContext(), Toast.LENGTH_SHORT, getString(R.string.succeed))
            return
        }
        showKeepAlivePermissionDialog()
    }

    private fun showKeepAlivePermissionDialog() {
        val dialog = COUIAlertDialogBuilder(requireContext())
            .setTitle(R.string.avoid_stop_title)
            .setMessage(R.string.avoid_stop_content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                if (!openKeepAliveSettings()) {
                    ToastUtil.show(
                        requireContext(),
                        Toast.LENGTH_SHORT,
                        getString(R.string.open_permission_failed)
                    )
                }
            }
            .show()
        CouiAlertDialogSurface.apply(dialog)
    }

    private fun openKeepAliveSettings(): Boolean {
        val pkg = requireContext().packageName
        if (isMiuiDevice()) {
            val openedMiuiPowerKeeper = runCatching {
                startActivity(
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                        )
                        putExtra("package_name", pkg)
                    }
                )
            }.isSuccess
            if (openedMiuiPowerKeeper) return true
        }
        val packageUri = Uri.parse("package:$pkg")
        return runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
        }.recoverCatching {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.recoverCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = packageUri
                }
            )
        }.isSuccess
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ToastUtil.show(
                requireContext(),
                Toast.LENGTH_SHORT,
                getString(R.string.permission_open_failed)
            )
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        (activity as? SettingActivity)?.requestSettingsNotificationPermission()
    }

    private fun renderKeepAlive(showDot: Boolean) {
        val pref = jumpPref(KEY_KEEP_ALIVE) ?: return
        pref.isVisible = !ignoringBatteryOptimizations
        if (ignoringBatteryOptimizations) return
        pref.setEndRedDotMode(
            if (showDot) COUIHintRedDot.POINT_ONLY_MODE else COUIHintRedDot.NO_POINT_MODE
        )
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager =
            requireContext().getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun isMiuiDevice(): Boolean {
        return Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("xiaomi", ignoreCase = true)
    }

    private fun renderNotificationPermissionPrompt() {
        val showPermissionPrompt = !hasNotificationPermission()
        switchPref(KEY_USE_NOTIFICATION)?.apply {
            isVisible = showPermissionPrompt
            isChecked = hasNotificationPermission()
        }
    }

    private fun currentUiState(): SettingsUiState = viewModel.uiState.value ?: SettingsUiState()

    private fun formatSecondsLabel(seconds: Int): String {
        return "$seconds${getString(R.string.seconds)}"
    }

    private fun switchPref(key: String): COUISwitchPreference? =
        findPreference(key)

    private fun jumpPref(key: String): COUIJumpPreference? =
        findPreference(key)

    private fun menuPref(key: String): SettingsMenuPreference? =
        findPreference(key)

    private fun deskLrcPref(): DesktopLyricsPreference? =
        findPreference(KEY_DESK_LRC)

    private fun fadeSeekPref(): FadeSeekPreference? =
        findPreference(KEY_FADE_SEEK)

    companion object {
        // Upper bound on blocking the main thread for the first DataStore snapshot;
        // past it the list paints defaults and the collector applies values later.
        private const val INITIAL_STATE_TIMEOUT_MS = 300L
        private val FORWARD_BACKWARD_SECONDS = intArrayOf(5, 10, 15, 20, 30, 60)

        const val KEY_USE_TEN_BAND = "use_ten_band"
        const val KEY_SHOW_HIDDEN_FOLDERS = "show_hidden_folders"
        const val KEY_DARK_MODE = "preference_dark_mode"
        const val KEY_SHOW_FORWARD_BACKWARD = "show_forward_backward"
        const val KEY_TIME_FORWARD_BACKWARD = "preference_time_forward_backward"
        const val KEY_KEEP_ALIVE = "preference_keep_alive_background"
        const val KEY_QUEUE_FOR_SEARCHING = "preference_queue_for_searching"
        const val KEY_ACCENT_COLOR = "preference_accent_color"
        const val KEY_LIBRARY_ORDER = "preference_library_order"
        const val KEY_FIND_DUPLICATE = "preference_find_duplicate"
        const val KEY_DESK_LRC = "preference_show_desk_lrc"
        const val KEY_BLUETOOTH_LYRIC = "bluetooth_lyric"
        const val KEY_STATUS_BAR_LYRICS = "preference_status_bar_lyrics"
        const val KEY_SHAKE = "preference_shake_change_music"
        const val KEY_SHAKE_LEVEL = "preference_shake_level"
        const val KEY_SIMULTANEOUS = "simultaneous_play"
        const val KEY_VOLUME_FADE = "preference_volume_fade"
        const val KEY_GAPLESS = "gapless_play"
        const val KEY_CROSS_FADE = "fade_enable"
        const val KEY_FADE_SEEK = "preference_fade_seek"
        const val KEY_TRACK_CLICK = "preference_track_click_operation"
        const val KEY_REPLAY_SONG = "preference_replay_song"
        const val KEY_REPLAY_GAIN_MODE = "preference_replay_gain_mode"
        const val KEY_REPLAY_GAIN_PREAMP = "preference_replay_gain_preamp"
        const val KEY_CLICK_ADD_QUEUE = "preference_click_add_queue"
        const val KEY_PLAYLIST_ADD_POSITION = "preference_playlist_add_position"
        const val KEY_PLAYLIST_TRACK_LIMIT = "preference_playlist_track_limit"
        const val KEY_USE_NOTIFICATION = "preference_use_notification"
        const val KEY_OLD_NOTIFICATION = "old_notification"
        const val KEY_COLOR_NOTIFICATION = "color_notification"
        const val KEY_LOCK_SCREEN = "preference_lock_screen"
        const val KEY_LOCK_BACKGROUND = "preference_lock_background"
        const val KEY_HEADSET_IN_PLAY = "preference_headset_in_play"
        const val KEY_HEADSET_OUT_STOP = "preference_headset_out_stop"
        const val KEY_BLUETOOTH_AUTO_START = "preference_bluetooth_auto_start"
        const val KEY_BLUETOOTH_AUTO_STOP = "preference_bluetooth_auto_stop"
        const val KEY_HEADSET_CONTROL = "preference_headset_control_allow"
    }
}

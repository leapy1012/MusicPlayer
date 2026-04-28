package gd.app.musicplayer.ui.feature.setting

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.textfield.TextInputEditText
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivitySettingBinding
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.playback.SoundEffectPreferences
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.core.ui.dialog.OptionsListDialog
import gd.app.musicplayer.core.ui.dialog.DialogRegistry
import gd.app.musicplayer.core.ui.dialog.MessageDialog
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.ui.feature.duplicate.ActivityDuplicatedFinder
import gd.app.musicplayer.ui.feature.lyrics.ActivityStatusBarLyrics
import gd.app.musicplayer.ui.theme.SelectAccentColorDialog
import gd.app.musicplayer.util.LibraryTabConfigStore
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.ShakeDetector
import gd.app.musicplayer.util.StatusBarLyricSettings
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.core.ui.dialog.MaterialDialogConfig

class SettingActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingBinding
    private val preferenceUtil by lazy { PreferenceUtil.getInstance(this) }

    private var pendingBluetoothAutoStartEnable = false
    private var pendingNotificationPermissionRefresh = false
    private var restoredScrollPercent: Float? = null

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingBluetoothAutoStartEnable) {
                setBluetoothAutoStartEnabled(true)
            } else {
                binding.preferenceBluetoothAutoStart.setSelected(false)
                if (pendingBluetoothAutoStartEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    showBluetoothPermissionSettingsDialog()
                }
            }
            pendingBluetoothAutoStartEnable = false
        }

    companion object {
        private const val KEY_FORWARD_BACKWARD_SECONDS = "time_forward_backward"
        private const val KEY_FADE_DURATION_MS = "fade_duration"
        private const val KEY_QUEUE_FOR_SEARCHING = "queue_for_searching"
        private const val KEY_BLUETOOTH_LYRIC = "bluetooth_lyric"
        private const val KEY_SHOW_KEEP_ALIVE_DOT = "show_keep_alive_dot"
        private const val KEY_SHOW_FORWARD_BACKWARD = "show_forward_backward"
        private const val KEY_SHOW_SHUFFLE_BUTTON = "preference_show_shuffle_button"
        private const val KEY_LOCK_BACKGROUND = "lock_background"
        private const val KEY_REPLAY_GAIN_MODE = "replay_gain_mode"
        private const val KEY_REPLAY_GAIN_PREAMP_WITH_TAG = "preamp_with_tag"
        private const val KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG = "preamp_without_tag"
        private const val KEY_OLD_NOTIFICATION = "old_notification"
        private const val KEY_COLOR_NOTIFICATION = "color_notification"
        private const val KEY_NOTIFICATION_BAR_ENABLED = "use_notification_bar"
        private const val KEY_SCROLL_PERCENT = "scrollPercent"
        private const val KEY_PENDING_BLUETOOTH_AUTO_START_ENABLE =
            "pending_bluetooth_auto_start_enable"
        private const val KEY_PENDING_NOTIFICATION_PERMISSION_REFRESH =
            "pending_notification_permission_refresh"
        private val SHUFFLE_BUTTON_TARGET_IDS = listOf(0, 1, -1, -5, -4, -8, -6)

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, SettingActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pendingBluetoothAutoStartEnable =
            savedInstanceState?.getBoolean(KEY_PENDING_BLUETOOTH_AUTO_START_ENABLE, false) == true
        pendingNotificationPermissionRefresh =
            savedInstanceState?.getBoolean(
                KEY_PENDING_NOTIFICATION_PERMISSION_REFRESH,
                false
            ) == true
        restoredScrollPercent =
            savedInstanceState?.getFloat(KEY_SCROLL_PERCENT)?.takeIf { it > 0f }

        setupToolbar()
        setupFragmentResults()
        setupInteractions()
        syncState()
        restoreScrollPositionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        binding.preferenceShowDeskLrc.resumeDesktopLyricsAfterOverlayPermissionChange()
        binding.preferenceShowDeskLrc.disableDesktopLyricsIfOverlayPermissionWasRevoked()
        syncState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val scrollView = binding.settingScrollView
        val contentView = scrollView.getChildAt(0)
        if (contentView != null) {
            val contentHeight = contentView.height
            if (contentHeight > 0) {
                outState.putFloat(
                    KEY_SCROLL_PERCENT,
                    (scrollView.scrollY + scrollView.height).toFloat() / contentHeight.toFloat()
                )
            }
        }
        outState.putBoolean(
            KEY_PENDING_BLUETOOTH_AUTO_START_ENABLE,
            pendingBluetoothAutoStartEnable
        )
        outState.putBoolean(
            KEY_PENDING_NOTIFICATION_PERMISSION_REFRESH,
            pendingNotificationPermissionRefresh
        )
        super.onSaveInstanceState(outState)
    }

    override fun onNotificationPermissionResult() {
        super.onNotificationPermissionResult()
        if (hasNotificationPermission()) {
            preferenceUtil.putBooleanPreference(KEY_NOTIFICATION_BAR_ENABLED, true)
        }
        if (pendingNotificationPermissionRefresh) {
            pendingNotificationPermissionRefresh = false
            syncNotificationPreferences()
        }
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.settings
        )
    }

    private fun restoreScrollPositionIfNeeded() {
        val scrollPercent = restoredScrollPercent ?: return
        binding.settingScrollView.post {
            val scrollView = binding.settingScrollView
            val contentView = scrollView.getChildAt(0) ?: return@post
            scrollView.scrollTo(
                0,
                ((contentView.height * scrollPercent) - scrollView.height).toInt().coerceAtLeast(0)
            )
        }
        restoredScrollPercent = null
    }

    private fun setupInteractions() {
        binding.preferenceUseTenBands.setOnClickListener {
            toggleTenBandEqualizer()
        }

        binding.preferenceDarkMode.setOnClickListener {
            val next = !binding.preferenceDarkMode.isSelected
            binding.preferenceDarkMode.isSelected = next
            appDependencies.themeRepo.toggleDarkMode(this, next)
        }

        binding.preferenceTimeForwardBackward.setOnClickListener {
            showForwardBackwardDialog()
        }

        binding.preferenceShowForwardBackward.setOnPreferenceChangedListener { preferenceItemView, z10 -> syncForwardBackwardEnabledState() }

        binding.preferenceKeepAliveBackground.setOnClickListener {
            openKeepAliveSettings()
        }

        binding.preferenceQueueForSearching.setOnClickListener {
            showQueueForSearchingDialog()
        }

        binding.preferenceShuffleButton.setOnClickListener {
            ShuffleButtonSettingsDialog()
                .show(supportFragmentManager, ShuffleButtonSettingsDialog::class.java.simpleName)
        }

        binding.preferenceAccentColor.setOnClickListener {
            SelectAccentColorDialog.newInstance(preferenceUtil.getThemeColor())
                .show(supportFragmentManager, SelectAccentColorDialog.TAG)
        }

        binding.preferenceLibraryOrder.setOnClickListener {
            LibraryTabManagerDialog()
                .show(supportFragmentManager, LibraryTabManagerDialog::class.java.simpleName)
        }

        binding.preferenceFindDuplicate.setOnClickListener {
            ActivityDuplicatedFinder.start(this)
        }

        binding.preferenceBluetoothLyric.setOnClickListener {
            handleBluetoothLyricClick()
        }

        binding.preferenceStatusBarLyrics.setOnClickListener {
            ActivityStatusBarLyrics.start(this)
        }

        binding.preferenceShakeChangeMusic.setOnPreferenceChangedListener { preferenceItemView, enabled ->
            renderShakeLevel(enabled)
            binding.preferenceShakeLevelDivider.visibility =
                if (enabled) View.VISIBLE else View.GONE
            ShakeDetector.getInstance(this).setEnabled(enabled)
        }

        binding.preferenceShakeLevel.setOnClickListener {
            showShakeLevelDialog()
        }

        binding.preferenceSimultaneousPlay.setOnPreferenceChangedListener { preferenceItemView, enabled ->
            if (enabled) {
                SimultaneousTipDialog.newInstance().show(this@SettingActivity.supportFragmentManager, null)
            }
            MusicPlaybackController.applyPlaybackTuning(this)
        }

        binding.preferenceVolumeFade.setOnPreferenceChangedListener { _, enabled ->
            if (enabled) {
                binding.preferenceGaplessPlayback.isSelected = false
            }
            MusicPlaybackController.applyPlaybackTuning(this)
        }

        binding.preferenceGaplessPlayback.setOnPreferenceChangedListener { _, z10 ->
            if (z10) {
                binding.preferenceCrossFade.isSelected = false
            }
            renderFadeControls(binding.preferenceCrossFade.isSelected)
            MusicPlaybackController.applyPlaybackTuning(this)
        }

        binding.preferenceCrossFade.setOnPreferenceChangedListener { _, z10 ->
            if (z10) {
                binding.preferenceGaplessPlayback.isSelected = false
            }
            renderFadeControls(z10)
            MusicPlaybackController.applyPlaybackTuning(this)
        }

        binding.preferenceFadeSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val seconds = progress + 1
                    binding.preferenceFadeSeekText.text = formatSecondsLabel(seconds)
                    if (fromUser) {
                        preferenceUtil.putIntPreference(KEY_FADE_DURATION_MS, seconds * 1000)
                        MusicPlaybackController.applyPlaybackTuning(this@SettingActivity)
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) =
                    Unit

                override fun onStartTrackingTouch(seekBar: SeekBar) =
                    Unit
            }
        )

        binding.preferenceReplayGainMode.setOnClickListener {
            showReplayGainModeDialog()
        }

        binding.preferenceReplayGainPreamp.setOnClickListener {
            showReplayGainPreampDialog()
        }

        binding.preferencePlaylistAddPosition.setOnClickListener {
            showPlaylistAddPositionDialog()
        }

        binding.preferencePlaylistTrackLimit.setOnClickListener {
            showSmartPlaylistLimitDialog()
        }

        binding.preferenceUseNotification.setOnClickListener {
            pendingNotificationPermissionRefresh = true
            if (!hasNotificationPermission()) {
                requestNotificationPermission()
            } else {
                openAppNotificationSettings()
            }
        }

        binding.preferenceUseOldNotification.setOnClickListener {
            if (!hasNotificationPermission()) {
                pendingNotificationPermissionRefresh = true
                requestNotificationPermission()
                return@setOnClickListener
            }
            val enabled = !binding.preferenceUseOldNotification.isSelected
            preferenceUtil.putBooleanPreference(KEY_OLD_NOTIFICATION, enabled)
            syncNotificationPreferences()
            MusicPlaybackController.refreshNotificationStyle(this)
        }

        binding.preferenceUseColorNotification.setOnPreferenceChangedListener { _, z10 ->
            preferenceUtil.putBooleanPreference(
                KEY_COLOR_NOTIFICATION,
                z10
            )
            MusicPlaybackController.refreshNotificationStyle(this)
        }

//        binding.preferenceLockTimeFormat.setOnClickListener {
//            showLockTimeFormatDialog()
//        }

        binding.preferenceLockBackground.setOnClickListener {
            showLockBackgroundDialog()
        }

//        binding.preferenceLockScreen.setOnPreferenceChangedListener { preferenceItemView, z10 -> syncLockSettingEnabledState() }

        binding.preferenceBluetoothAutoStart.setOnClickListener {
            handleBluetoothAutoStartClick()
        }
    }

    private fun syncState() {
        syncTenBandState()
        syncDarkModeState()
        syncForwardBackwardSummary()
        syncForwardBackwardEnabledState()
        syncQueueForSearchingSummary()
        syncShakeLevelSummary()
        syncReplayGainModeSummary()
        syncReplayGainPreampSummary()
//        syncLockTimeFormatSummary()
        syncLockBackgroundSummary()
//        syncLockSettingEnabledState()
        syncPlaylistAddPositionSummary()
        syncShuffleButtonSummary()
        syncLibraryOrderSummary()
        syncSmartPlaylistSummary()
        syncBluetoothLyricState()
        syncStatusBarLyricState()
        syncNotificationPreferences()
        syncBluetoothAutoStartState()
        syncFadeControls()
        renderShakeLevel(binding.preferenceShakeChangeMusic.isSelected)
        ShakeDetector.getInstance(this).apply {
            updateSensitivity(preferenceUtil.getShakeLevel())
            setEnabled(binding.preferenceShakeChangeMusic.isSelected)
        }
    }

    private fun syncTenBandState() {
        binding.preferenceUseTenBands.isSelected =
            SoundEffectPreferences.getEqualizerBandMode(this) == SoundEffectPreferences.TEN_BAND_MODE
    }

    private fun syncDarkModeState() {
        val themeType = preferenceUtil.getIntPreference("theme_type", ThemeManager.THEME_TYPE_PICTURE)
        binding.preferenceDarkMode.isSelected = themeType == ThemeManager.THEME_TYPE_DARK
    }

    private fun syncForwardBackwardSummary() {
        val value = preferenceUtil.getIntPreference(KEY_FORWARD_BACKWARD_SECONDS, 15)
        binding.preferenceTimeForwardBackward.setSummaryOn("${value}s")
    }

    private fun syncForwardBackwardEnabledState() {
        setEnabledState(
            binding.preferenceTimeForwardBackward,
            preferenceUtil.getBooleanPreference(KEY_SHOW_FORWARD_BACKWARD, false)
        )
    }

    private fun syncQueueForSearchingSummary() {
        binding.preferenceQueueForSearching.setSummaryOn(
            if (preferenceUtil.getQueueForSearchingMode() == 0) {
                getString(R.string.queue_all_songs)
            } else {
                getString(R.string.queue_search_result)
            }
        )
    }

    private fun syncShakeLevelSummary() {
        val level = ((preferenceUtil.getShakeLevel() * 15f) + 1f).toInt()
        binding.preferenceShakeLevel.setSummaryOn(level.toString())
    }

    private fun syncReplayGainModeSummary() {
        binding.preferenceReplayGainMode.setSummaryOn(
            when (preferenceUtil.getIntPreference(KEY_REPLAY_GAIN_MODE, 0)) {
                1 -> getString(R.string.replay_gain_track)
                2 -> getString(R.string.replay_gain_album)
                else -> getString(R.string.replay_gain_none)
            }
        )
    }

    private fun syncReplayGainPreampSummary() {
        val withTag = formatPreampDb(
            preferenceUtil.getFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITH_TAG, 0f)
        )
        val withoutTag = formatPreampDb(
            preferenceUtil.getFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG, 0f)
        )
        binding.preferenceReplayGainPreamp.setSummaryOn("$withTag / $withoutTag")
    }

//    private fun syncLockTimeFormatSummary() {
//        val labels = arrayOf(
//            getString(R.string.time_format_auto),
//            "12-hour",
//            "24-hour"
//        )
//        val index = preferenceUtil.getLockScreenTimeFormat().coerceIn(0, labels.lastIndex)
//        binding.preferenceLockTimeFormat.setSummaryOn(labels[index])
//    }

    private fun syncLockBackgroundSummary() {
        binding.preferenceLockBackground.setSummaryOn(
            if (preferenceUtil.getIntPreference(KEY_LOCK_BACKGROUND, 1) == 0) {
                getString(R.string.lock_screen_theme)
            } else {
                getString(R.string.lock_screen_artwork)
            }
        )
    }

//    private fun syncLockSettingEnabledState() {
//        val enabled = binding.preferenceLockScreen.isSelected
//        setEnabledState(binding.preferenceLockTimeFormat, enabled)
//        setEnabledState(binding.preferenceLockBackground, enabled)
//    }

    private fun syncStatusBarLyricState() {
        val enabled = StatusBarLyricSettings.from(this).enabled
        binding.preferenceStatusBarLyrics.setTips(
            if (enabled) R.string.sbar_lyric_opened else R.string.sbar_lyric_closed
        )
    }

    private fun syncPlaylistAddPositionSummary() {
        binding.preferencePlaylistAddPosition.setTips(
            if (preferenceUtil.getPlaylistAddPosition() == 0) {
                R.string.add_music_position_top
            } else {
                R.string.add_music_position_end
            }
        )
    }

    private fun syncShuffleButtonSummary() {
        val enabledLabels = SHUFFLE_BUTTON_TARGET_IDS
            .filter { preferenceUtil.isShowShuffleButtonEnabled(it) }
            .map(::shuffleTargetLabel)
        binding.preferenceShuffleButton.setSummaryOn(
            enabledLabels.joinToString(", ").ifBlank {
                getString(R.string.show_shuffle_button_summary)
            }
        )
    }

    private fun syncLibraryOrderSummary() {
        val currentTab = preferenceUtil.getLibraryTabConfigs()
            .filter { it.visible }
            .joinToString(", ") { getString(LibraryTabConfigStore.labelRes(it.id)) }
        binding.preferenceLibraryOrder.setSummaryOn(
            getString(R.string.library_order_custom_tip, currentTab)
        )
    }

    private fun syncSmartPlaylistSummary() {
        binding.preferencePlaylistTrackLimit.setTips(preferenceUtil.getSmartPlaylistSummary(this))
    }

    private fun syncBluetoothLyricState() {
        binding.preferenceBluetoothLyric.setSelected(
            preferenceUtil.getBooleanPreference(KEY_BLUETOOTH_LYRIC, true)
        )
    }

    private fun syncNotificationPreferences() {
        val hasPermission = hasNotificationPermission()
        binding.preferenceUseNotification.isSelected = hasPermission
        preferenceUtil.putBooleanPreference(KEY_NOTIFICATION_BAR_ENABLED, hasPermission)

        val oldNotificationEnabled = hasPermission &&
                preferenceUtil.getBooleanPreference(KEY_OLD_NOTIFICATION, false)
        binding.preferenceUseOldNotification.isSelected = oldNotificationEnabled
        setEnabledState(binding.preferenceUseColorNotification, !oldNotificationEnabled)
        setEnabledState(binding.preferenceUseOldNotificationDivider, !oldNotificationEnabled)

        val colorNotificationEnabled =
            preferenceUtil.getBooleanPreference(
                KEY_COLOR_NOTIFICATION,
                true
            ) && !oldNotificationEnabled
        binding.preferenceUseColorNotification.isSelected = colorNotificationEnabled
    }

    private fun syncBluetoothAutoStartState() {
        val enabled =
            preferenceUtil.isBluetoothAutoStartEnabled() && hasBluetoothConnectPermission()
        if (enabled != preferenceUtil.isBluetoothAutoStartEnabled()) {
            preferenceUtil.putBooleanPreference(
                AppPreferenceKey.BLUETOOTH_AUTO_START,
                enabled
            )
        }
        binding.preferenceBluetoothAutoStart.isSelected = enabled
    }

    private fun syncFadeControls() {
        val seconds =
            (preferenceUtil.getIntPreference(KEY_FADE_DURATION_MS, 6000) / 1000).coerceIn(1, 12)
        binding.preferenceFadeSeekBar.setMax(11)
        binding.preferenceFadeSeekBar.setProgress(seconds - 1)
        binding.preferenceFadeSeekText.text = formatSecondsLabel(seconds)
        renderFadeControls(binding.preferenceCrossFade.isSelected)
    }

    private fun toggleTenBandEqualizer() {
        val settings = AudioEffectsManager.loadSettings(this)
        val updatedBandMode = if (settings.useTenBand) {
            SoundEffectPreferences.FIVE_BAND_MODE
        } else {
            SoundEffectPreferences.TEN_BAND_MODE
        }
        val updated =
            settings.copy(useTenBand = updatedBandMode == SoundEffectPreferences.TEN_BAND_MODE)
        AudioEffectsManager.saveSettings(this, updated)
        binding.preferenceUseTenBands.isSelected = updated.useTenBand
    }

    private fun showForwardBackwardDialog() {
        val values = intArrayOf(5, 10, 15, 20, 30, 60)
        val labels = values.map { "${it}s" }.toTypedArray()
        val current = preferenceUtil.getIntPreference(KEY_FORWARD_BACKWARD_SECONDS, 15)
        val checkedIndex = values.indexOf(current).takeIf { it >= 0 } ?: 2

        val config =
            MaterialDialogConfig.createMaterialListDialogConfig(this, labels.toList()).apply {
                titleText = getString(R.string.select_time)
                selectedItemIndex = checkedIndex
                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    DialogRegistry.dismissAll(this@SettingActivity)
                    preferenceUtil.putIntPreference(KEY_FORWARD_BACKWARD_SECONDS, values[which])
                    syncForwardBackwardSummary()
                }
                this.itemIconRes = R.drawable.vector_single_check_selector
            }

        OptionsListDialog.show(this, config)
    }

    private fun openKeepAliveSettings() {
        preferenceUtil.putBooleanPreference(KEY_SHOW_KEEP_ALIVE_DOT, false)
        if (isIgnoringBatteryOptimizations()) {
            ToastUtil.show(this, Toast.LENGTH_SHORT, getString(R.string.succeed))
            return
        }
        showMessageDialog(
            title = getString(R.string.avoid_stop_title),
            message = getString(R.string.avoid_stop_content),
            positiveText = getString(R.string.grant_permission),
            negativeText = getString(R.string.cancel)
        ) {
            val packageUri = Uri.parse("package:$packageName")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                ToastUtil.show(this, Toast.LENGTH_SHORT, getString(R.string.permission_open_failed))
            }
        }
    }

    private fun showQueueForSearchingDialog() {
        val labels = arrayOf(
            getString(R.string.queue_all_songs),
            getString(R.string.queue_search_result)
        )

        val config =
            MaterialDialogConfig.createMaterialListDialogConfig(this, labels.toList()).apply {
                titleText = getString(R.string.queue_for_searching)
                selectedItemIndex = preferenceUtil.getQueueForSearchingMode()
                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    DialogRegistry.dismissAll(this@SettingActivity)
                    preferenceUtil.putIntPreference(KEY_QUEUE_FOR_SEARCHING, which)
                    syncQueueForSearchingSummary()
                }
            }
        OptionsListDialog.show(this, config)
    }

    private fun handleBluetoothLyricClick() {
        val enabled = binding.preferenceBluetoothLyric.isSelected
        if (enabled) {
            preferenceUtil.putBooleanPreference(KEY_BLUETOOTH_LYRIC, false)
            binding.preferenceBluetoothLyric.setSelected(false)
            return
        }
        showMessageDialog(
            title = getString(R.string.bluetooth_lyric_dialog_title),
            message = getString(R.string.bluetooth_lyric_dialog_msg),
            positiveText = getString(R.string.confirm),
            negativeText = getString(R.string.cancel)
        ) {
            preferenceUtil.putBooleanPreference(KEY_BLUETOOTH_LYRIC, true)
            binding.preferenceBluetoothLyric.setSelected(true)
        }
    }

    private fun renderShakeLevel(enabled: Boolean) {
        binding.preferenceShakeLevel.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun showShakeLevelDialog() {

        ShakeLevelDialogFragment.newInstance().show(supportFragmentManager, null)
//        val values = floatArrayOf(0.2f, 0.5f, 0.8f, 1.0f)
//        val labels = arrayOf("Low", "Normal", "High", "Very High")
//        val current = preferenceUtil.getShakeLevel()
//        val checkedIndex = values.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 1
//        showSingleChoiceDialog(
//            title = getString(R.string.shake_level),
//            items = labels.toList(),
//            checkedIndex = checkedIndex
//        ) { which ->
//            val level = values[which]
//            preferenceUtil.setShakeLevel(level)
//            ShakeDetector.getInstance(this).updateSensitivity(level)
//            syncShakeLevelSummary()
//        }
    }

    private fun showReplayGainModeDialog() {
        val labels = arrayOf(
            getString(R.string.replay_gain_none),
            getString(R.string.replay_gain_track),
            getString(R.string.replay_gain_album)
        )
        val checked = preferenceUtil.getIntPreference(KEY_REPLAY_GAIN_MODE, 0).coerceIn(0, 2)
        showSingleChoiceDialog(
            title = getString(R.string.replay_gain_mode),
            items = labels.toList(),
            checkedIndex = checked
        ) { which ->
            preferenceUtil.putIntPreference(KEY_REPLAY_GAIN_MODE, which)
            syncReplayGainModeSummary()
            MusicPlaybackController.applyPlaybackTuning(this)
        }
    }

    private fun showReplayGainPreampDialog() {
        val contentView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_replay_gain_preamp, null, false)
        appDependencies.themeEngine.apply(contentView)

        val withTagSeek = contentView.findViewById<SeekBar>(R.id.with_tag_seek)
        val withoutTagSeek = contentView.findViewById<SeekBar>(R.id.without_tag_seek)
        val withTagText = contentView.findViewById<TextView>(R.id.with_tag_text)
        val withoutTagText = contentView.findViewById<TextView>(R.id.without_tag_text)
        val resetButton = contentView.findViewById<TextView>(R.id.dialog_button_reset)
        val cancelButton = contentView.findViewById<TextView>(R.id.dialog_button_cancel)
        val okButton = contentView.findViewById<TextView>(R.id.dialog_button_ok)
        val palette = appDependencies.themeRepo.getCorePalette(this)

        resetButton.setTextColor(palette.cancelTextColor)
        cancelButton.setTextColor(palette.cancelTextColor)
        okButton.setTextColor(Color.WHITE)
        resetButton.background = DrawableUtil.roundedRipple(
            fillColor = palette.cancelBaseColor,
            rippleColor = palette.rippleColor,
            radius = resources.displayMetrics.density * 100f
        )
        cancelButton.background = DrawableUtil.roundedRipple(
            fillColor = palette.cancelBaseColor,
            rippleColor = palette.rippleColor,
            radius = resources.displayMetrics.density * 100f
        )
        okButton.background = DrawableUtil.roundedRipple(
            fillColor = palette.accentColor,
            rippleColor = palette.confirmRippleColor,
            radius = resources.displayMetrics.density * 100f
        )

        fun updateLabel(seekBar: SeekBar, labelView: TextView) {
            labelView.text =
                formatPreampDb(progressToPreampDb(seekBar.getProgress(), seekBar.getMax()))
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                when (seekBar.id) {
                    R.id.with_tag_seek -> updateLabel(seekBar, withTagText)
                    R.id.without_tag_seek -> updateLabel(seekBar, withoutTagText)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }
        withTagSeek.setOnSeekBarChangeListener(seekListener)
        withoutTagSeek.setOnSeekBarChangeListener(seekListener)
        withTagSeek.setProgress(
            preampDbToProgress(
                preferenceUtil.getFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITH_TAG, 0f),
                withTagSeek.getMax()
            )
        )
        withoutTagSeek.setProgress(
            preampDbToProgress(
                preferenceUtil.getFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG, 0f),
                withoutTagSeek.getMax()
            )
        )
        updateLabel(withTagSeek, withTagText)
        updateLabel(withoutTagSeek, withoutTagText)

        val dialog = MessageDialog(
            this,
            themedMessageDialogConfig().apply {
                customView = contentView
            }
        )
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        resetButton.setOnClickListener {
            withTagSeek.setProgress(withTagSeek.getMax() / 2)
            withoutTagSeek.setProgress(withoutTagSeek.getMax() / 2)
        }
        okButton.setOnClickListener {
            preferenceUtil.putFloatPreference(
                KEY_REPLAY_GAIN_PREAMP_WITH_TAG,
                progressToPreampDb(withTagSeek.getProgress(), withTagSeek.getMax())
            )
            preferenceUtil.putFloatPreference(
                KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG,
                progressToPreampDb(withoutTagSeek.getProgress(), withoutTagSeek.getMax())
            )
            syncReplayGainPreampSummary()
            MusicPlaybackController.applyPlaybackTuning(this)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showPlaylistAddPositionDialog() {
        val labels = arrayOf(
            getString(R.string.add_music_position_top),
            getString(R.string.add_music_position_end)
        )
        showSingleChoiceDialog(
            items = labels.toList(),
            checkedIndex = preferenceUtil.getPlaylistAddPosition().coerceIn(0, 1)
        ) { which ->
            preferenceUtil.setPlaylistAddPosition(which)
            syncPlaylistAddPositionSummary()
        }
    }

    private fun showSmartPlaylistLimitDialog() {
        val items = arrayOf(
            getString(R.string.playlist_limit_day),
            getString(R.string.playlist_limit_week),
            getString(R.string.playlist_limit_month),
            getString(R.string.playlist_limit_month_3),
            getString(R.string.playlist_limit_month_6),
            getString(R.string.playlist_limit_year),
            getString(R.string.playlist_limit_forever),
            getString(R.string.playlist_track_limit_hint)
        )
        showSingleChoiceDialog(
            title = getString(R.string.playlist_track_limit),
            items = items.toList(),
            checkedIndex = preferenceUtil.getSmartPlaylistSelectionIndex()
        ) { which ->
            if (which == 7) {
                showCustomTrackLimitDialog()
            } else {
                preferenceUtil.setSmartPlaylistSelection(which)
                syncSmartPlaylistSummary()
            }
        }
    }

    private fun showCustomTrackLimitDialog() {
        val input = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.playlist_track_limit_hint)
            val currentLimit = preferenceUtil.getSmartPlaylistTrackLimit()
            if (currentLimit > 0) {
                setText(currentLimit.toString())
                setSelection(text?.length ?: 0)
            }
        }
        val dialogConfig = themedMessageDialogConfig()
        dialogConfig.titleText = getString(R.string.playlist_track_limit_hint)
        dialogConfig.customView = input
        dialogConfig.negativeButtonText = getString(android.R.string.cancel)
        dialogConfig.positiveButtonText = getString(android.R.string.ok)
        dialogConfig.positiveButtonClickListener =
            DialogInterface.OnClickListener { dialog, _ ->
                val value = input.text?.toString()?.trim()?.toIntOrNull()
                if (value == null || value <= 0) {
                    ToastUtil.show(this, Toast.LENGTH_SHORT, getString(R.string.input_error))
                    return@OnClickListener
                }
                preferenceUtil.setSmartPlaylistSelection(7, value)
                syncSmartPlaylistSummary()
                dialog.dismiss()
            }
        MessageDialog.show(this, dialogConfig)
    }

//    private fun showLockTimeFormatDialog() {
//        val labels = arrayOf(
//            getString(R.string.time_format_auto),
//            "12-hour",
//            "24-hour"
//        )
//        showSingleChoiceDialog(
//            items = labels.toList(),
//            checkedIndex = preferenceUtil.getLockScreenTimeFormat().coerceIn(0, 2)
//        ) { which ->
//            preferenceUtil.setLockScreenTimeFormat(which)
//            syncLockTimeFormatSummary()
//        }
//    }

    private fun showLockBackgroundDialog() {
        val labels = arrayOf(
            getString(R.string.lock_screen_theme),
            getString(R.string.lock_screen_artwork)
        )
        showSingleChoiceDialog(
            items = labels.toList(),
            checkedIndex = preferenceUtil.getIntPreference(KEY_LOCK_BACKGROUND, 1).coerceIn(0, 1)
        ) { which ->
            preferenceUtil.putIntPreference(KEY_LOCK_BACKGROUND, which)
            syncLockBackgroundSummary()
        }
    }

    private fun handleBluetoothAutoStartClick() {
        if (binding.preferenceBluetoothAutoStart.isSelected) {
            setBluetoothAutoStartEnabled(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
            pendingBluetoothAutoStartEnable = true
            showMessageDialog(
                message = getString(R.string.permission_bluetooth_connect_ask),
                positiveText = getString(R.string.grant_permission),
                negativeText = getString(R.string.cancel)
            ) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return
        }
        setBluetoothAutoStartEnabled(true)
    }

    private fun setBluetoothAutoStartEnabled(enabled: Boolean) {
        preferenceUtil.putBooleanPreference(AppPreferenceKey.BLUETOOTH_AUTO_START, enabled)
        binding.preferenceBluetoothAutoStart.setSelected(enabled)
    }

    private fun showBluetoothPermissionSettingsDialog() {
        showMessageDialog(
            message = getString(R.string.permission_bluetooth_connect_ask_again),
            positiveText = getString(R.string.grant_permission),
            negativeText = getString(R.string.cancel)
        ) {
            openAppDetailsSettings()
        }
    }

    private fun renderFadeControls(enabled: Boolean) {
        binding.preferenceFadeSeekLayout.alpha = if (enabled) 1f else 0.45f
        binding.preferenceFadeSeekBar.isEnabled = enabled
    }

    private fun setEnabledState(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
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

    private fun libraryTabNames(): Array<String> = arrayOf(
        getString(R.string.tracks),
        getString(R.string.artists),
        getString(R.string.albums),
        getString(R.string.genres)
    )

    private fun formatSecondsLabel(seconds: Int): String = "$seconds${getString(R.string.seconds)}"

    private fun formatPreampDb(value: Float): String {
        val rounded = (value * 10f).toInt() / 10f
        val prefix = if (rounded > 0f) "+" else ""
        return "$prefix${rounded}dB"
    }

    private fun progressToPreampDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0f
        val ratio = progress.toFloat() / max.toFloat()
        return ((-15f + (30f * ratio)) * 10f).toInt() / 10f
    }

    private fun preampDbToProgress(value: Float, max: Int): Int {
        if (max <= 0) return 0
        return (((value.coerceIn(-15f, 15f) + 15f) / 30f) * max).toInt()
    }

    private fun setupFragmentResults() {
        supportFragmentManager.setFragmentResultListener(
            ShuffleButtonSettingsDialog.RESULT_KEY,
            this
        ) { _, _ ->
            syncShuffleButtonSummary()
        }
        supportFragmentManager.setFragmentResultListener(
            LibraryTabManagerDialog.RESULT_KEY,
            this
        ) { _, _ ->
            syncLibraryOrderSummary()
        }
    }

    private fun shuffleTargetLabel(id: Int): String = getString(
        when (id) {
            0 -> R.string.home
            1 -> R.string.playlist
            -1 -> R.string.tracks
            -5 -> R.string.albums
            -4 -> R.string.artists
            -8 -> R.string.genres
            -6 -> R.string.folder
            else -> R.string.tracks
        }
    )

    private object AppPreferenceKey {
        const val BLUETOOTH_AUTO_START = "preference_bluetooth_auto_start"
    }

    private fun themedMessageDialogConfig(): MessageDialog.Config {
        val palette = appDependencies.themeRepo.getCorePalette(this)
        return MessageDialog.Config.create(this).apply {
            titleTextColor = palette.titleColor
            messageTextColor = palette.messageColor
        }
    }

    private fun themedListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = appDependencies.themeRepo.getCorePalette(this)
        return OptionsListDialog.Config.create(this, items).apply {
            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.accentColor
            itemIconPlacement = 1
        }
    }

    private fun showMessageDialog(
        title: String? = null,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        onPositive: (() -> Unit)? = null
    ) {
        val config = themedMessageDialogConfig().apply {
            titleText = title
            messageText = message
            positiveButtonText = positiveText
            negativeButtonText = negativeText
            positiveButtonClickListener =
                DialogInterface.OnClickListener { dialog, _ ->
                    onPositive?.invoke()
                    dialog.dismiss()
                }
        }
        MessageDialog.show(this, config)
    }

    private fun showCustomDialog(
        title: String? = null,
        message: String? = null,
        customView: View,
        positiveText: String,
        negativeText: String? = null,
        onPositive: () -> Unit
    ) {
        val config = themedMessageDialogConfig().apply {
            titleText = title
            messageText = message
            this.customView = customView
            positiveButtonText = positiveText
            negativeButtonText = negativeText
            positiveButtonClickListener =
                DialogInterface.OnClickListener { dialog, _ ->
                    onPositive()
                    dialog.dismiss()
                }
        }
        MessageDialog.show(this, config)
    }

    private fun showSingleChoiceDialog(
        title: String? = null,
        items: List<String>,
        checkedIndex: Int,
        itemIconRes: Int = 0,
        onSelected: (Int) -> Unit
    ) {
        val config = themedListDialogConfig(items).apply {
            titleText = title
            selectedItemIndex = checkedIndex
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(this@SettingActivity)
                onSelected(which)
            }
            this.itemIconRes = itemIconRes
        }
        OptionsListDialog.show(this, config)
    }
}

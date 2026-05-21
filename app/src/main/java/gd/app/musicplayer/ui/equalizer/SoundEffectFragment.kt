package gd.app.musicplayer.ui.equalizer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.view.EqualizerSingleGroup
import gd.app.musicplayer.core.designsystem.view.RotateStepBar
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.data.local.preference.SoundEffectSettings
import gd.app.musicplayer.databinding.FragmentSoundEffectBinding
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SoundEffectFragment : ViewBindingFragment<FragmentSoundEffectBinding>() {

    private val soundEffectViewModel: SoundEffectViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private lateinit var audioManager: AudioManager

    private var latestSettings: SoundEffectSettings = SoundEffectSettings()

    private var isRendering: Boolean = false

    private var volumeTracking: Boolean = false
    private var boostTracking: Boolean = false
    private var leftBalanceTracking: Boolean = false
    private var rightBalanceTracking: Boolean = false
    private var loudnessApplyJob: Job? = null

    private val systemVolumeObserver: ContentObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                syncSystemVolumeToSlider()
            }
        }
    }

    override fun onCreateBinding(inflater: LayoutInflater): FragmentSoundEffectBinding {
        return FragmentSoundEffectBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentSoundEffectBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        audioManager = requireContext()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupSeekBars(binding)
        setupSwitches(binding)
        setupRotations(binding)
        setupReverb(binding)
        observeSettings()
        updateContentHeight()

        syncSystemVolumeToSlider()
    }

    override fun onStart() {
        super.onStart()

        requireContext().contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            systemVolumeObserver
        )

        syncSystemVolumeToSlider()
    }

    override fun onStop() {
        requireContext().contentResolver.unregisterContentObserver(systemVolumeObserver)
        super.onStop()
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                soundEffectViewModel.settings.collect { settings ->
                    latestSettings = settings
                    renderAll(requireBinding(), settings)
                }
            }
        }
    }

    private fun setupSeekBars(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerVolumeProgress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    updateSystemVolumeDescription(progress, seekBar.getMax())

                    if (!fromUser || isRendering) return

                    setSystemMusicVolumeFromProgress(
                        progress = progress,
                        sliderMax = seekBar.getMax()
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    volumeTracking = true
                    updateGestureInterception(true)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    volumeTracking = false
                    updateGestureInterception(false)
                    syncSystemVolumeToSlider()
                }
            }
        )

        equalizerVolumeBoostProgress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val value = progress.toNormalizedFloat(seekBar.getMax())
                    equalizerVolumeBoostProgressDes.text = value.toPercentText()

                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(loudnessStrength = value)
                    loudnessApplyJob?.cancel()
                    loudnessApplyJob = persistAndApplyDelayed(CONTROL_APPLY_DELAY_MS) {
                        soundEffectViewModel.persistLoudnessStrength(value)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    boostTracking = true
                    updateGestureInterception(true)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    boostTracking = false
                    updateGestureInterception(false)
                    loudnessApplyJob?.cancel()
                    persistAndApply {
                        soundEffectViewModel.persistLoudnessStrength(
                            latestSettings.loudnessStrength
                        )
                    }
                }
            }
        )
    }

    private fun setupSwitches(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerVolumeBoostBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser || isRendering) return

                    if (isSelected && !AudioEffectsManager.supportsLoudnessEnhancer()) {
                        selectBox.isSelected = false
                        ToastUtil.show(requireContext(), R.string.not_supported)
                        return
                    }

                    latestSettings = latestSettings.copy(loudnessEnabled = isSelected)
                    renderEnabledState(requireBinding(), latestSettings)
                    persistAndApply {
                        soundEffectViewModel.persistLoudnessEnabled(isSelected)
                    }
                }
            }
        )

        equalizerBalanceBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(balanceEnabled = isSelected)
                    renderEnabledState(requireBinding(), latestSettings)
                    persistAndApply {
                        soundEffectViewModel.persistBalanceEnabled(isSelected)
                    }
                }
            }
        )
    }

    private fun setupRotations(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerLeftRotate.setOnRotateChangedListener(
            balanceRotateListener(isLeft = true)
        )

        equalizerRightRotate.setOnRotateChangedListener(
            balanceRotateListener(isLeft = false)
        )
    }

    private fun balanceRotateListener(
        isLeft: Boolean
    ): RotateStepBar.OnRotateChangedListener {
        return object : RotateStepBar.OnRotateChangedListener {
            override fun onRotationTrackingChanged(
                view: RotateStepBar,
                isTracking: Boolean
            ) {
                if (isLeft) {
                    leftBalanceTracking = isTracking
                } else {
                    rightBalanceTracking = isTracking
                }

                updateGestureInterception(
                    leftBalanceTracking || rightBalanceTracking
                )

                if (!isTracking) {
                    persistAndApplyBalance(isLeft)
                }
            }

            override fun onRotationChanged(
                view: RotateStepBar,
                progress: Int
            ) {
                if (isRendering) return

                val value = progress.toNormalizedFloat(view.getMax())

                latestSettings = if (isLeft) {
                    latestSettings.copy(balanceLeft = value)
                } else {
                    latestSettings.copy(balanceRight = value)
                }
                persistBalanceValue(isLeft, value)
            }
        }
    }

    private fun setupReverb(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerReverbLayout.root.setOnSingleSelectListener(
            object : EqualizerSingleGroup.OnSingleSelectionChangedListener {
                override fun onSelectionChanged(
                    parent: ViewGroup,
                    clickedView: View,
                    selectedIndex: Int
                ) {
                    if (isRendering) return

                    val reverbIndex = if (selectedIndex < 0) {
                        REVERB_NONE
                    } else {
                        selectedIndex + 1
                    }

                    latestSettings = latestSettings.copy(reverbIndex = reverbIndex)
                    persistAndApply {
                        soundEffectViewModel.persistBalanceLeft(latestSettings.balanceLeft)
                        soundEffectViewModel.persistBalanceRight(latestSettings.balanceRight)
                        soundEffectViewModel.persistReverbIndex(reverbIndex)
                    }
                }
            }
        )
    }

    private fun renderAll(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        isRendering = true

        try {
            renderSystemVolume(binding)
            renderLoudness(binding, settings)
            renderReverb(binding, settings)
            renderBalance(binding, settings)
            renderEnabledState(binding, settings)
        } finally {
            isRendering = false
        }
    }

    private fun renderSystemVolume(binding: FragmentSoundEffectBinding) = with(binding) {
        if (volumeTracking) return@with

        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val sliderMax = equalizerVolumeProgress.getMax()

        val progress = if (maxSystemVolume > 0) {
            ((currentSystemVolume / maxSystemVolume.toFloat()) * sliderMax)
                .roundToInt()
                .coerceIn(0, sliderMax)
        } else {
            0
        }

        equalizerVolumeProgress.setProgress(progress)
        updateSystemVolumeDescription(progress, sliderMax)
    }

    private fun renderLoudness(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        val loudnessSupported = AudioEffectsManager.supportsLoudnessEnhancer()
        val loudnessEnabled = settings.loudnessEnabled && loudnessSupported

        equalizerVolumeBoostBox.isSelected = loudnessEnabled

        if (!boostTracking) {
            equalizerVolumeBoostProgress.setProgress(
                settings.loudnessStrength.toProgress(
                    equalizerVolumeBoostProgress.getMax()
                )
            )
        }

        equalizerVolumeBoostProgressDes.text =
            settings.loudnessStrength.toPercentText()
    }

    private fun renderReverb(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        val selectedReverbIndex = if (settings.reverbIndex <= REVERB_NONE) {
            NO_SELECTED_REVERB_INDEX
        } else {
            settings.reverbIndex - 1
        }

        equalizerReverbLayout.root.setSelectedIndex(selectedReverbIndex)
    }

    private fun renderBalance(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        equalizerBalanceBox.isSelected = settings.balanceEnabled

        if (!leftBalanceTracking) {
            equalizerLeftRotate.setProgress(
                settings.balanceLeft.toProgress(equalizerLeftRotate.getMax())
            )
        }

        if (!rightBalanceTracking) {
            equalizerRightRotate.setProgress(
                settings.balanceRight.toProgress(equalizerRightRotate.getMax())
            )
        }
    }

    private fun renderEnabledState(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        val loudnessSupported = AudioEffectsManager.supportsLoudnessEnhancer()
        val loudnessEnabled = settings.loudnessEnabled && loudnessSupported
        val balanceEnabled = settings.balanceEnabled

        equalizerVolumeBoostBox.isEnabled = loudnessSupported
        equalizerVolumeBoostProgress.isEnabled = loudnessEnabled
        equalizerAmplifierText.isEnabled = loudnessEnabled
        equalizerVolumeBoostProgressDes.isEnabled = loudnessEnabled

        equalizerLeftRotate.isEnabled = balanceEnabled
        equalizerRightRotate.isEnabled = balanceEnabled
        equalizerBalanceText.isEnabled = balanceEnabled
        equalizerLeftText.isEnabled = balanceEnabled
        equalizerRightText.isEnabled = balanceEnabled
    }

    private fun syncSystemVolumeToSlider() {
        val binding = binding ?: return

        isRendering = true
        try {
            renderSystemVolume(binding)
        } finally {
            isRendering = false
        }
    }

    private fun setSystemMusicVolumeFromProgress(
        progress: Int,
        sliderMax: Int
    ) {
        if (sliderMax <= 0) return

        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val targetVolume = ((progress / sliderMax.toFloat()) * maxSystemVolume)
            .roundToInt()
            .coerceIn(0, maxSystemVolume)

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume,
            0
        )
    }

    private fun updateSystemVolumeDescription(
        progress: Int,
        sliderMax: Int
    ) {
        val binding = binding ?: return

        val value = progress.toNormalizedFloat(sliderMax)
        binding.equalizerVolumeProgressDes.text = value.toPercentText()
    }

    private fun applyAudioEffects() {
        playerViewModel.applyAudioEffects(requireContext())
    }

    private fun persistBalanceValue(
        isLeft: Boolean,
        value: Float
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (isLeft) {
                soundEffectViewModel.persistBalanceLeft(value)
            } else {
                soundEffectViewModel.persistBalanceRight(value)
            }
        }
    }

    private fun persistAndApplyBalance(
        isLeft: Boolean
    ): Job {
        return persistAndApply {
            if (isLeft) {
                soundEffectViewModel.persistBalanceLeft(latestSettings.balanceLeft)
            } else {
                soundEffectViewModel.persistBalanceRight(latestSettings.balanceRight)
            }
        }
    }

    private fun persistAndApply(
        work: suspend () -> Unit
    ): Job {
        return viewLifecycleOwner.lifecycleScope.launch {
            work()
            applyAudioEffects()
        }
    }

    private fun persistAndApplyDelayed(
        delayMs: Long,
        work: suspend () -> Unit
    ): Job {
        return viewLifecycleOwner.lifecycleScope.launch {
            delay(delayMs)
            work()
            applyAudioEffects()
        }
    }

    private fun updateGestureInterception(intercept: Boolean) {
        val binding = binding ?: return

        binding.root.requestDisallowInterceptTouchEvent(intercept)
        binding.equalizerContentView.requestDisallowInterceptTouchEvent(intercept)
    }

    private fun updateContentHeight() {
        val binding = requireBinding()

        binding.root.post {
            val rootHeight = binding.root.height
            if (rootHeight <= 0) return@post

            binding.equalizerContentView.setFixedHeight(rootHeight)
        }
    }

    private fun Float.toProgress(max: Int): Int {
        return (coerceIn(0f, 1f) * max)
            .roundToInt()
            .coerceIn(0, max)
    }

    private fun Int.toNormalizedFloat(max: Int): Float {
        if (max <= 0) return 0f
        return (this / max.toFloat()).coerceIn(0f, 1f)
    }

    private fun Float.toPercentText(): String {
        return "${(coerceIn(0f, 1f) * 100f).roundToInt()}%"
    }

    private companion object {
        const val REVERB_NONE = 0
        const val NO_SELECTED_REVERB_INDEX = -1
        const val CONTROL_APPLY_DELAY_MS = 80L
    }
}

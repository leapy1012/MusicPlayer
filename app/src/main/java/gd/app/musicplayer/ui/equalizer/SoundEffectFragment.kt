package gd.app.musicplayer.ui.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.designsystem.view.EqualizerSingleGroup
import gd.app.musicplayer.core.designsystem.view.RotateStepBar
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.data.local.preference.SoundEffectSettings
import gd.app.musicplayer.databinding.FragmentSoundEffectBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SoundEffectFragment : ViewBindingFragment<FragmentSoundEffectBinding>() {

    private val soundEffectViewModel: SoundEffectViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private var latestSettings: SoundEffectSettings = SoundEffectSettings()
    private var isRendering: Boolean = false

    override fun onCreateBinding(inflater: LayoutInflater): FragmentSoundEffectBinding {
        return FragmentSoundEffectBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentSoundEffectBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupSeekBars(binding)
        setupSwitches(binding)
        setupRotations(binding)
        setupReverb(binding)
        observeSettings()
        updateContentHeight()
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
                    val value = progress / seekBar.getMax().toFloat()
                    equalizerVolumeProgressDes.text = value.toPercentText()

                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(masterVolume = value)
                    soundEffectViewModel.setMasterVolume(value)
                    applyAudioEffects()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    root.requestDisallowInterceptTouchEvent(true)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    root.requestDisallowInterceptTouchEvent(false)
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
                    val value = progress / seekBar.getMax().toFloat()
                    equalizerVolumeBoostProgressDes.text = value.toPercentText()

                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(loudnessStrength = value)
                    soundEffectViewModel.setLoudnessStrength(value)
                    applyAudioEffects()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    root.requestDisallowInterceptTouchEvent(true)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    root.requestDisallowInterceptTouchEvent(false)
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

                    latestSettings = latestSettings.copy(loudnessEnabled = isSelected)
                    renderEnabledState(requireBinding(), latestSettings)

                    soundEffectViewModel.setLoudnessEnabled(isSelected)
                    applyAudioEffects()
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

                    soundEffectViewModel.setBalanceEnabled(isSelected)
                    applyAudioEffects()
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
                requireBinding().root.requestDisallowInterceptTouchEvent(isTracking)
            }

            override fun onRotationChanged(
                view: RotateStepBar,
                progress: Int
            ) {
                if (isRendering) return

                val value = progress / view.getMax().toFloat()

                latestSettings = if (isLeft) {
                    latestSettings.copy(balanceLeft = value)
                } else {
                    latestSettings.copy(balanceRight = value)
                }

                if (isLeft) {
                    soundEffectViewModel.setBalanceLeft(value)
                } else {
                    soundEffectViewModel.setBalanceRight(value)
                }

                applyAudioEffects()
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
                        0
                    } else {
                        selectedIndex + 1
                    }

                    latestSettings = latestSettings.copy(reverbIndex = reverbIndex)
                    soundEffectViewModel.setReverbIndex(reverbIndex)
                    applyAudioEffects()
                }
            }
        )
    }

    private fun renderAll(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        isRendering = true

        equalizerVolumeProgress.setProgress(
            settings.masterVolume.toProgress(equalizerVolumeProgress.getMax())
        )
        equalizerVolumeProgressDes.text = settings.masterVolume.toPercentText()

        equalizerVolumeBoostBox.isSelected = settings.loudnessEnabled
        equalizerVolumeBoostProgress.setProgress(
            settings.loudnessStrength.toProgress(equalizerVolumeBoostProgress.getMax())
        )
        equalizerVolumeBoostProgressDes.text = settings.loudnessStrength.toPercentText()

        val selectedReverbIndex = if (settings.reverbIndex <= 0) {
            -1
        } else {
            settings.reverbIndex - 1
        }
        equalizerReverbLayout.root.setSelectedIndex(selectedReverbIndex)

        equalizerBalanceBox.isSelected = settings.balanceEnabled
        equalizerLeftRotate.setProgress(
            settings.balanceLeft.toProgress(equalizerLeftRotate.getMax())
        )
        equalizerRightRotate.setProgress(
            settings.balanceRight.toProgress(equalizerRightRotate.getMax())
        )

        renderEnabledState(binding, settings)

        isRendering = false
    }

    private fun renderEnabledState(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        equalizerVolumeBoostProgress.isEnabled = settings.loudnessEnabled
        equalizerAmplifierText.isEnabled = settings.loudnessEnabled
        equalizerVolumeBoostProgressDes.isEnabled = settings.loudnessEnabled

        equalizerLeftRotate.isEnabled = settings.balanceEnabled
        equalizerRightRotate.isEnabled = settings.balanceEnabled
        equalizerBalanceText.isEnabled = settings.balanceEnabled
        equalizerLeftText.isEnabled = settings.balanceEnabled
        equalizerRightText.isEnabled = settings.balanceEnabled
    }

    private fun applyAudioEffects() {
        playerViewModel.applyAudioEffects(requireContext())
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
        return (coerceIn(0f, 1f) * max).roundToInt()
    }

    private fun Float.toPercentText(): String {
        return "${(coerceIn(0f, 1f) * 100f).roundToInt()}%"
    }
}

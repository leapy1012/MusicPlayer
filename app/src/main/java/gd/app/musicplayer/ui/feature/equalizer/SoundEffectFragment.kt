package gd.app.musicplayer.ui.feature.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gd.app.musicplayer.databinding.FragmentSoundEffectBinding
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.PlaybackControllerProvider
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.core.ui.view.EqualizerSingleGroup
import gd.app.musicplayer.core.ui.view.RotateStepBar
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.core.ui.view.SelectBox
import kotlin.math.roundToInt

class SoundEffectFragment : ViewBindingFragment<FragmentSoundEffectBinding>() {

    override fun onCreateBinding(inflater: LayoutInflater): FragmentSoundEffectBinding =
        FragmentSoundEffectBinding.inflate(inflater)

    private lateinit var settings: AudioEffectsManager.Settings

    override fun onBindingCreated(
        binding: FragmentSoundEffectBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)
        settings = AudioEffectsManager.loadSettings(requireContext())

        setupSeekBars(binding)
        setupSwitches(binding)
        setupRotations(binding)
        setupReverb(binding)
        updateContentHeight()
        renderAll(binding)
    }

    override fun onResume() {
        super.onResume()
        settings = AudioEffectsManager.loadSettings(requireContext())
        updateContentHeight()
        renderAll(requireBinding())
    }

    private fun setupSeekBars(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerVolumeProgress.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress / seekBar.getMax().toFloat()
                equalizerVolumeProgressDes.text = "${(value * 100f).roundToInt()}%"
                if (!fromUser) return
                settings = settings.copy(masterVolume = value)
                persistAndApply()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                root.requestDisallowInterceptTouchEvent(false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                root.requestDisallowInterceptTouchEvent(true)
            }
        })

        equalizerVolumeBoostProgress.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress / seekBar.getMax().toFloat()
                equalizerVolumeBoostProgressDes.text = "${(value * 100f).roundToInt()}%"
                if (!fromUser) return
                settings = settings.copy(loudnessStrength = value)
                persistAndApply()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                root.requestDisallowInterceptTouchEvent(false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                root.requestDisallowInterceptTouchEvent(true)
            }
        })
    }

    private fun setupSwitches(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerVolumeBoostBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser) return
                    settings = settings.copy(loudnessEnabled = isSelected)
                    renderEnabledState(requireBinding())
                    persistAndApply()
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
                    if (!fromUser) return
                    settings = settings.copy(balanceEnabled = isSelected)
                    renderEnabledState(requireBinding())
                    persistAndApply()
                }
            }
        )
    }

    private fun setupRotations(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerLeftRotate.setOnRotateChangedListener(balanceRotateListener(isLeft = true))
        equalizerRightRotate.setOnRotateChangedListener(balanceRotateListener(isLeft = false))
    }

    private fun balanceRotateListener(isLeft: Boolean): RotateStepBar.OnRotateChangedListener {
        return object : RotateStepBar.OnRotateChangedListener {
            override fun onRotationTrackingChanged(view: RotateStepBar, isTracking: Boolean) {
                requireBinding().root.requestDisallowInterceptTouchEvent(isTracking)
            }

            override fun onRotationChanged(view: RotateStepBar, progress: Int) {
                val value = progress / view.getMax().toFloat()
                settings = if (isLeft) {
                    settings.copy(balanceLeft = value)
                } else {
                    settings.copy(balanceRight = value)
                }
                persistAndApply()
            }
        }
    }

    private fun setupReverb(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerReverbLayout.root.setOnSingleSelectListener(
            object :
                EqualizerSingleGroup.OnSingleSelectionChangedListener {
                override fun onSelectionChanged(
                    parent: ViewGroup,
                    clickedView: View,
                    selectedIndex: Int
                ) {
                    settings =
                        settings.copy(reverbIndex = if (selectedIndex < 0) 0 else selectedIndex + 1)
                    persistAndApply()
                }
            }
        )
    }

    private fun renderAll(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerVolumeProgress.setProgress(
            (settings.masterVolume * equalizerVolumeProgress.getMax()).toInt()
        )
        equalizerVolumeProgressDes.text = "${(settings.masterVolume * 100f).roundToInt()}%"

        equalizerVolumeBoostBox.isSelected = settings.loudnessEnabled
        equalizerVolumeBoostProgress.setProgress(
            (settings.loudnessStrength * equalizerVolumeBoostProgress.getMax()).toInt()
        )
        equalizerVolumeBoostProgressDes.text =
            "${(settings.loudnessStrength * 100f).roundToInt()}%"

        equalizerBalanceBox.isSelected = settings.balanceEnabled
        equalizerLeftRotate.setProgress(
            (settings.balanceLeft * equalizerLeftRotate.getMax()).toInt()
        )
        equalizerRightRotate.setProgress(
            (settings.balanceRight * equalizerRightRotate.getMax()).toInt()
        )

        val selectedReverb = if (settings.reverbIndex <= 0) -1 else settings.reverbIndex - 1
        equalizerReverbLayout.root.setSelectedIndex(selectedReverb)

        renderEnabledState(binding)
    }

    private fun renderEnabledState(binding: FragmentSoundEffectBinding) = with(binding) {
        val loudnessEnabled = settings.loudnessEnabled
        equalizerVolumeBoostProgress.isEnabled = loudnessEnabled
        equalizerAmplifierText.isEnabled = loudnessEnabled
        equalizerVolumeBoostProgressDes.isEnabled = loudnessEnabled

        val balanceEnabled = settings.balanceEnabled
        equalizerLeftRotate.isEnabled = balanceEnabled
        equalizerRightRotate.isEnabled = balanceEnabled
        equalizerBalanceText.isEnabled = balanceEnabled
        equalizerLeftText.isEnabled = balanceEnabled
        equalizerRightText.isEnabled = balanceEnabled
    }

    private fun persistAndApply() {
        AudioEffectsManager.saveSettings(requireContext(), settings)
        PlaybackControllerProvider.applyAudioEffects(requireContext())
    }

    private fun updateContentHeight() {
        val binding = requireBinding()
        binding.root.post {
            val context = context ?: return@post
            val rootHeight = binding.root.height
            if (rootHeight <= 0) return@post

//            val extraHeight =
//                context.sp(64f) +
//                context.resources.getDimensionPixelSize(R.dimen.equalizer_title_margin_bottom) +
//                        context.resources.getDimensionPixelSize(R.dimen.equalizer_rotate_margin_bottom) +
//                        context.resources.getDimensionPixelSize(R.dimen.equalizer_toggle_height) +
//                        context.resources.getDimensionPixelSize(R.dimen.equalizer_box_margin_top) +
//                        context.resources.getDimensionPixelSize(R.dimen.equalizer_bass_height) +
//                        context.resources.getDimensionPixelSize(R.dimen.equalizer_rotate_text_margin) +
//                        context.sp(16f)

            binding.equalizerContentView.setFixedHeight(rootHeight)
        }
    }
}


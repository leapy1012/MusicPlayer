package gd.app.musicplayer.feature.equalizer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.datastore.SoundEffectSettings
import gd.app.musicplayer.databinding.FragmentSoundEffectBinding
import gd.app.musicplayer.playback.effects.AudioEffectsManager
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.player.full.PlayerViewModel
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

    private val reverbChipIds = intArrayOf(
        R.id.equalizer_reverb_small_room,
        R.id.equalizer_reverb_middle_room,
        R.id.equalizer_reverb_large_room,
        R.id.equalizer_reverb_middle_hall,
        R.id.equalizer_reverb_large_hall,
        R.id.equalizer_reverb_plate
    )

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
        setupBalance(binding)
        setupReverb(binding)
        observeSettings()

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
        equalizerVolumeProgress.setOnSliderChangeListener(
            onTrackingChanged = { tracking ->
                volumeTracking = tracking
                updateGestureInterception(tracking)
                if (!tracking) syncSystemVolumeToSlider()
            }
        ) { progress, fromUser ->
            val max = equalizerVolumeProgress.max
            equalizerVolumeProgressDes.text = progress.toPercentText(max)

            if (!fromUser || isRendering) return@setOnSliderChangeListener
            setSystemMusicVolumeFromProgress(progress = progress, sliderMax = max)
        }

        equalizerVolumeBoostProgress.setOnSliderChangeListener(
            onTrackingChanged = { tracking ->
                boostTracking = tracking
                updateGestureInterception(tracking)
                if (!tracking) {
                    loudnessApplyJob?.cancel()
                    persistAndApply {
                        soundEffectViewModel.persistLoudnessStrength(
                            latestSettings.loudnessStrength
                        )
                    }
                }
            }
        ) { progress, fromUser ->
            val max = equalizerVolumeBoostProgress.max
            equalizerVolumeBoostProgressDes.text = progress.toPercentText(max)

            if (!fromUser || isRendering) return@setOnSliderChangeListener

            val value = progress.toNormalizedFloat(max)
            latestSettings = latestSettings.copy(loudnessStrength = value)
            loudnessApplyJob?.cancel()
            loudnessApplyJob = persistAndApplyDelayed(CONTROL_APPLY_DELAY_MS) {
                soundEffectViewModel.persistLoudnessStrength(value)
            }
        }
    }

    private fun setupSwitches(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerVolumeBoostRow.bindSwitchRow(equalizerVolumeBoostBox)
        equalizerBalanceRow.bindSwitchRow(equalizerBalanceBox)

        equalizerVolumeBoostBox.setOnCheckedChangeListener { button, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener

            if (isChecked && !AudioEffectsManager.supportsLoudnessEnhancer()) {
                rendering { button.isChecked = false }
                ToastUtil.show(requireContext(), R.string.not_supported)
                return@setOnCheckedChangeListener
            }

            latestSettings = latestSettings.copy(loudnessEnabled = isChecked)
            renderEnabledState(requireBinding(), latestSettings)
            persistAndApply {
                soundEffectViewModel.persistLoudnessEnabled(isChecked)
            }
        }

        equalizerBalanceBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener

            latestSettings = latestSettings.copy(balanceEnabled = isChecked)
            renderEnabledState(requireBinding(), latestSettings)
            persistAndApply {
                soundEffectViewModel.persistBalanceEnabled(isChecked)
            }
        }
    }

    private fun setupBalance(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerLeftRotate.setOnSliderChangeListener(
            onTrackingChanged = { tracking -> onBalanceTrackingChanged(isLeft = true, tracking) }
        ) { progress, _ ->
            val max = equalizerLeftRotate.max
            equalizerLeftProgressDes.text = progress.toPercentText(max)
            onBalanceChanged(isLeft = true, progress.toNormalizedFloat(max))
        }

        equalizerRightRotate.setOnSliderChangeListener(
            onTrackingChanged = { tracking -> onBalanceTrackingChanged(isLeft = false, tracking) }
        ) { progress, _ ->
            val max = equalizerRightRotate.max
            equalizerRightProgressDes.text = progress.toPercentText(max)
            onBalanceChanged(isLeft = false, progress.toNormalizedFloat(max))
        }
    }

    private fun onBalanceTrackingChanged(isLeft: Boolean, tracking: Boolean) {
        if (isLeft) {
            leftBalanceTracking = tracking
        } else {
            rightBalanceTracking = tracking
        }
        updateGestureInterception(leftBalanceTracking || rightBalanceTracking)
        if (!tracking) {
            persistAndApplyBalance(isLeft)
        }
    }

    private fun onBalanceChanged(isLeft: Boolean, value: Float) {
        if (isRendering) return

        latestSettings = if (isLeft) {
            latestSettings.copy(balanceLeft = value)
        } else {
            latestSettings.copy(balanceRight = value)
        }
        persistBalanceValue(isLeft, value)
    }

    private fun setupReverb(binding: FragmentSoundEffectBinding) = with(binding) {
        equalizerReverbGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isRendering) return@setOnCheckedStateChangeListener

            val chipIndex = checkedIds.firstOrNull()?.let(reverbChipIds::indexOf) ?: -1
            val reverbIndex = if (chipIndex < 0) REVERB_NONE else chipIndex + 1

            latestSettings = latestSettings.copy(reverbIndex = reverbIndex)
            persistAndApply {
                soundEffectViewModel.persistReverbIndex(reverbIndex)
            }
        }
    }

    private fun renderAll(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = rendering {
        renderSystemVolume(binding)
        renderLoudness(binding, settings)
        renderReverb(binding, settings)
        renderBalance(binding, settings)
        renderEnabledState(binding, settings)
    }

    private fun renderSystemVolume(binding: FragmentSoundEffectBinding) = with(binding) {
        if (volumeTracking) return@with

        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val sliderMax = equalizerVolumeProgress.max

        val progress = if (maxSystemVolume > 0) {
            ((currentSystemVolume / maxSystemVolume.toFloat()) * sliderMax)
                .roundToInt()
                .coerceIn(0, sliderMax)
        } else {
            0
        }

        equalizerVolumeProgress.setProgress(progress)
        equalizerVolumeProgressDes.text = progress.toPercentText(sliderMax)
    }

    private fun renderLoudness(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        val loudnessSupported = AudioEffectsManager.supportsLoudnessEnhancer()
        equalizerVolumeBoostBox.isChecked = settings.loudnessEnabled && loudnessSupported

        if (!boostTracking) {
            val max = equalizerVolumeBoostProgress.max
            val progress = settings.loudnessStrength.toSliderProgress(max)
            equalizerVolumeBoostProgress.setProgress(progress)
            equalizerVolumeBoostProgressDes.text = progress.toPercentText(max)
        }
    }

    private fun renderReverb(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        val chipId = reverbChipIds.getOrNull(settings.reverbIndex - 1)
        if (chipId == null) {
            equalizerReverbGroup.clearCheck()
        } else if (equalizerReverbGroup.checkedChipId != chipId) {
            equalizerReverbGroup.check(chipId)
        }
    }

    private fun renderBalance(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        equalizerBalanceBox.isChecked = settings.balanceEnabled

        if (!leftBalanceTracking) {
            val max = equalizerLeftRotate.max
            val progress = settings.balanceLeft.toSliderProgress(max)
            equalizerLeftRotate.setProgress(progress)
            equalizerLeftProgressDes.text = progress.toPercentText(max)
        }

        if (!rightBalanceTracking) {
            val max = equalizerRightRotate.max
            val progress = settings.balanceRight.toSliderProgress(max)
            equalizerRightRotate.setProgress(progress)
            equalizerRightProgressDes.text = progress.toPercentText(max)
        }
    }

    private fun renderEnabledState(
        binding: FragmentSoundEffectBinding,
        settings: SoundEffectSettings
    ) = with(binding) {
        val loudnessEnabled = settings.loudnessEnabled && AudioEffectsManager.supportsLoudnessEnhancer()
        val balanceEnabled = settings.balanceEnabled

        equalizerVolumeBoostProgress.isEnabled = loudnessEnabled
        equalizerVolumeBoostProgressDes.isEnabled = loudnessEnabled

        equalizerLeftRotate.isEnabled = balanceEnabled
        equalizerRightRotate.isEnabled = balanceEnabled
        equalizerLeftText.isEnabled = balanceEnabled
        equalizerRightText.isEnabled = balanceEnabled
        equalizerLeftProgressDes.isEnabled = balanceEnabled
        equalizerRightProgressDes.isEnabled = balanceEnabled
    }

    private fun syncSystemVolumeToSlider() {
        val binding = binding ?: return
        rendering { renderSystemVolume(binding) }
    }

    private inline fun rendering(block: () -> Unit) {
        val wasRendering = isRendering
        isRendering = true
        try {
            block()
        } finally {
            isRendering = wasRendering
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
        (activity as? EqualizerActivity)?.requestPagerDisallowInterceptTouchEvent(intercept)
    }

    private fun Int.toNormalizedFloat(max: Int): Float {
        if (max <= 0) return 0f
        return (this / max.toFloat()).coerceIn(0f, 1f)
    }

    private companion object {
        const val REVERB_NONE = 0
        const val CONTROL_APPLY_DELAY_MS = 80L
    }
}

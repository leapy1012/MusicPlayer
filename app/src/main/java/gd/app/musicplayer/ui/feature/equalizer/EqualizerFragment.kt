package gd.app.musicplayer.ui.feature.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.view.RotateStepBar
import gd.app.musicplayer.core.ui.view.SelectBox
import gd.app.musicplayer.data.local.preference.EqualizerPreference
import gd.app.musicplayer.data.model.AudioEffectSettings
import gd.app.musicplayer.databinding.FragmentEqualizerBinding
import gd.app.musicplayer.domain.usecase.equalizer.LoadAudioEffectSettingsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.SaveAudioEffectSettingsUseCase
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.player.PlayerViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EqualizerFragment : ViewBindingFragment<FragmentEqualizerBinding>() {

    @Inject lateinit var loadAudioEffectSettingsUseCase: LoadAudioEffectSettingsUseCase
    @Inject lateinit var saveAudioEffectSettingsUseCase: SaveAudioEffectSettingsUseCase

    private val equalizerViewModel: EqualizerViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private lateinit var equalizerBandAdapter: EqualizerBandAdapter
    private var presetNames: List<String> = emptyList()
    private var currentBandLevels: MutableList<Int> = mutableListOf()

    private var latestSettings: EqualizerPreference = EqualizerPreference()
    private var isRendering: Boolean = false

    override fun onCreateBinding(inflater: LayoutInflater): FragmentEqualizerBinding {
        return FragmentEqualizerBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentEqualizerBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        presetNames = EqualizerPresets.defaultPresetNames(requireContext())
        setupEqualizerSwitch(binding)
        setupPresetSelector(binding)
        setupEditAndSave(binding)
        setupBandRecycler(binding)
        setupBassAndVirtualizer(binding)
        observeSettings()
        updateContentHeight()
    }

    fun reloadFromSettings() {
        if (!isAdded || binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = equalizerViewModel.settings.value
            latestSettings = settings
            currentBandLevels = resolveBandLevels(settings, loadAudioEffectSettingsUseCase())
            renderAll(requireBinding(), settings)
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                equalizerViewModel.settings.collect { settings ->
                    latestSettings = settings
                    currentBandLevels = resolveBandLevels(settings, loadAudioEffectSettingsUseCase())
                    renderAll(requireBinding(), settings)
                }
            }
        }
    }

    private fun setupEqualizerSwitch(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(equalizerEnabled = isSelected)
                    equalizerViewModel.setEqualizerEnabled(isSelected)
                    applyAudioEffects()
                }
            }
        )
    }

    private fun setupPresetSelector(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerEffectLayout.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showPresetPickerDialog()
        }
    }

    private fun setupEditAndSave(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerEdit.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            equalizerViewModel.setSelectedEffectId(USER_PRESET_ID)
        }

        equalizerSave.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                saveCurrentCustomLevels()
                equalizerViewModel.setSelectedEffectId(USER_PRESET_ID)
                applyAudioEffects()
            }
        }
    }

    private fun setupBandRecycler(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerSeekParent.equalizerRecycler.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )

            equalizerBandAdapter = EqualizerBandAdapter(
                layoutInflater = layoutInflater,
                onBandChanged = { bandIndex, levelMb, fromUser ->
                    if (isRendering || !fromUser) return@EqualizerBandAdapter
                    if (bandIndex !in currentBandLevels.indices) return@EqualizerBandAdapter

                    currentBandLevels[bandIndex] = levelMb
                    viewLifecycleOwner.lifecycleScope.launch {
                        saveCurrentCustomLevels()
                        if (latestSettings.selectedEffectId != USER_PRESET_ID) {
                            equalizerViewModel.setSelectedEffectId(USER_PRESET_ID)
                        }
                        applyAudioEffects()
                    }
                },
                onTrackingChanged = { tracking ->
                    requireBinding().root.requestDisallowInterceptTouchEvent(tracking)
                }
            )
            adapter = equalizerBandAdapter
        }
    }

    private fun setupBassAndVirtualizer(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerBassParent.equalizerBassBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(bassEnabled = isSelected)
                    equalizerViewModel.setBassEnabled(isSelected)
                    renderBassAndVirtualizerEnabledState(requireBinding(), latestSettings)
                    applyAudioEffects()
                }
            }
        )

        equalizerBassParent.equalizerVirtualBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser || isRendering) return

                    latestSettings = latestSettings.copy(virtualizerEnabled = isSelected)
                    equalizerViewModel.setVirtualizerEnabled(isSelected)
                    renderBassAndVirtualizerEnabledState(requireBinding(), latestSettings)
                    applyAudioEffects()
                }
            }
        )

        equalizerBassParent.equalizerBassRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
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
                    latestSettings = latestSettings.copy(bassProgress = value)

                    equalizerViewModel.setBassProgress(value)
                    applyAudioEffects()
                }
            }
        )

        equalizerBassParent.equalizerVirtualRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
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
                    latestSettings = latestSettings.copy(virtualizerProgress = value)

                    equalizerViewModel.setVirtualizerProgress(value)
                    applyAudioEffects()
                }
            }
        )
    }

    private fun renderAll(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        isRendering = true

        equalizerBox.isSelected = settings.equalizerEnabled
        equalizerText.text = resolveEffectName(settings.selectedEffectId)

        renderMainEnabledState(binding, settings)
        renderBandRecycler(binding, settings)
        renderBassAndVirtualizer(binding, settings)

        isRendering = false
    }

    private fun renderMainEnabledState(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        equalizerEditParent.isEnabled = settings.equalizerEnabled
        equalizerEffectLayout.isEnabled = settings.equalizerEnabled
        equalizerText.isEnabled = settings.equalizerEnabled
        equalizerTextArrow.isEnabled = settings.equalizerEnabled
        equalizerEdit.isEnabled = settings.equalizerEnabled
        equalizerSave.isEnabled = settings.equalizerEnabled

        equalizerSeekParent.equalizerSeekGroup.isEnabled = settings.equalizerEnabled
        equalizerSeekParent.equalizerRecycler.isEnabled = settings.equalizerEnabled
        equalizerBassParent.root.isEnabled = settings.equalizerEnabled
    }

    private fun renderBandRecycler(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        equalizerBandAdapter.submit(
            labels = EqualizerPresets.frequencies(settings.bandMode == TEN_BAND_MODE),
            levels = currentBandLevels.toIntArray(),
            enabled = settings.equalizerEnabled
        )
    }

    private fun renderBassAndVirtualizer(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding.equalizerBassParent) {
        equalizerBassBox.isSelected = settings.bassEnabled
        equalizerVirtualBox.isSelected = settings.virtualizerEnabled

        equalizerBassRotate.setProgress(
            settings.bassProgress.toProgress(equalizerBassRotate.getMax())
        )

        equalizerVirtualRotate.setProgress(
            settings.virtualizerProgress.toProgress(equalizerVirtualRotate.getMax())
        )

        renderBassAndVirtualizerEnabledState(binding, settings)
    }

    private fun renderBassAndVirtualizerEnabledState(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding.equalizerBassParent) {
        val equalizerEnabled = settings.equalizerEnabled

        equalizerBassBox.isEnabled = equalizerEnabled
        equalizerVirtualBox.isEnabled = equalizerEnabled

        equalizerBassRotate.isEnabled = equalizerEnabled && settings.bassEnabled
        equalizerBassText.isEnabled = equalizerEnabled && settings.bassEnabled

        equalizerVirtualRotate.isEnabled = equalizerEnabled && settings.virtualizerEnabled
        equalizerVirtualText.isEnabled = equalizerEnabled && settings.virtualizerEnabled
    }

    private fun resolveEffectName(effectId: Int): String {
        return presetNames.getOrNull(effectId)
            ?: getString(R.string.equalizer_effect_user_defined)
    }

    private fun showPresetPickerDialog() {
        if (presetNames.isEmpty()) return
        val selected = latestSettings.selectedEffectId.coerceIn(0, presetNames.lastIndex)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.equalizer)
            .setSingleChoiceItems(presetNames.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                if (which == selected) return@setSingleChoiceItems
                equalizerViewModel.setSelectedEffectId(which)
                applyAudioEffects()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun saveCurrentCustomLevels() {
        val stored = loadAudioEffectSettingsUseCase()
        val updated = if (latestSettings.bandMode == TEN_BAND_MODE) {
            stored.copy(customTenBandLevels = currentBandLevels)
        } else {
            stored.copy(customFiveBandLevels = currentBandLevels)
        }
        saveAudioEffectSettingsUseCase(updated)
    }

    private fun resolveBandLevels(
        settings: EqualizerPreference,
        stored: AudioEffectSettings
    ): MutableList<Int> {
        val isTenBand = settings.bandMode == TEN_BAND_MODE
        val defaults = EqualizerPresets.defaultBands(isTenBand)
        val selected = settings.selectedEffectId
        return if (selected != USER_PRESET_ID && selected in defaults.indices) {
            defaults[selected].toMutableList()
        } else {
            (if (isTenBand) stored.customTenBandLevels else stored.customFiveBandLevels).toMutableList()
        }
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

    companion object {
        private const val USER_PRESET_ID = 0
        private const val TEN_BAND_MODE = 1
    }
}

package gd.app.musicplayer.feature.equalizer

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.AdapterView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.extractValidatedText
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.BaseDialog
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.datastore.EqualizerPreference
import gd.app.musicplayer.domain.repository.EqualizerPresetRecord
import gd.app.musicplayer.databinding.FragmentEqualizerBinding
import gd.app.musicplayer.domain.usecase.equalizer.CreateEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.DeleteEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.LoadEqualizerPresetsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.SaveEqualizerCustomLevelsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.UpdateEqualizerPresetUseCase
import gd.app.musicplayer.playback.ProcessPlayerHolder
import gd.app.musicplayer.playback.effects.AudioEffectsManager
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EqualizerFragment : ViewBindingFragment<FragmentEqualizerBinding>() {

    @Inject lateinit var saveEqualizerCustomLevelsUseCase: SaveEqualizerCustomLevelsUseCase
    @Inject lateinit var loadEqualizerPresetsUseCase: LoadEqualizerPresetsUseCase
    @Inject lateinit var createEqualizerPresetUseCase: CreateEqualizerPresetUseCase
    @Inject lateinit var updateEqualizerPresetUseCase: UpdateEqualizerPresetUseCase
    @Inject lateinit var deleteEqualizerPresetUseCase: DeleteEqualizerPresetUseCase
    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory
    @Inject lateinit var audioEffectsManager: AudioEffectsManager
    @Inject lateinit var processPlayerHolder: ProcessPlayerHolder

    private val equalizerViewModel: EqualizerViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private lateinit var equalizerBandAdapter: EqualizerBandAdapter
    private var presetRecords: List<EqualizerPresetRecord> = emptyList()
    private var currentBandLevels: MutableList<Int> = mutableListOf()

    private var latestSettings: EqualizerPreference = EqualizerPreference()
    private var isRendering: Boolean = false
    private var lastAnimatedPresetId: Int? = null
    private var lastAnimatedBandMode: Int? = null
    private var loadedBandMode: Int? = null
    private var bandSaveJob: Job? = null
    private var bassApplyJob: Job? = null
    private var virtualizerApplyJob: Job? = null

    override fun onCreateBinding(inflater: LayoutInflater): FragmentEqualizerBinding {
        return FragmentEqualizerBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentEqualizerBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupEqualizerSwitch(binding)
        setupPresetSelector(binding)
        setupEditAndSave(binding)
        setupBandRecycler(binding)
        setupBassAndVirtualizer(binding)
        setupEnableTipGuard(binding)
        observeSettings()
    }

    override fun onDestroyView() {
        (activity as? EqualizerActivity)?.equalizerTipGuard()?.clearShieldViews()
        super.onDestroyView()
    }

    fun reloadFromSettings() {
        if (!isAdded || binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = equalizerViewModel.settings.value
            ensurePresetRecords(settings, force = true)
            latestSettings = settings
            currentBandLevels = resolveBandLevels(settings)
            renderAll(requireBinding(), settings)
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                equalizerViewModel.settings.collect { settings ->
                    ensurePresetRecords(settings)
                    latestSettings = settings
                    currentBandLevels = resolveBandLevels(settings)
                    renderAll(requireBinding(), settings)
                }
            }
        }
    }

    private fun setupEqualizerSwitch(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerSwitchRow.bindSwitchRow(equalizerBox)
        equalizerBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener

            latestSettings = latestSettings.copy(equalizerEnabled = isChecked)
            renderMainEnabledState(requireBinding(), latestSettings)
            equalizerBandAdapter.submit(
                labels = EqualizerPresets.frequencies(latestSettings.bandMode == TEN_BAND_MODE),
                levels = currentBandLevels.toIntArray(),
                enabled = isChecked
            )
            persistAndApply {
                equalizerViewModel.persistEqualizerEnabled(isChecked)
            }
        }
    }

    private fun setupPresetSelector(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerEffectLayout.installCouiPressFeedback()
        equalizerEffectLayout.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showPresetPickerDialog()
        }
    }

    private fun setupEditAndSave(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerEdit.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showEditPresetDialog()
        }

        equalizerSave.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showSavePresetDialog()
        }
    }

    private fun setupBandRecycler(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerRecycler.apply {
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
                    syncCustomPresetFromCurrentLevels()
                    switchToCustomPresetIfNeeded()
                    applyBandsLive()
                    scheduleBandSave()
                },
                onTrackingChanged = { tracking ->
                    updateGestureInterception(tracking)
                },
                applyTheme = ::applyThemeTo
            )
            adapter = equalizerBandAdapter
        }
    }

    private fun setupEnableTipGuard(binding: FragmentEqualizerBinding) {
        val host = activity as? EqualizerActivity ?: return
        val guard = host.equalizerTipGuard()
        guard.clearShieldViews()
        guard.setEqualizerToggle(binding.equalizerBox)
        guard.addShieldViews(
            binding.equalizerEffectLayout,
            binding.equalizerRecycler
        )
    }

    private fun setupBassAndVirtualizer(binding: FragmentEqualizerBinding) = with(binding) {
        equalizerBassRow.bindSwitchRow(equalizerBassBox)
        equalizerVirtualRow.bindSwitchRow(equalizerVirtualBox)

        equalizerBassBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener

            latestSettings = latestSettings.copy(bassEnabled = isChecked)
            renderBassAndVirtualizerEnabledState(requireBinding(), latestSettings)
            persistAndApply {
                equalizerViewModel.persistBassEnabled(isChecked)
            }
        }

        equalizerVirtualBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener

            latestSettings = latestSettings.copy(virtualizerEnabled = isChecked)
            renderBassAndVirtualizerEnabledState(requireBinding(), latestSettings)
            persistAndApply {
                equalizerViewModel.persistVirtualizerEnabled(isChecked)
            }
        }

        equalizerBassProgress.setOnSliderChangeListener(
            onTrackingChanged = ::updateGestureInterception
        ) { progress, _ ->
            val max = equalizerBassProgress.max
            equalizerBassProgressDes.text = progress.toPercentText(max)
            if (isRendering) return@setOnSliderChangeListener

            val value = progress / max.toFloat()
            latestSettings = latestSettings.copy(bassProgress = value)
            bassApplyJob?.cancel()
            bassApplyJob = persistAndApplyDelayed(BASS_VIRTUALIZER_APPLY_DELAY_MS) {
                equalizerViewModel.persistBassProgress(value)
            }
        }

        equalizerVirtualProgress.setOnSliderChangeListener(
            onTrackingChanged = ::updateGestureInterception
        ) { progress, _ ->
            val max = equalizerVirtualProgress.max
            equalizerVirtualProgressDes.text = progress.toPercentText(max)
            if (isRendering) return@setOnSliderChangeListener

            val value = progress / max.toFloat()
            latestSettings = latestSettings.copy(virtualizerProgress = value)
            virtualizerApplyJob?.cancel()
            virtualizerApplyJob = persistAndApplyDelayed(BASS_VIRTUALIZER_APPLY_DELAY_MS) {
                equalizerViewModel.persistVirtualizerProgress(value)
            }
        }
    }

    private fun renderAll(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        isRendering = true

        equalizerBox.isChecked = settings.equalizerEnabled
        equalizerText.text = resolveEffectName(settings.selectedEffectId)
        equalizerSave.isSelected = settings.selectedEffectId == USER_PRESET_ID

        renderMainEnabledState(binding, settings)
        renderBandRecycler(binding, settings)
        renderBassAndVirtualizer(binding, settings)

        isRendering = false
    }

    private fun renderMainEnabledState(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        val enabled = settings.equalizerEnabled
        equalizerEffectLayout.isEnabled = enabled
        equalizerEffectTitle.isEnabled = enabled
        equalizerText.isEnabled = enabled
        equalizerTextArrow.isEnabled = enabled
        equalizerEdit.isEnabled = enabled
        equalizerSave.isEnabled = enabled

        equalizerSeekGroup.isEnabled = enabled
        equalizerRecycler.isEnabled = enabled
    }

    private fun renderBandRecycler(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        if (
            lastAnimatedPresetId != settings.selectedEffectId ||
            lastAnimatedBandMode != settings.bandMode
        ) {
            equalizerBandAdapter.markAnimationsPending()
            lastAnimatedPresetId = settings.selectedEffectId
            lastAnimatedBandMode = settings.bandMode
        }

        equalizerBandAdapter.submit(
            labels = EqualizerPresets.frequencies(settings.bandMode == TEN_BAND_MODE),
            levels = currentBandLevels.toIntArray(),
            enabled = settings.equalizerEnabled
        )
    }

    private fun renderBassAndVirtualizer(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        equalizerBassBox.isChecked = settings.bassEnabled
        equalizerVirtualBox.isChecked = settings.virtualizerEnabled

        val bassProgress = settings.bassProgress.toSliderProgress(equalizerBassProgress.max)
        equalizerBassProgress.setProgress(bassProgress)
        equalizerBassProgressDes.text = bassProgress.toPercentText(equalizerBassProgress.max)

        val virtualProgress =
            settings.virtualizerProgress.toSliderProgress(equalizerVirtualProgress.max)
        equalizerVirtualProgress.setProgress(virtualProgress)
        equalizerVirtualProgressDes.text =
            virtualProgress.toPercentText(equalizerVirtualProgress.max)

        renderBassAndVirtualizerEnabledState(binding, settings)
    }

    private fun renderBassAndVirtualizerEnabledState(
        binding: FragmentEqualizerBinding,
        settings: EqualizerPreference
    ) = with(binding) {
        equalizerBassProgress.isEnabled = settings.bassEnabled
        equalizerBassProgressDes.isEnabled = settings.bassEnabled

        equalizerVirtualProgress.isEnabled = settings.virtualizerEnabled
        equalizerVirtualProgressDes.isEnabled = settings.virtualizerEnabled
    }

    private fun resolveEffectName(effectId: Int): String {
        return presetRecords.getOrNull(effectId)?.name
            ?: getString(R.string.equalizer_effect_user_defined)
    }

    private fun showPresetPickerDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = presetRecords.ifEmpty { loadPresetRecords(latestSettings) }.map { it.name }
            if (items.isEmpty()) return@launch

            val selected = latestSettings.selectedEffectId.coerceIn(0, items.lastIndex)
            val config = materialDialogConfigFactory
                .createMaterialListDialogConfig(requireContext(), items)
                .apply {
                    titleText = getString(R.string.equalizer_effect_msg)
                    selectedItemIndex = selected
                    onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                        if (which == selected) return@OnItemClickListener
                        persistAndApply {
                            equalizerViewModel.persistSelectedEffectId(which)
                            latestSettings = latestSettings.copy(selectedEffectId = which)
                        }
                        BaseDialog.dismissAll(requireActivity())
                    }
                }

            OptionsListDialog.show(requireActivity(), config)
        }
    }

    private suspend fun saveCurrentCustomLevels() {
        saveEqualizerCustomLevelsUseCase(
            tenBand = latestSettings.bandMode == TEN_BAND_MODE,
            bands = currentBandLevels.toList()
        )
    }

    private fun showEditPresetDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val editablePresets = presetRecords.ifEmpty {
                loadPresetRecords(latestSettings)
            }.drop(1)
            if (editablePresets.isEmpty()) {
                return@launch
            }

            val config = materialDialogConfigFactory
                .createMaterialListDialogConfig(
                    requireContext(),
                    editablePresets.map { it.name }
                )
                .apply {
                    titleText = getString(R.string.equalizer_edit)
                    onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                        BaseDialog.dismissAll(requireActivity())
                        editablePresets.getOrNull(which)?.let(::showEditActionsDialog)
                    }
                }

            OptionsListDialog.show(requireActivity(), config)
        }
    }

    private fun showEditActionsDialog(record: EqualizerPresetRecord) {
        val options = buildList {
            add(getString(R.string.rename))
            if (record.canDelete) {
                add(getString(R.string.delete))
            }
        }

        val config = materialDialogConfigFactory
            .createMaterialListDialogConfig(requireContext(), options)
            .apply {
                titleText = getString(R.string.equalizer_edit)
                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    BaseDialog.dismissAll(requireActivity())
                    when {
                        which == 0 -> showRenamePresetDialog(record)
                        which == 1 && record.canDelete -> deletePreset(record)
                    }
                }
            }

        OptionsListDialog.show(requireActivity(), config)
    }

    private fun showRenamePresetDialog(record: EqualizerPresetRecord) {
        val input = buildPresetInput(record.name)
        showNameInputDialog(
            title = getString(R.string.rename),
            input = input
        ) { dialog ->
            val name = input.extractValidatedText(keepPathSeparators = false)
            if (name == null) {
                ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
                return@showNameInputDialog false
            }
            if (isPresetNameTaken(name, exceptId = record.id)) {
                ToastUtil.show(requireContext(), R.string.name_exist)
                return@showNameInputDialog false
            }

            viewLifecycleOwner.lifecycleScope.launch {
                updateEqualizerPresetUseCase(
                    id = record.id,
                    name = name,
                    bands = record.bands,
                    tenBand = latestSettings.bandMode == TEN_BAND_MODE
                )
                ensurePresetRecords(latestSettings, force = true)
                renderAll(requireBinding(), latestSettings)
                ToastUtil.show(requireContext(), R.string.rename_success)
            }
            dialog.dismiss()
            true
        }
    }

    private fun showSavePresetDialog() {
        val input = buildPresetInput(buildNextPresetName())
        input.setSelection(0, input.text.length)
        showNameInputDialog(
            title = getString(R.string.save),
            input = input
        ) { dialog ->
            val name = input.extractValidatedText(keepPathSeparators = false)
            if (name == null) {
                ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
                return@showNameInputDialog false
            }
            if (isPresetNameTaken(name)) {
                ToastUtil.show(requireContext(), R.string.name_exist)
                return@showNameInputDialog false
            }

            viewLifecycleOwner.lifecycleScope.launch {
                createEqualizerPresetUseCase(
                    name = name,
                    bands = currentBandLevels.toList(),
                    tenBand = latestSettings.bandMode == TEN_BAND_MODE,
                    preset = EqualizerPresetRecord.USER_CREATED_PRESET
                )
                ensurePresetRecords(latestSettings, force = true)
                equalizerViewModel.persistSelectedEffectId(presetRecords.lastIndex)
                latestSettings = latestSettings.copy(selectedEffectId = presetRecords.lastIndex)
                applyAudioEffects()
                ToastUtil.show(requireContext(), R.string.save_success)
            }
            dialog.dismiss()
            true
        }
    }

    private fun deletePreset(record: EqualizerPresetRecord) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentIndex = latestSettings.selectedEffectId
            val deleteIndex = presetRecords.indexOfFirst { it.id == record.id }
            if (deleteIndex == -1) {
                return@launch
            }

            deleteEqualizerPresetUseCase(
                id = record.id,
                tenBand = latestSettings.bandMode == TEN_BAND_MODE
            )
            ensurePresetRecords(latestSettings, force = true)

            val nextIndex = when {
                currentIndex == deleteIndex -> USER_PRESET_ID
                currentIndex > deleteIndex -> currentIndex - 1
                else -> currentIndex
            }.coerceIn(0, presetRecords.lastIndex.coerceAtLeast(0))

            equalizerViewModel.persistSelectedEffectId(nextIndex)
            latestSettings = latestSettings.copy(selectedEffectId = nextIndex)
            applyAudioEffects()
            ToastUtil.show(requireContext(), R.string.delete_success)
        }
    }

    private fun buildPresetInput(initialValue: String): EditText {
        val input = layoutInflater.inflate(
            R.layout.layout_edittext,
            null as ViewGroup?
        ) as com.coui.appcompat.edittext.COUIEditText
        input.applyLengthFilter(120)
        input.setFastDeletable(true)
        input.setText(initialValue)
        applyThemeTo(input)
        input.showKeyboardDelayed()
        return input
    }

    private fun showNameInputDialog(
        title: String,
        input: EditText,
        onPositiveClick: (DialogInterface) -> Boolean
    ) {
        val config = materialDialogConfigFactory
            .createMaterialMessageDialogConfig(requireContext())
            .apply {
                titleText = title
                customView = input
                positiveButtonText = getString(R.string.ok)
                negativeButtonText = getString(R.string.cancel)
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    if (onPositiveClick(dialog)) {
                        dialog.dismiss()
                    }
                }
            }

        MessageDialog.show(requireActivity(), config)
    }

    private fun isPresetNameTaken(
        name: String,
        exceptId: Long? = null
    ): Boolean {
        return presetRecords.any { record ->
            record.id != exceptId && record.name.equals(name, ignoreCase = true)
        }
    }

    private fun buildNextPresetName(): String {
        val prefix = getString(R.string.equalizer_new_effect)
        var index = 1
        while (true) {
            val candidate = "$prefix $index"
            if (!isPresetNameTaken(candidate)) {
                return candidate
            }
            index += 1
        }
    }

    private fun resolveBandLevels(settings: EqualizerPreference): MutableList<Int> {
        val selected = settings.selectedEffectId
        return presetRecords.getOrNull(selected)?.bands?.toMutableList()
            ?: MutableList(if (settings.bandMode == TEN_BAND_MODE) 10 else 5) { 0 }
    }

    private fun applyAudioEffects() {
        playerViewModel.applyAudioEffects(requireContext())
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

    private suspend fun loadPresetRecords(settings: EqualizerPreference): List<EqualizerPresetRecord> {
        val tenBand = settings.bandMode == TEN_BAND_MODE
        val presets = loadEqualizerPresetsUseCase(tenBand)
        return presets.ifEmpty {
            EqualizerPresets.defaultPresetNames(requireContext()).mapIndexed { index, name ->
                EqualizerPresetRecord(
                    id = index.toLong(),
                    name = name,
                    bands = EqualizerPresets.defaultBands(tenBand).getOrElse(index) {
                        List(if (tenBand) 10 else 5) { 0 }
                    },
                    preset = EqualizerPresetRecord.FACTORY_PRESET
                )
            }
        }
    }

    private suspend fun ensurePresetRecords(
        settings: EqualizerPreference,
        force: Boolean = false
    ) {
        if (!force && loadedBandMode == settings.bandMode && presetRecords.isNotEmpty()) {
            return
        }
        presetRecords = loadPresetRecords(settings)
        loadedBandMode = settings.bandMode
    }

    private fun switchToCustomPresetIfNeeded() {
        if (latestSettings.selectedEffectId == USER_PRESET_ID) return

        latestSettings = latestSettings.copy(selectedEffectId = USER_PRESET_ID)
        val binding = binding ?: return
        binding.equalizerText.text = resolveEffectName(USER_PRESET_ID)
        binding.equalizerSave.isSelected = true

        viewLifecycleOwner.lifecycleScope.launch {
            equalizerViewModel.persistSelectedEffectId(USER_PRESET_ID)
        }
    }

    private fun applyBandsLive() {
        if (!latestSettings.equalizerEnabled) return
        val levels = currentBandLevels.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            val player = processPlayerHolder.playerOrNull() ?: return@launch
            if (!audioEffectsManager.ensureAttached(player)) return@launch
            audioEffectsManager.setEqualizerUiLevels(levels, enabled = true)
        }
    }

    private fun scheduleBandSave() {
        bandSaveJob?.cancel()
        bandSaveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(BAND_SAVE_DEBOUNCE_MS)
            saveCurrentCustomLevels()
        }
    }

    private fun updateGestureInterception(intercept: Boolean) {
        val binding = binding ?: return
        binding.equalizerRecycler.requestDisallowInterceptTouchEvent(intercept)
        binding.root.requestDisallowInterceptTouchEvent(intercept)
        (activity as? EqualizerActivity)?.requestPagerDisallowInterceptTouchEvent(intercept)
    }

    private fun syncCustomPresetFromCurrentLevels() {
        if (presetRecords.isEmpty()) {
            return
        }

        val customRecord = presetRecords.firstOrNull() ?: return
        val updatedCustom = customRecord.copy(
            bands = currentBandLevels.toList()
        )
        presetRecords = buildList(presetRecords.size) {
            add(updatedCustom)
            addAll(presetRecords.drop(1))
        }
    }

    companion object {
        private const val USER_PRESET_ID = 0
        private const val TEN_BAND_MODE = 1
        private const val BAND_SAVE_DEBOUNCE_MS = 1000L
        private const val BASS_VIRTUALIZER_APPLY_DELAY_MS = 150L
    }
}

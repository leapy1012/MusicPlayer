package gd.app.musicplayer.ui.equalizer

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.AdapterView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.extractValidatedText
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.BaseDialog
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.view.RotateStepBar
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.data.local.preference.EqualizerPreference
import gd.app.musicplayer.domain.repository.EqualizerPresetRecord
import gd.app.musicplayer.databinding.FragmentEqualizerBinding
import gd.app.musicplayer.domain.usecase.equalizer.CreateEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.DeleteEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.LoadAudioEffectSettingsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.LoadEqualizerPresetsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.SaveAudioEffectSettingsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.UpdateEqualizerPresetUseCase
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EqualizerFragment : ViewBindingFragment<FragmentEqualizerBinding>() {

    @Inject lateinit var loadAudioEffectSettingsUseCase: LoadAudioEffectSettingsUseCase
    @Inject lateinit var saveAudioEffectSettingsUseCase: SaveAudioEffectSettingsUseCase
    @Inject lateinit var loadEqualizerPresetsUseCase: LoadEqualizerPresetsUseCase
    @Inject lateinit var createEqualizerPresetUseCase: CreateEqualizerPresetUseCase
    @Inject lateinit var updateEqualizerPresetUseCase: UpdateEqualizerPresetUseCase
    @Inject lateinit var deleteEqualizerPresetUseCase: DeleteEqualizerPresetUseCase
    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private val equalizerViewModel: EqualizerViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private lateinit var equalizerBandAdapter: EqualizerBandAdapter
    private var presetRecords: List<EqualizerPresetRecord> = emptyList()
    private var currentBandLevels: MutableList<Int> = mutableListOf()

    private var latestSettings: EqualizerPreference = EqualizerPreference()
    private var isRendering: Boolean = false
    private var lastAnimatedPresetId: Int? = null
    private var lastAnimatedBandMode: Int? = null
    private var loadedBandMode: Int? = null

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
        observeSettings()
        updateContentHeight()
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
            showEditPresetDialog()
        }

        equalizerSave.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showSavePresetDialog()
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
                    updateGestureInterception(tracking)
                },
                applyTheme = ::applyThemeTo
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
                    updateGestureInterception(isTracking)
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
                    updateGestureInterception(isTracking)
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
        equalizerEditParent.isEnabled = settings.equalizerEnabled
        equalizerEffectLayout.isEnabled = settings.equalizerEnabled
        equalizerText.isEnabled = settings.equalizerEnabled
        equalizerTextArrow.isEnabled = settings.equalizerEnabled
        equalizerEdit.isEnabled = settings.equalizerEnabled
        equalizerSave.isEnabled = settings.equalizerEnabled

        equalizerSeekParent.equalizerSeekGroup.isEnabled = settings.equalizerEnabled
        equalizerSeekParent.equalizerRecycler.isEnabled = settings.equalizerEnabled
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
        equalizerBassBox.isEnabled = true
        equalizerVirtualBox.isEnabled = true

        equalizerBassRotate.isEnabled = settings.bassEnabled
        equalizerBassText.isEnabled = settings.bassEnabled

        equalizerVirtualRotate.isEnabled = settings.virtualizerEnabled
        equalizerVirtualText.isEnabled = settings.virtualizerEnabled
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
                        equalizerViewModel.setSelectedEffectId(which)
                        applyAudioEffects()
                        BaseDialog.dismissAll(requireActivity())
                    }
                }

            OptionsListDialog.show(requireActivity(), config)
        }
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
                equalizerViewModel.setSelectedEffectId(presetRecords.lastIndex)
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

            equalizerViewModel.setSelectedEffectId(nextIndex)
            applyAudioEffects()
            ToastUtil.show(requireContext(), R.string.delete_success)
        }
    }

    private fun buildPresetInput(initialValue: String): EditText {
        val input = layoutInflater.inflate(
            R.layout.layout_edittext,
            null as ViewGroup?
        ) as EditText
        input.applyLengthFilter(120)
        input.setText(initialValue)
        applyThemeTo(input)
        input.showKeyboardDelayed()
        return input
    }

    private fun showNameInputDialog(
        title: String,
        input: EditText,
        onPositiveClick: (MessageDialog) -> Boolean
    ) {
        val config = materialDialogConfigFactory
            .createMaterialMessageDialogConfig(requireContext())
            .apply {
                titleText = title
                customView = input
                positiveButtonText = getString(R.string.ok)
                negativeButtonText = getString(R.string.cancel)
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    onPositiveClick(dialog as MessageDialog)
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

    private suspend fun loadPresetRecords(settings: EqualizerPreference): List<EqualizerPresetRecord> {
        val tenBand = settings.bandMode == TEN_BAND_MODE
        val presets = loadEqualizerPresetsUseCase(tenBand)
        return if (presets.isNotEmpty()) {
            presets
        } else {
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

    private fun updateGestureInterception(intercept: Boolean) {
        val binding = binding ?: return
        binding.equalizerSeekParent.equalizerRecycler.requestDisallowInterceptTouchEvent(intercept)
        binding.root.requestDisallowInterceptTouchEvent(intercept)
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

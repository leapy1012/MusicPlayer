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
import androidx.viewbinding.ViewBinding
import com.coui.appcompat.seekbar.COUISeekBar
import gd.app.musicplayer.core.designsystem.view.RotateStepBar
import gd.app.musicplayer.core.datastore.EqualizerPreference
import gd.app.musicplayer.domain.repository.EqualizerPresetRecord
import gd.app.musicplayer.databinding.FragmentEqualizerBinding
import gd.app.musicplayer.databinding.FragmentEqualizerPicturedBinding
import gd.app.musicplayer.domain.usecase.equalizer.CreateEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.DeleteEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.LoadEqualizerPresetsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.SaveEqualizerCustomLevelsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.UpdateEqualizerPresetUseCase
import gd.app.musicplayer.playback.ProcessPlayerHolder
import gd.app.musicplayer.playback.effects.AudioEffectsManager
import gd.app.musicplayer.playback.effects.EffectGroupPresets
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class EqualizerFragment : ViewBindingFragment<ViewBinding>() {

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
    private var bassTracking: Boolean = false
    private var virtualizerTracking: Boolean = false

    private val picturedStyle: Boolean
        get() = (activity as? EqualizerActivity)?.usesPicturedStyle()
            ?: EqualizerUiStyle.isPictured(themeEngine)

    override fun onCreateBinding(inflater: LayoutInflater): ViewBinding {
        return if (picturedStyle) {
            FragmentEqualizerPicturedBinding.inflate(inflater)
        } else {
            FragmentEqualizerBinding.inflate(inflater)
        }
    }

    override fun onBindingCreated(
        binding: ViewBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupEqualizerSwitch()
        setupPresetSelector()
        setupEditAndSave()
        setupBandRecycler()
        setupBassAndVirtualizer()
        setupEnableTipGuard()
        observeSettings()
    }

    private fun coui(): FragmentEqualizerBinding =
        requireBinding() as FragmentEqualizerBinding

    private fun pictured(): FragmentEqualizerPicturedBinding =
        requireBinding() as FragmentEqualizerPicturedBinding

    override fun onDestroyView() {
        (activity as? EqualizerActivity)?.equalizerTipGuard()?.clearShieldViews()
        super.onDestroyView()
    }

    fun reloadFromSettings() {
        if (!isAdded || binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = equalizerViewModel.settings.value.withEffectGroupOverride()
            ensurePresetRecords(settings, force = true)
            latestSettings = settings
            currentBandLevels = resolveBandLevels(settings)
            renderAll(settings)
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                equalizerViewModel.settings.collect { stored ->
                    val settings = stored.withEffectGroupOverride()
                    ensurePresetRecords(settings)
                    latestSettings = settings.copy(
                        bassProgress = if (bassTracking) {
                            latestSettings.bassProgress
                        } else {
                            settings.bassProgress
                        },
                        virtualizerProgress = if (virtualizerTracking) {
                            latestSettings.virtualizerProgress
                        } else {
                            settings.virtualizerProgress
                        },
                    )
                    currentBandLevels = resolveBandLevels(latestSettings)
                    renderAll(latestSettings)
                }
            }
        }
    }


    private fun setupEqualizerSwitch() {
        val box = if (picturedStyle) pictured().equalizerBox else coui().equalizerBox
        val row = if (picturedStyle) pictured().equalizerSwitchRow else coui().equalizerSwitchRow
        row.bindSwitchRow(box)
        box.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            latestSettings = latestSettings.copy(equalizerEnabled = isChecked)
            renderMainEnabledState(latestSettings)
            equalizerBandAdapter.submit(
                labels = EqualizerPresets.frequencies(latestSettings.bandMode == TEN_BAND_MODE),
                levels = currentBandLevels.toIntArray(),
                enabled = isChecked
            )
            persistAndApply {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistEqualizerEnabled(isChecked)
            }
        }
    }

    private fun setupPresetSelector() {
        val layout = if (picturedStyle) pictured().equalizerEffectLayout else coui().equalizerEffectLayout
        layout.installCouiPressFeedback()
        layout.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showPresetPickerDialog()
        }
    }

    private fun setupEditAndSave() {
        val edit = if (picturedStyle) pictured().equalizerEdit else coui().equalizerEdit
        val save = if (picturedStyle) pictured().equalizerSave else coui().equalizerSave
        edit.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showEditPresetDialog()
        }
        save.setOnClickListener {
            if (isRendering || !latestSettings.equalizerEnabled) return@setOnClickListener
            showSavePresetDialog()
        }
    }

    private fun setupBandRecycler() {
        val recycler = if (picturedStyle) pictured().equalizerRecycler else coui().equalizerRecycler
        recycler.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        equalizerBandAdapter = EqualizerBandAdapter(
            layoutInflater = layoutInflater,
            pictured = picturedStyle,
            onBandChanged = { bandIndex, levelMb, fromUser ->
                if (isRendering || !fromUser) return@EqualizerBandAdapter
                if (bandIndex !in currentBandLevels.indices) return@EqualizerBandAdapter
                currentBandLevels[bandIndex] = levelMb
                syncCustomPresetFromCurrentLevels()
                switchToCustomPresetIfNeeded()
                applyBandsLive()
                scheduleBandSave()
            },
            onTrackingChanged = { tracking -> updateGestureInterception(tracking) },
            applyTheme = ::applyThemeTo
        )
        recycler.adapter = equalizerBandAdapter
    }

    private fun setupEnableTipGuard() {
        val host = activity as? EqualizerActivity ?: return
        val guard = host.equalizerTipGuard()
        guard.clearShieldViews()
        val box = if (picturedStyle) pictured().equalizerBox else coui().equalizerBox
        val effect = if (picturedStyle) pictured().equalizerEffectLayout else coui().equalizerEffectLayout
        val recycler = if (picturedStyle) pictured().equalizerRecycler else coui().equalizerRecycler
        guard.setEqualizerToggle(box)
        guard.addShieldViews(effect, recycler)
    }

    private fun setupBassAndVirtualizer() {
        if (picturedStyle) {
            setupPicturedBassAndVirtualizer()
        } else {
            setupCouiBassAndVirtualizer()
        }
    }

    private fun setupCouiBassAndVirtualizer() = with(coui()) {
        equalizerBassRow.bindSwitchRow(equalizerBassBox)
        equalizerVirtualRow.bindSwitchRow(equalizerVirtualBox)
        equalizerBassBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            latestSettings = latestSettings.copy(bassEnabled = isChecked)
            renderBassAndVirtualizerEnabledState(latestSettings)
            persistAndApply {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistBassEnabled(isChecked)
            }
        }
        equalizerVirtualBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            latestSettings = latestSettings.copy(virtualizerEnabled = isChecked)
            renderBassAndVirtualizerEnabledState(latestSettings)
            persistAndApply {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistVirtualizerEnabled(isChecked)
            }
        }
        equalizerBassProgress.setOnSliderChangeListener(
            onTrackingChanged = { tracking ->
                bassTracking = tracking
                updateGestureInterception(tracking)
            }
        ) { progress, _ ->
            val max = equalizerBassProgress.max
            equalizerBassProgressDes.text = progress.toPercentText(max)
            if (isRendering) return@setOnSliderChangeListener
            val value = progress / max.toFloat()
            latestSettings = latestSettings.copy(bassProgress = value)
            bassApplyJob?.cancel()
            bassApplyJob = persistAndApplyDelayed(BASS_VIRTUALIZER_APPLY_DELAY_MS) {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistBassProgress(value)
            }
        }
        equalizerVirtualProgress.setOnSliderChangeListener(
            onTrackingChanged = { tracking ->
                virtualizerTracking = tracking
                updateGestureInterception(tracking)
            }
        ) { progress, _ ->
            val max = equalizerVirtualProgress.max
            equalizerVirtualProgressDes.text = progress.toPercentText(max)
            if (isRendering) return@setOnSliderChangeListener
            val value = progress / max.toFloat()
            latestSettings = latestSettings.copy(virtualizerProgress = value)
            virtualizerApplyJob?.cancel()
            virtualizerApplyJob = persistAndApplyDelayed(BASS_VIRTUALIZER_APPLY_DELAY_MS) {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistVirtualizerProgress(value)
            }
        }
    }

    private fun setupPicturedBassAndVirtualizer() = with(pictured()) {
        equalizerBassSwitchHost.bringToFront()
        equalizerVirtualSwitchHost.bringToFront()
        equalizerBassBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            latestSettings = latestSettings.copy(bassEnabled = isChecked)
            renderBassAndVirtualizerEnabledState(latestSettings)
            persistAndApply {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistBassEnabled(isChecked)
            }
        }
        equalizerVirtualBox.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            latestSettings = latestSettings.copy(virtualizerEnabled = isChecked)
            renderBassAndVirtualizerEnabledState(latestSettings)
            persistAndApply {
                equalizerViewModel.disableEffectGroup()
                equalizerViewModel.persistVirtualizerEnabled(isChecked)
            }
        }
        equalizerBassRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
                override fun onRotationTrackingChanged(view: RotateStepBar, isTracking: Boolean) {
                    bassTracking = isTracking
                    updateGestureInterception(isTracking)
                }
                override fun onRotationChanged(view: RotateStepBar, progress: Int) {
                    if (isRendering) return
                    val value = progress / view.getMax().toFloat()
                    latestSettings = latestSettings.copy(bassProgress = value)
                    bassApplyJob?.cancel()
                    bassApplyJob = persistAndApplyDelayed(BASS_VIRTUALIZER_APPLY_DELAY_MS) {
                        equalizerViewModel.disableEffectGroup()
                        equalizerViewModel.persistBassProgress(value)
                    }
                }
            }
        )
        equalizerVirtualRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
                override fun onRotationTrackingChanged(view: RotateStepBar, isTracking: Boolean) {
                    virtualizerTracking = isTracking
                    updateGestureInterception(isTracking)
                }
                override fun onRotationChanged(view: RotateStepBar, progress: Int) {
                    if (isRendering) return
                    val value = progress / view.getMax().toFloat()
                    latestSettings = latestSettings.copy(virtualizerProgress = value)
                    virtualizerApplyJob?.cancel()
                    virtualizerApplyJob = persistAndApplyDelayed(BASS_VIRTUALIZER_APPLY_DELAY_MS) {
                        equalizerViewModel.disableEffectGroup()
                        equalizerViewModel.persistVirtualizerProgress(value)
                    }
                }
            }
        )
    }

    private fun renderAll(settings: EqualizerPreference) {
        isRendering = true
        if (picturedStyle) {
            with(pictured()) {
                equalizerBox.isChecked = settings.equalizerEnabled
                equalizerText.text = resolveEffectName(settings.selectedEffectId)
                equalizerSave.isSelected = settings.selectedEffectId == USER_PRESET_ID
            }
        } else {
            with(coui()) {
                equalizerBox.isChecked = settings.equalizerEnabled
                equalizerText.text = resolveEffectName(settings.selectedEffectId)
                equalizerSave.isSelected = settings.selectedEffectId == USER_PRESET_ID
            }
        }
        renderMainEnabledState(settings)
        renderBandRecycler(settings)
        renderBassAndVirtualizer(settings)
        isRendering = false
    }

    private fun renderMainEnabledState(settings: EqualizerPreference) {
        val enabled = settings.equalizerEnabled
        if (picturedStyle) {
            with(pictured()) {
                equalizerEffectLayout.isEnabled = enabled
                equalizerEffectTitle.isEnabled = enabled
                equalizerText.isEnabled = enabled
                equalizerTextArrow.isEnabled = enabled
                equalizerEdit.isEnabled = enabled
                equalizerSave.isEnabled = enabled
                equalizerSeekGroup.isEnabled = enabled
                equalizerRecycler.isEnabled = enabled
            }
        } else {
            with(coui()) {
                equalizerEffectLayout.isEnabled = enabled
                equalizerEffectTitle.isEnabled = enabled
                equalizerText.isEnabled = enabled
                equalizerTextArrow.isEnabled = enabled
                equalizerEdit.isEnabled = enabled
                equalizerSave.isEnabled = enabled
                equalizerSeekGroup.isEnabled = enabled
                equalizerRecycler.isEnabled = enabled
            }
        }
    }

    private fun renderBandRecycler(settings: EqualizerPreference) {
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

    private fun renderBassAndVirtualizer(settings: EqualizerPreference) {
        if (picturedStyle) {
            with(pictured()) {
                equalizerBassBox.isChecked = settings.bassEnabled
                equalizerVirtualBox.isChecked = settings.virtualizerEnabled
                if (!bassTracking) {
                    equalizerBassRotate.setProgress(
                        (settings.bassProgress.coerceIn(0f, 1f) * equalizerBassRotate.getMax()).roundToInt()
                    )
                }
                if (!virtualizerTracking) {
                    equalizerVirtualRotate.setProgress(
                        (settings.virtualizerProgress.coerceIn(0f, 1f) * equalizerVirtualRotate.getMax()).roundToInt()
                    )
                }
            }
        } else {
            with(coui()) {
                equalizerBassBox.isChecked = settings.bassEnabled
                equalizerVirtualBox.isChecked = settings.virtualizerEnabled
                if (!bassTracking) {
                    val bassProgress = settings.bassProgress.toSliderProgress(equalizerBassProgress.max)
                    equalizerBassProgress.setProgress(bassProgress)
                    equalizerBassProgressDes.text = bassProgress.toPercentText(equalizerBassProgress.max)
                }
                if (!virtualizerTracking) {
                    val virtualProgress = settings.virtualizerProgress.toSliderProgress(equalizerVirtualProgress.max)
                    equalizerVirtualProgress.setProgress(virtualProgress)
                    equalizerVirtualProgressDes.text = virtualProgress.toPercentText(equalizerVirtualProgress.max)
                }
            }
        }
        renderBassAndVirtualizerEnabledState(settings)
    }

    private fun renderBassAndVirtualizerEnabledState(settings: EqualizerPreference) {
        if (picturedStyle) {
            with(pictured()) {
                equalizerBassRotate.isEnabled = settings.bassEnabled
                equalizerBassText.isEnabled = settings.bassEnabled
                equalizerVirtualRotate.isEnabled = settings.virtualizerEnabled
                equalizerVirtualText.isEnabled = settings.virtualizerEnabled
            }
        } else {
            with(coui()) {
                equalizerBassProgress.isEnabled = settings.bassEnabled
                equalizerBassProgressDes.isEnabled = settings.bassEnabled
                equalizerVirtualProgress.isEnabled = settings.virtualizerEnabled
                equalizerVirtualProgressDes.isEnabled = settings.virtualizerEnabled
            }
        }
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
                renderAll(latestSettings)
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
        val rootBinding = binding ?: return
        if (picturedStyle) {
            val b = rootBinding as FragmentEqualizerPicturedBinding
            b.equalizerText.text = resolveEffectName(USER_PRESET_ID)
            b.equalizerSave.isSelected = true
        } else {
            val b = rootBinding as FragmentEqualizerBinding
            b.equalizerText.text = resolveEffectName(USER_PRESET_ID)
            b.equalizerSave.isSelected = true
        }

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
        val rootBinding = binding ?: return
        val recycler = if (picturedStyle) {
            (rootBinding as FragmentEqualizerPicturedBinding).equalizerRecycler
        } else {
            (rootBinding as FragmentEqualizerBinding).equalizerRecycler
        }
        recycler.requestDisallowInterceptTouchEvent(intercept)
        (rootBinding.root as ViewGroup).requestDisallowInterceptTouchEvent(intercept)
        (activity as? EqualizerActivity)?.requestPagerDisallowInterceptTouchEvent(intercept)
    }

    /**
     * While an effect group drives playback, the manual EQ, bass and virtualizer read as off,
     * as in the original (`z5.m.b()/d()/m()`); turning any of them on hands control back.
     */
    private fun EqualizerPreference.withEffectGroupOverride(): EqualizerPreference {
        val groupActive = groupSoundEffectEnabled &&
            EffectGroupPresets.find(groupSoundEffectIndex) != null
        if (!groupActive) return this
        return copy(equalizerEnabled = false, bassEnabled = false, virtualizerEnabled = false)
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

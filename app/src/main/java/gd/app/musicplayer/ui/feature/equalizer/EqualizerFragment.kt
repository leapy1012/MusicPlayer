package gd.app.musicplayer.ui.feature.equalizer

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.LinearLayoutManager
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.theme.accentColor
import gd.app.musicplayer.core.theme.messageColor
import gd.app.musicplayer.core.theme.titleColor
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.FragmentEqualizerBinding
import gd.app.musicplayer.databinding.LayoutEdittextBinding
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.PlaybackControllerProvider
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.core.ui.dialog.OptionsListDialog
import gd.app.musicplayer.core.ui.dialog.DialogRegistry
import gd.app.musicplayer.core.ui.dialog.MessageDialog
import gd.app.musicplayer.core.ui.view.RotateStepBar
import gd.app.musicplayer.core.ui.view.SelectBox

class EqualizerFragment : ViewBindingFragment<FragmentEqualizerBinding>(), View.OnClickListener {

    private lateinit var adapter: EqualizerBandAdapter
    private lateinit var settings: AudioEffectsManager.Settings

    private var defaultPresetNames: List<String> = emptyList()
    private var userPresets: MutableList<AudioEffectsManager.UserPreset> = mutableListOf()
    override fun onCreateBinding(inflater: LayoutInflater): FragmentEqualizerBinding =
        FragmentEqualizerBinding.inflate(inflater)


    override fun onBindingCreated(binding: FragmentEqualizerBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        defaultPresetNames = EqualizerPresets.defaultPresetNames(requireContext())
        settings = AudioEffectsManager.loadSettings(requireContext())
        userPresets = AudioEffectsManager.readUserPresets(requireContext(), settings.useTenBand)
            .toMutableList()

        adapter = EqualizerBandAdapter(
            layoutInflater = layoutInflater,
            onBandChanged = ::onBandChanged,
            onTrackingChanged = { tracking ->
                binding.root.requestDisallowInterceptTouchEvent(tracking)
            }
        )

        binding.equalizerSeekParent.equalizerRecycler.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = this@EqualizerFragment.adapter
            // The EQ bands never animate independently. Disabling item animations avoids extra
            // layout work while the user drags a band continuously.
            itemAnimator = null
            setHasFixedSize(true)
        }

        binding.equalizerText.setOnClickListener(this)
        binding.equalizerTextArrow.setOnClickListener(this)
        binding.equalizerEdit.setOnClickListener(this)
        binding.equalizerSave.setOnClickListener(this)

        binding.equalizerBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser) return
                    settings = settings.copy(eqEnabled = isSelected)
                    persistAndApply()
                    renderEnabledState()
                }
            }
        )

        binding.equalizerBassParent.equalizerBassBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser) return
                    settings = settings.copy(bassEnabled = isSelected)
                    persistAndApply()
                    renderEnabledState()
                }
            }
        )

        binding.equalizerBassParent.equalizerVirtualBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser) return
                    settings = settings.copy(virtualizerEnabled = isSelected)
                    persistAndApply()
                    renderEnabledState()
                }
            }
        )

        binding.equalizerBassParent.equalizerBassRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
                override fun onRotationTrackingChanged(
                    view: RotateStepBar,
                    isTracking: Boolean
                ) {
                    binding.root.requestDisallowInterceptTouchEvent(isTracking)
                }

                override fun onRotationChanged(
                    view: RotateStepBar,
                    progress: Int
                ) {
                    val value = progress / view.getMax().toFloat()
                    settings = settings.copy(bassStrength = value)
                    persistAndApply()
                }
            }
        )

        binding.equalizerBassParent.equalizerVirtualRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
                override fun onRotationTrackingChanged(
                    view: RotateStepBar,
                    isTracking: Boolean
                ) {
                    binding.root.requestDisallowInterceptTouchEvent(isTracking)
                }

                override fun onRotationChanged(
                    view: RotateStepBar,
                    progress: Int
                ) {
                    val value = progress / view.getMax().toFloat()
                    settings = settings.copy(virtualizerStrength = value)
                    persistAndApply()
                }
            }
        )

        renderAll()
        updateContentHeight()

    }

    override fun onResume() {
        super.onResume()
        settings = AudioEffectsManager.loadSettings(requireContext())
        userPresets = AudioEffectsManager.readUserPresets(requireContext(), settings.useTenBand)
            .toMutableList()
        renderAll()
        updateContentHeight()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.equalizer_text, R.id.equalizer_text_arrow -> showPresetPicker()
            R.id.equalizer_save -> showSavePresetDialog()
            R.id.equalizer_edit -> showEditPresetList()
        }
    }

    fun reloadFromSettings() {
        if (!isAdded) return
        settings = AudioEffectsManager.loadSettings(requireContext())
        userPresets = AudioEffectsManager.readUserPresets(requireContext(), settings.useTenBand)
            .toMutableList()
        renderAll()
        updateContentHeight()
    }

    private fun renderAll() {
        val binding = requireBinding()
        val levels = settings.customLevels()
        binding.equalizerBox.isSelected = settings.eqEnabled

        binding.equalizerBassParent.equalizerBassBox.isSelected = settings.bassEnabled
        binding.equalizerBassParent.equalizerVirtualBox.isSelected = settings.virtualizerEnabled
        binding.equalizerBassParent.equalizerBassRotate.setProgress(
            (settings.bassStrength * binding.equalizerBassParent.equalizerBassRotate.getMax()).toInt()
        )
        binding.equalizerBassParent.equalizerVirtualRotate.setProgress(
            (settings.virtualizerStrength * binding.equalizerBassParent.equalizerVirtualRotate.getMax()).toInt()
        )

        val labels = EqualizerPresets.frequencies(settings.useTenBand)
        adapter.submit(labels, levels, settings.eqEnabled)

        binding.equalizerText.text = currentPresetName()
        binding.equalizerSave.isSelected = settings.selectedPresetIndex() == 0
        renderEnabledState()
    }

    private fun renderEnabledState() {
        val binding = requireBinding()
        val eqEnabled = settings.eqEnabled
        binding.equalizerEffectLayout.isEnabled = eqEnabled
        binding.equalizerText.isEnabled = eqEnabled
        binding.equalizerTextArrow.isEnabled = eqEnabled
        binding.equalizerEdit.isEnabled = eqEnabled
        binding.equalizerSave.isEnabled = eqEnabled
        binding.equalizerEffectLayout.alpha = if (eqEnabled) 1f else 0.45f

        val bassEnabled = settings.bassEnabled
        binding.equalizerBassParent.equalizerBassRotate.isEnabled = bassEnabled
        binding.equalizerBassParent.equalizerBassText.isEnabled = bassEnabled

        val virtualEnabled = settings.virtualizerEnabled
        binding.equalizerBassParent.equalizerVirtualRotate.isEnabled = virtualEnabled
        binding.equalizerBassParent.equalizerVirtualText.isEnabled = virtualEnabled

    }

    private fun onBandChanged(index: Int, levelMb: Int, fromUser: Boolean) {
        if (!fromUser) return
        val binding = requireBinding()
        if (settings.useTenBand) {
            val updated = settings.customTenBandLevels.copyOf().apply { this[index] = levelMb }
            settings = settings.copy(
                customTenBandLevels = updated,
                selectedPresetIndexTenBand = 0
            )
        } else {
            val updated = settings.customFiveBandLevels.copyOf().apply { this[index] = levelMb }
            settings = settings.copy(
                customFiveBandLevels = updated,
                selectedPresetIndexFiveBand = 0
            )
        }
        binding.equalizerText.text = currentPresetName()
        persistAndApply()
    }

    private fun showPresetPicker() {
        if (!settings.eqEnabled) return

        val names = buildPresetList()
        val selected = settings.selectedPresetIndex().coerceIn(0, names.lastIndex)
        val config = themedSelectableListDialogConfig(names).apply {
            titleText = getString(R.string.equalizer_effect_msg)
            selectedItemIndex = selected
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                if (which == selected) return@OnItemClickListener
                DialogRegistry.dismissAll(requireActivity())
                applyPreset(which)
            }
        }
        OptionsListDialog.show(requireActivity(), config)
    }

    private fun showSavePresetDialog() {
        val editText = createPresetNameInputView(suggestPresetName())
        val config = themedMessageDialogConfig().apply {
            titleText = getString(R.string.save)
            customView = editText
            positiveButtonText = getString(R.string.ok)
            negativeButtonText = getString(R.string.cancel)
            positiveButtonClickListener =
                DialogInterface.OnClickListener { dialog, _ ->
                    val name = editText.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty()) {
                        ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
                        return@OnClickListener
                    }
                    if (isDuplicateUserPreset(name)) {
                        ToastUtil.show(requireContext(), R.string.name_exist)
                        return@OnClickListener
                    }

                    val bands = settings.customLevels().copyOf()
                    userPresets.add(AudioEffectsManager.UserPreset(name, bands))
                    AudioEffectsManager.writeUserPresets(
                        requireContext(),
                        settings.useTenBand,
                        userPresets
                    )

                    val selected = defaultPresetNames.size + userPresets.lastIndex
                    settings = if (settings.useTenBand) {
                        settings.copy(selectedPresetIndexTenBand = selected)
                    } else {
                        settings.copy(selectedPresetIndexFiveBand = selected)
                    }
                    persistAndApply()
                    renderAll()
                    ToastUtil.show(requireContext(), R.string.save_success)
                    dialog.dismiss()
                }
        }
        MessageDialog.show(requireActivity(), config)
    }

    private fun showEditPresetList() {
        if (userPresets.isEmpty()) return
        val names = userPresets.map { it.name }
        val config = themedPlainListDialogConfig(names).apply {
            titleText = getString(R.string.equalizer_edit)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(requireActivity())
                showEditPresetActions(which)
            }
        }
        OptionsListDialog.show(requireActivity(), config)
    }

    private fun showEditPresetActions(index: Int) {
        if (index !in userPresets.indices) return
        val actions = mutableListOf(getString(R.string.rename), getString(R.string.delete))
        val config = themedPlainListDialogConfig(actions).apply {
            titleText = getString(R.string.equalizer_edit)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(requireActivity())
                when (which) {
                    0 -> renameUserPreset(index)
                    1 -> deleteUserPreset(index)
                }
            }
        }
        OptionsListDialog.show(requireActivity(), config)
    }

    private fun renameUserPreset(index: Int) {
        val target = userPresets[index]
        val editText = createPresetNameInputView(target.name)
        val config = themedMessageDialogConfig().apply {
            titleText = getString(R.string.rename)
            customView = editText
            positiveButtonText = getString(R.string.ok)
            negativeButtonText = getString(R.string.cancel)
            positiveButtonClickListener =
                DialogInterface.OnClickListener { dialog, _ ->
                    val value = editText.text?.toString()?.trim().orEmpty()
                    if (value.isEmpty()) {
                        ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
                        return@OnClickListener
                    }
                    if (isDuplicateUserPreset(value, ignoreIndex = index)) {
                        ToastUtil.show(requireContext(), R.string.name_exist)
                        return@OnClickListener
                    }

                    userPresets[index] = target.copy(name = value)
                    AudioEffectsManager.writeUserPresets(
                        requireContext(),
                        settings.useTenBand,
                        userPresets
                    )
                    renderAll()
                    ToastUtil.show(requireContext(), R.string.rename_success)
                    dialog.dismiss()
                }
        }
        MessageDialog.show(requireActivity(), config)
    }

    private fun deleteUserPreset(index: Int) {
        userPresets.removeAt(index)
        AudioEffectsManager.writeUserPresets(requireContext(), settings.useTenBand, userPresets)

        val selected = settings.selectedPresetIndex()
        val firstUserPresetIndex = defaultPresetNames.size
        if (selected >= firstUserPresetIndex) {
            settings = if (settings.useTenBand) {
                settings.copy(selectedPresetIndexTenBand = 0)
            } else {
                settings.copy(selectedPresetIndexFiveBand = 0)
            }
            persistAndApply()
        }
        renderAll()
        ToastUtil.show(requireContext(), R.string.delete_success)
    }

    private fun buildPresetList(): List<String> = buildList {
        addAll(defaultPresetNames)
        addAll(userPresets.map { it.name })
    }

    private fun applyPreset(index: Int) {
        if (index == 0) {
            settings = if (settings.useTenBand) {
                settings.copy(selectedPresetIndexTenBand = 0)
            } else {
                settings.copy(selectedPresetIndexFiveBand = 0)
            }
            persistAndApply()
            renderAll()
            return
        }

        val levels = when {
            index <= defaultPresetNames.lastIndex -> {
                EqualizerPresets.defaultBands(settings.useTenBand)[index].copyOf()
            }

            else -> {
                val userIndex = index - defaultPresetNames.size
                userPresets.getOrNull(userIndex)?.bands?.copyOf() ?: settings.customLevels()
                    .copyOf()
            }
        }

        settings = if (settings.useTenBand) {
            settings.copy(
                customTenBandLevels = levels,
                selectedPresetIndexTenBand = index
            )
        } else {
            settings.copy(
                customFiveBandLevels = levels,
                selectedPresetIndexFiveBand = index
            )
        }

        persistAndApply()
        adapter.refreshAnimations()
        renderAll()
    }

    private fun currentPresetName(): String {
        val names = buildPresetList()
        return names.getOrElse(settings.selectedPresetIndex().coerceAtLeast(0)) {
            defaultPresetNames.firstOrNull().orEmpty()
        }
    }

    private fun suggestPresetName(): String {
        val base = getString(R.string.equalizer_new_effect)
        var index = 1
        val existing = userPresets.map { it.name }.toSet()
        while (true) {
            val candidate = "$base $index"
            if (!existing.contains(candidate)) return candidate
            index++
        }
    }

    private fun isDuplicateUserPreset(name: String, ignoreIndex: Int = -1): Boolean {
        return userPresets.withIndex().any { (idx, preset) ->
            idx != ignoreIndex && preset.name.equals(name, ignoreCase = true)
        }
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

    private fun createPresetNameInputView(initialValue: String): AppCompatEditText {
        val binding = LayoutEdittextBinding.inflate(layoutInflater)
        binding.root.filters = arrayOf(InputFilter.LengthFilter(120))
        binding.root.setText(initialValue)
        binding.root.setSelection(binding.root.text?.length ?: 0)
        return binding.root
    }

    private fun themedMessageDialogConfig(): MessageDialog.Config {
        val palette = requireActivity().appDependencies.themeRepo
            .getCorePalette(requireContext())
        return MessageDialog.Config.create(requireContext()).apply {
            titleTextColor = palette.titleColor
            messageTextColor = palette.messageColor
        }
    }

    private fun themedSelectableListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = requireActivity().appDependencies.themeRepo
            .getCorePalette(requireContext())
        return OptionsListDialog.Config.create(requireContext(), items).apply {
            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.accentColor
            itemIconRes = R.drawable.vector_single_check_selector
            itemIconPlacement = 1
        }
    }

    private fun themedPlainListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = requireActivity().appDependencies.themeRepo
            .getCorePalette(requireContext())
        return OptionsListDialog.Config.create(requireContext(), items).apply {
            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.messageColor
            itemIconRes = 0
            selectedItemIndex = -1
        }
    }
}


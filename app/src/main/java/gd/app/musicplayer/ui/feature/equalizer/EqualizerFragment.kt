package gd.app.musicplayer.ui.feature.equalizer

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import androidx.fragment.app.activityViewModels
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.theme.accentColor
import gd.app.musicplayer.core.theme.messageColor
import gd.app.musicplayer.core.theme.titleColor
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.FragmentEqualizerBinding
import gd.app.musicplayer.databinding.LayoutEdittextBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.core.ui.dialog.OptionsListDialog
import gd.app.musicplayer.core.ui.dialog.DialogRegistry
import gd.app.musicplayer.core.ui.dialog.MessageDialog
import gd.app.musicplayer.core.ui.view.RotateStepBar
import gd.app.musicplayer.core.ui.view.SelectBox
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EqualizerFragment : ViewBindingFragment<FragmentEqualizerBinding>(), View.OnClickListener {

    private val viewModel: EqualizerViewModel by activityViewModels()

    private lateinit var adapter: EqualizerBandAdapter
    private var screenState: EqualizerScreenState = EqualizerScreenState()

    override fun onCreateBinding(inflater: LayoutInflater): FragmentEqualizerBinding =
        FragmentEqualizerBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentEqualizerBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        adapter = EqualizerBandAdapter(
            layoutInflater = layoutInflater,
            onBandChanged = ::onBandChanged,
            onTrackingChanged = { tracking ->
                binding.root.requestDisallowInterceptTouchEvent(tracking)
            }
        )

        binding.equalizerSeekParent.equalizerRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = this@EqualizerFragment.adapter
            itemAnimator = null
            setHasFixedSize(true)
        }

        binding.equalizerText.setOnClickListener(this)
        binding.equalizerTextArrow.setOnClickListener(this)
        binding.equalizerEdit.setOnClickListener(this)
        binding.equalizerSave.setOnClickListener(this)

        binding.equalizerBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(selectBox: SelectBox, fromUser: Boolean, isSelected: Boolean) {
                    if (!fromUser) return
                    viewModel.updateSettings { it.copy(eqEnabled = isSelected) }
                }
            }
        )

        binding.equalizerBassParent.equalizerBassBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(selectBox: SelectBox, fromUser: Boolean, isSelected: Boolean) {
                    if (!fromUser) return
                    viewModel.updateSettings { it.copy(bassEnabled = isSelected) }
                }
            }
        )

        binding.equalizerBassParent.equalizerVirtualBox.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(selectBox: SelectBox, fromUser: Boolean, isSelected: Boolean) {
                    if (!fromUser) return
                    viewModel.updateSettings { it.copy(virtualizerEnabled = isSelected) }
                }
            }
        )

        binding.equalizerBassParent.equalizerBassRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
                override fun onRotationTrackingChanged(view: RotateStepBar, isTracking: Boolean) {
                    binding.root.requestDisallowInterceptTouchEvent(isTracking)
                }

                override fun onRotationChanged(view: RotateStepBar, progress: Int) {
                    viewModel.updateSettings { it.copy(bassStrength = progress / view.getMax().toFloat()) }
                }
            }
        )

        binding.equalizerBassParent.equalizerVirtualRotate.setOnRotateChangedListener(
            object : RotateStepBar.OnRotateChangedListener {
                override fun onRotationTrackingChanged(view: RotateStepBar, isTracking: Boolean) {
                    binding.root.requestDisallowInterceptTouchEvent(isTracking)
                }

                override fun onRotationChanged(view: RotateStepBar, progress: Int) {
                    viewModel.updateSettings { it.copy(virtualizerStrength = progress / view.getMax().toFloat()) }
                }
            }
        )

        observeState()
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
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
        viewModel.refresh()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    screenState = state
                    renderAll()
                    updateContentHeight()
                }
            }
        }
    }

    private fun renderAll() {
        val binding = requireBinding()
        val settings = screenState.settings ?: return
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

        adapter.submit(EqualizerPresets.frequencies(settings.useTenBand), levels, settings.eqEnabled)

        binding.equalizerText.text = currentPresetName()
        binding.equalizerSave.isSelected = settings.selectedPresetIndex().toLong() == 1L
        renderEnabledState(settings.eqEnabled, settings.bassEnabled, settings.virtualizerEnabled)
    }

    private fun renderEnabledState(eqEnabled: Boolean, bassEnabled: Boolean, virtualEnabled: Boolean) {
        val binding = requireBinding()
        binding.equalizerEffectLayout.isEnabled = eqEnabled
        binding.equalizerText.isEnabled = eqEnabled
        binding.equalizerTextArrow.isEnabled = eqEnabled
        binding.equalizerEdit.isEnabled = eqEnabled
        binding.equalizerSave.isEnabled = eqEnabled
        binding.equalizerEffectLayout.alpha = if (eqEnabled) 1f else 0.45f

        binding.equalizerBassParent.equalizerBassRotate.isEnabled = bassEnabled
        binding.equalizerBassParent.equalizerBassText.isEnabled = bassEnabled
        binding.equalizerBassParent.equalizerVirtualRotate.isEnabled = virtualEnabled
        binding.equalizerBassParent.equalizerVirtualText.isEnabled = virtualEnabled
    }

    private fun onBandChanged(index: Int, levelMb: Int, fromUser: Boolean) {
        if (!fromUser) return
        viewModel.onBandChanged(index, levelMb)
    }

    private fun showPresetPicker() {
        val settings = screenState.settings ?: return
        if (!settings.eqEnabled || screenState.presets.isEmpty()) return

        val presets = screenState.presets
        val names = presets.map { it.name }
        val selected = presets.indexOfFirst { it.id == settings.selectedPresetIndex().toLong() }.coerceAtLeast(0)
        val config = themedSelectableListDialogConfig(names).apply {
            titleText = getString(R.string.equalizer_effect_msg)
            selectedItemIndex = selected
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                if (which == selected) return@OnItemClickListener
                DialogRegistry.dismissAll(requireActivity())
                viewModel.applyPreset(presets[which].id)
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
            positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                val name = editText.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
                    return@OnClickListener
                }
                if (hasPresetName(name)) {
                    ToastUtil.show(requireContext(), R.string.name_exist)
                    return@OnClickListener
                }
                val bands = screenState.settings?.customLevels()?.copyOf() ?: return@OnClickListener
                viewModel.createPreset(name, bands) {
                    ToastUtil.show(requireContext(), R.string.save_success)
                }
                dialog.dismiss()
            }
        }
        MessageDialog.show(requireActivity(), config)
    }

    private fun showEditPresetList() {
        val editable = screenState.presets.filter { !it.isDefaultPreset }
        if (editable.isEmpty()) return
        val config = themedPlainListDialogConfig(editable.map { it.name }).apply {
            titleText = getString(R.string.equalizer_edit)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(requireActivity())
                editable.getOrNull(which)?.let(::showEditPresetActions)
            }
        }
        OptionsListDialog.show(requireActivity(), config)
    }

    private fun showEditPresetActions(preset: EqualizerUiPreset) {
        if (preset.isDefaultPreset) return
        val config = themedPlainListDialogConfig(listOf(getString(R.string.rename), getString(R.string.delete))).apply {
            titleText = getString(R.string.equalizer_edit)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(requireActivity())
                when (which) {
                    0 -> renamePreset(preset)
                    1 -> viewModel.deletePreset(preset.id) {
                        ToastUtil.show(requireContext(), R.string.delete_success)
                    }
                }
            }
        }
        OptionsListDialog.show(requireActivity(), config)
    }

    private fun renamePreset(preset: EqualizerUiPreset) {
        if (preset.isDefaultPreset) return
        val editText = createPresetNameInputView(preset.name)
        val config = themedMessageDialogConfig().apply {
            titleText = getString(R.string.rename)
            customView = editText
            positiveButtonText = getString(R.string.ok)
            negativeButtonText = getString(R.string.cancel)
            positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                val value = editText.text?.toString()?.trim().orEmpty()
                if (value.isEmpty()) {
                    ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
                    return@OnClickListener
                }
                if (hasPresetName(value, ignoreId = preset.id)) {
                    ToastUtil.show(requireContext(), R.string.name_exist)
                    return@OnClickListener
                }
                viewModel.renamePreset(preset, value) {
                    ToastUtil.show(requireContext(), R.string.rename_success)
                }
                dialog.dismiss()
            }
        }
        MessageDialog.show(requireActivity(), config)
    }

    private fun currentPresetName(): String {
        val selectedId = screenState.settings?.selectedPresetIndex()?.toLong() ?: return ""
        return screenState.presets.firstOrNull { it.id == selectedId }?.name
            ?: getString(R.string.equalizer_effect_user_defined)
    }

    private fun suggestPresetName(): String {
        val base = getString(R.string.equalizer_new_effect)
        var index = 1
        val existing = screenState.presets.map { it.name.lowercase() }.toSet()
        while (true) {
            val candidate = "$base $index"
            if (!existing.contains(candidate.lowercase())) return candidate
            index++
        }
    }

    private fun hasPresetName(name: String, ignoreId: Long = -1L): Boolean {
        return screenState.presets.any { it.id != ignoreId && it.name.equals(name, ignoreCase = true) }
    }

    private fun updateContentHeight() {
        val binding = requireBinding()
        binding.root.post {
            val rootHeight = binding.root.height
            if (rootHeight <= 0) return@post
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
        val palette = requireContext().appDependencies.themeRepo.getCorePalette(requireContext())
        return MessageDialog.Config.create(requireContext()).apply {
            titleTextColor = palette.titleColor
            messageTextColor = palette.messageColor
            backgroundDrawable = palette.getDialogBackground(requireContext())
        }
    }

    private fun themedSelectableListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = requireContext().appDependencies.themeRepo.getCorePalette(requireContext())
        return OptionsListDialog.Config.create(requireContext(), items).apply {
            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.accentColor
            itemIconRes = R.drawable.vector_single_check_selector
            itemIconPlacement = 1
            backgroundDrawable = palette.getDialogSurfaceDrawable(requireContext())
        }
    }

    private fun themedPlainListDialogConfig(items: List<String>): OptionsListDialog.Config {
        val palette = requireContext().appDependencies.themeRepo.getCorePalette(requireContext())
        return OptionsListDialog.Config.create(requireContext(), items).apply {
            titleTextColor = palette.titleColor
            itemTextColor = palette.messageColor
            selectedItemTextColor = palette.messageColor
            itemIconRes = 0
            selectedItemIndex = -1
            backgroundDrawable = palette.getDialogSurfaceDrawable(requireContext())
        }
    }
}

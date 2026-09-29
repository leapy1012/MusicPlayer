package gd.app.musicplayer.ui.theme

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.dialog.CouiAlertDialogSurface
import gd.app.musicplayer.core.designsystem.view.ColorPickerView
import gd.app.musicplayer.databinding.DialogAccentColorPickerBinding
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import javax.inject.Inject

@AndroidEntryPoint
class SelectAccentColorDialog : DialogFragment(),
    View.OnClickListener,
    PresetColorAdapter.Callback {

    @Inject
    lateinit var themeRepo: ThemeRepo

    private var binding: DialogAccentColorPickerBinding? = null
    private lateinit var presetAdapter: PresetColorAdapter
    private var state = DialogState()
    private var suppressPickerCallback = false
    private var lastPickerColor: Int? = null

    private data class DialogState(
        val initialAccentColor: Int = DEFAULT_ACCENT,
        val selectedAccentColor: Int = DEFAULT_ACCENT,
        val isCustomPageVisible: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreState(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentBinding = DialogAccentColorPickerBinding.inflate(LayoutInflater.from(requireContext()))
        binding = contentBinding

        contentBinding.accentColorFlipper.inAnimation =
            AlphaAnimation(0f, 1f).apply { duration = 150 }
        contentBinding.accentColorFlipper.outAnimation =
            AlphaAnimation(1f, 0f).apply { duration = 150 }

        setupColorPicker(contentBinding)
        setupPresetRecycler(contentBinding)
        setupClickListeners(contentBinding)
        renderState()

        val dialog = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_List
        )
            .setTitle(R.string.accent_color)
            .setView(contentBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            CouiAlertDialogSurface.apply(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                applySelectionAndDismiss()
            }
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INITIAL, state.initialAccentColor)
        outState.putInt(STATE_SELECTED, state.selectedAccentColor)
        outState.putBoolean(STATE_CUSTOM_PAGE, state.isCustomPageVisible)
    }

    override fun onPresetColorSelected(color: Int) {
        val wasCustomPageVisible = state.isCustomPageVisible
        state = state.copy(
            selectedAccentColor = color,
            isCustomPageVisible = false
        )
        syncPickerColorIfNeeded(color)
        if (wasCustomPageVisible) {
            renderState()
        } else {
            renderSelectionOnly()
        }
    }

    override fun onCustomColorRequested() {
        state = state.copy(isCustomPageVisible = true)
        renderState()
    }

    override fun onClick(view: View) {
        val content = binding ?: return
        when (view.id) {
            content.dialogButtonPrevious.id -> {
                state = state.copy(isCustomPageVisible = false)
                renderState()
            }
            else -> selectDefaultAccent()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            state = DialogState(
                initialAccentColor = savedInstanceState.getInt(STATE_INITIAL, DEFAULT_ACCENT),
                selectedAccentColor = savedInstanceState.getInt(STATE_SELECTED, DEFAULT_ACCENT),
                isCustomPageVisible = savedInstanceState.getBoolean(STATE_CUSTOM_PAGE, false)
            )
            return
        }
        val initialColor = arguments?.getInt(ARG_CURRENT) ?: themeRepo.getAccentColor()
        state = DialogState(
            initialAccentColor = initialColor,
            selectedAccentColor = initialColor,
            isCustomPageVisible = false
        )
    }

    private fun setupColorPicker(contentBinding: DialogAccentColorPickerBinding) {
        contentBinding.accentColorPicker.setOnColorChangedListener(object : ColorPickerView.c {
            override fun a(color: Int) {
                if (suppressPickerCallback) return
                state = state.copy(selectedAccentColor = color)
                renderSelectionOnly()
            }
        })
    }

    private fun setupPresetRecycler(contentBinding: DialogAccentColorPickerBinding) {
        presetAdapter = PresetColorAdapter(layoutInflater, PRESET_COLORS, this)
        contentBinding.accentColorRecycler.layoutManager = GridLayoutManager(requireContext(), 4)
        contentBinding.accentColorRecycler.addItemDecoration(
            SpacingItemDecoration.all(requireContext().dpToPx(8f))
        )
        contentBinding.accentColorRecycler.adapter = presetAdapter
        contentBinding.accentColorRecycler.itemAnimator = null
    }

    private fun setupClickListeners(contentBinding: DialogAccentColorPickerBinding) {
        contentBinding.accentColorDefaultSelect.setOnClickListener(this)
        contentBinding.accentColorDefaultText.setOnClickListener(this)
        contentBinding.dialogButtonPrevious.setOnClickListener(this)
    }

    private fun renderState() {
        val content = binding ?: return
        syncPickerColorIfNeeded(state.selectedAccentColor)
        val targetChild = if (state.isCustomPageVisible) 1 else 0
        if (content.accentColorFlipper.displayedChild != targetChild) {
            content.accentColorFlipper.displayedChild = targetChild
        }
        content.dialogButtonPrevious.isVisible = state.isCustomPageVisible
        content.dialogPickerTitle.isVisible = !state.isCustomPageVisible
        renderSelectionOnly()
    }

    private fun renderSelectionOnly() {
        val content = binding ?: return
        val isDefaultSelected = state.selectedAccentColor == DEFAULT_ACCENT
        content.accentColorDefaultSelect.isSelected = isDefaultSelected
        content.accentColorDefaultText.isSelected = isDefaultSelected
        presetAdapter.setSelectedColor(state.selectedAccentColor)
    }

    private fun syncPickerColorIfNeeded(color: Int) {
        val content = binding ?: return
        if (lastPickerColor == color) return
        suppressPickerCallback = true
        try {
            content.accentColorPicker.setColor(color)
            lastPickerColor = color
        } finally {
            suppressPickerCallback = false
        }
    }

    private fun selectDefaultAccent() {
        state = state.copy(
            selectedAccentColor = DEFAULT_ACCENT,
            isCustomPageVisible = false
        )
        renderState()
    }

    private fun applySelectionAndDismiss() {
        themeRepo.updateAccentColor(state.selectedAccentColor)
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(RESULT_COLOR to state.selectedAccentColor)
        )
        dismiss()
    }

    companion object {
        private const val DEFAULT_ACCENT = -12467
        private const val ARG_CURRENT = "arg_current"
        private const val STATE_INITIAL = "state_initial"
        private const val STATE_SELECTED = "state_selected"
        private const val STATE_CUSTOM_PAGE = "state_custom_page"
        const val RESULT_KEY = "accent_color_result"
        const val RESULT_COLOR = "accent_color"
        const val TAG = "AccentColorDialog"

        private val PRESET_COLORS = intArrayOf(
            -12467,
            -694124,
            -8789256,
            -30841,
            -10553112,
            -1815554,
            -7607,
            -15288351,
            -7579649,
            -977344,
            -12918359,
            -29952,
            -12756226,
            -15149988,
            -1998605
        )

        fun newInstance(currentColor: Int): SelectAccentColorDialog {
            return SelectAccentColorDialog().apply {
                arguments = bundleOf(ARG_CURRENT to currentColor)
            }
        }
    }
}

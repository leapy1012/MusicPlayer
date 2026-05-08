package gd.app.musicplayer.ui.theme

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.databinding.DialogAccentColorPickerBinding
import gd.app.musicplayer.core.ui.dialog.BaseDialogFragment
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.core.extension.screenHeight
import gd.app.musicplayer.core.extension.screenWidth
import gd.app.musicplayer.core.ui.view.ColorPickerView
import gd.app.musicplayer.data.repository.ThemeRepo
import javax.inject.Inject

@AndroidEntryPoint
class SelectAccentColorDialog : BaseDialogFragment(),
    View.OnClickListener,
    PresetColorAdapter.Callback {

    @Inject
    lateinit var themeRepo: ThemeRepo

    private var _binding: DialogAccentColorPickerBinding? = null
    private val binding: DialogAccentColorPickerBinding
        get() = _binding!!

    private lateinit var presetAdapter: PresetColorAdapter
    private var state = DialogState()
    private var suppressPickerCallback = false
    private var lastPickerColor: Int? = null

    private data class DialogState(
        val initialAccentColor: Int = DEFAULT_ACCENT,
        val selectedAccentColor: Int = DEFAULT_ACCENT,
        val isCustomPageVisible: Boolean = false
    )

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
                arguments = Bundle().apply {
                    putInt(ARG_CURRENT, currentColor)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAccentColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreState(savedInstanceState)
        setupDialogAppearance()
        setupColorPicker()
        setupPresetRecycler()
        setupClickListeners()
        renderState()
        syncPickerHeight()
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(widthRatio = 0.9f)
        dialog?.setCanceledOnTouchOutside(true)
        syncPickerHeight()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncPickerHeight()
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
        when (view.id) {
            binding.dialogButtonOk.id -> applySelectionAndDismiss()
            binding.dialogButtonCancel.id -> dismiss()
            binding.dialogButtonPrevious.id -> {
                state = state.copy(isCustomPageVisible = false)
                renderState()
            }

            else -> selectDefaultAccent()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            state = DialogState(
                initialAccentColor = savedInstanceState.getInt(
                    STATE_INITIAL,
                    DEFAULT_ACCENT
                ),
                selectedAccentColor = savedInstanceState.getInt(
                    STATE_SELECTED,
                    DEFAULT_ACCENT
                ),
                isCustomPageVisible = savedInstanceState.getBoolean(STATE_CUSTOM_PAGE, false)
            )
            return
        }

        val initialColor =
            arguments?.getInt(ARG_CURRENT) ?: themeRepo.getAccentColor()
        state = DialogState(
            initialAccentColor = initialColor,
            selectedAccentColor = initialColor,
            isCustomPageVisible = false
        )
    }

    private fun setupDialogAppearance() {
        applyDialogBackgroundForPicker()
        binding.accentColorFlipper.inAnimation = AlphaAnimation(0f, 1f).apply { duration = 150 }
        binding.accentColorFlipper.outAnimation = AlphaAnimation(1f, 0f).apply { duration = 150 }
        applyTagStyles(binding.root, state.selectedAccentColor)
    }

    private fun setupColorPicker() {
        binding.accentColorPicker.setOnColorChangedListener(object : ColorPickerView.c {
            override fun a(color: Int) {
                if (suppressPickerCallback) return
                state = state.copy(selectedAccentColor = color)
                renderSelectionOnly()
            }
        })
    }

    private fun setupPresetRecycler() {
        presetAdapter = PresetColorAdapter(layoutInflater, PRESET_COLORS, this)
        binding.accentColorRecycler.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.accentColorRecycler.addItemDecoration(
            SpacingItemDecoration.all(requireContext().dpToPx(8f))
        )
        binding.accentColorRecycler.adapter = presetAdapter
        binding.accentColorRecycler.itemAnimator = null
    }

    private fun setupClickListeners() {
        binding.accentColorDefaultSelect.setOnClickListener(this)
        binding.accentColorDefaultText.setOnClickListener(this)
        binding.dialogButtonPrevious.setOnClickListener(this)
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)
    }

    private fun renderState() {
        syncPickerColorIfNeeded(state.selectedAccentColor)
        val targetChild = if (state.isCustomPageVisible) 1 else 0
        if (binding.accentColorFlipper.displayedChild != targetChild) {
            binding.accentColorFlipper.displayedChild = targetChild
        }
        binding.dialogButtonPrevious.visibility =
            if (state.isCustomPageVisible) View.VISIBLE else View.GONE
        binding.dialogPickerTitle.visibility =
            if (state.isCustomPageVisible) View.GONE else View.VISIBLE
        renderSelectionOnly()
    }

    private fun renderSelectionOnly() {
        val isDefaultSelected = state.selectedAccentColor == DEFAULT_ACCENT
        binding.accentColorDefaultSelect.isSelected = isDefaultSelected
        binding.accentColorDefaultText.isSelected = isDefaultSelected
        presetAdapter.setSelectedColor(state.selectedAccentColor)
    }

    private fun syncPickerColorIfNeeded(color: Int) {
        if (lastPickerColor == color) return
        suppressPickerCallback = true
        try {
            binding.accentColorPicker.setColor(color)
            lastPickerColor = color
        } finally {
            suppressPickerCallback = false
        }
    }

    private fun applyDialogBackgroundForPicker() {
        applyDialogBackground(
            rootView = binding.root,
            reqWidth = maxOf(320, requireContext().screenWidth / 2),
            reqHeight = maxOf(320, requireContext().screenHeight / 2)
        )
    }

    private fun selectDefaultAccent() {
        state = state.copy(
            selectedAccentColor = DEFAULT_ACCENT,
            isCustomPageVisible = false
        )
        renderState()
    }

    private fun applySelectionAndDismiss() {
        themeRepo.updateAccentColor(
            state.selectedAccentColor
        )
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply { putInt(RESULT_COLOR, state.selectedAccentColor) }
        )
        dismiss()
    }

    private fun syncPickerHeight() {
        binding.accentColorPicker.post {
            val h = binding.accentColorFlipper.height
            if (h > 0) {
                binding.accentColorPicker.layoutParams =
                    binding.accentColorPicker.layoutParams.apply {
                        height = h
                    }
            }
        }
    }
}

package gd.app.musicplayer.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Toast
import androidx.core.os.bundleOf
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.databinding.DialogPlaylistLimitBinding

class SmartPlaylistLimitDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogPlaylistLimitBinding? = null
    private val binding: DialogPlaylistLimitBinding
        get() = requireNotNull(_binding)

    private var selectedIndex: Int = DEFAULT_SELECTION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedIndex = savedInstanceState?.getInt(STATE_SELECTED_INDEX)
            ?: requireArguments().getInt(ARG_SELECTED_INDEX, DEFAULT_SELECTION).coerceIn(0, 7)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPlaylistLimitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(binding.root)
        applyDialogWidth(0.9f)

        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)

        repeat(binding.playlistLimitContainer.childCount) { index ->
            val row = binding.playlistLimitContainer.getChildAt(index) as ViewGroup
            row.setOnClickListener(this)
        }

        binding.playlistLimitEdittext.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectIndex(CUSTOM_SELECTION)
        }

        val customLimit = requireArguments().getInt(ARG_CUSTOM_LIMIT, -1)
        if (customLimit > 0) {
            binding.playlistLimitEdittext.setText(customLimit.toString())
        }

        selectIndex(selectedIndex)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_INDEX, selectedIndex)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.dialog_button_cancel -> dismiss()
            R.id.dialog_button_ok -> saveSelection()
            else -> {
                val index = binding.playlistLimitContainer.indexOfChild(view)
                if (index >= 0) selectIndex(index)
            }
        }
    }

    override fun onDestroyView() {
        hideKeyboard()
        _binding = null
        super.onDestroyView()
    }

    private fun selectIndex(index: Int) {
        selectedIndex = index.coerceIn(0, CUSTOM_SELECTION)
        repeat(binding.playlistLimitContainer.childCount) { childIndex ->
            val row = binding.playlistLimitContainer.getChildAt(childIndex) as ViewGroup
            row.getChildAt(0).isSelected = childIndex == selectedIndex
        }
    }

    private fun saveSelection() {
        val customLimit = if (selectedIndex == CUSTOM_SELECTION) {
            binding.playlistLimitEdittext.text?.toString()?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: run {
                    ToastUtil.show(requireContext(), Toast.LENGTH_SHORT, getString(R.string.input_error))
                    return
                }
        } else {
            -1
        }

        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                KEY_SELECTION_INDEX to selectedIndex,
                KEY_CUSTOM_LIMIT to customLimit
            )
        )
        dismiss()
    }

    private fun hideKeyboard() {
        val context = context ?: return
        val input = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        input?.hideSoftInputFromWindow(binding.playlistLimitEdittext.windowToken, 0)
    }

    companion object {
        const val RESULT_KEY = "smart_playlist_limit_result"
        const val KEY_SELECTION_INDEX = "selection_index"
        const val KEY_CUSTOM_LIMIT = "custom_limit"
        private const val ARG_SELECTED_INDEX = "arg_selected_index"
        private const val ARG_CUSTOM_LIMIT = "arg_custom_limit"
        private const val STATE_SELECTED_INDEX = "state_selected_index"
        private const val DEFAULT_SELECTION = 4
        private const val CUSTOM_SELECTION = 7

        fun newInstance(
            selectedIndex: Int,
            customLimit: Int
        ): SmartPlaylistLimitDialogFragment {
            return SmartPlaylistLimitDialogFragment().apply {
                arguments = bundleOf(
                    ARG_SELECTED_INDEX to selectedIndex,
                    ARG_CUSTOM_LIMIT to customLimit
                )
            }
        }
    }
}

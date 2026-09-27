package gd.app.musicplayer.feature.setting

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ListView
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.coui.appcompat.dialog.adapter.ChoiceListAdapter
import com.coui.appcompat.edittext.COUIEditText
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil

/**
 * COUI single-choice list ([ChoiceListAdapter] / [coui_select_dialog_singlechoice]) —
 * same path as ColorOS list dialogs. Not PreferenceFragment/COUIMarkPreference rows
 * (those use card press feedback and look wrong inside an alert).
 */
class SmartPlaylistLimitDialogFragment : DialogFragment() {

    private var selectedIndex: Int = DEFAULT_SELECTION
    private var customEditText: COUIEditText? = null
    private var listView: ListView? = null
    private var entries: Array<CharSequence> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedIndex = savedInstanceState?.getInt(STATE_SELECTED_INDEX)
            ?: requireArguments().getInt(ARG_SELECTED_INDEX, DEFAULT_SELECTION)
                .coerceIn(0, CUSTOM_SELECTION)
        entries = resources.getTextArray(R.array.settings_playlist_limit_entries)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_playlist_limit_coui, null, false)
        listView = content.findViewById(R.id.playlist_limit_list)
        customEditText = content.findViewById(R.id.playlist_limit_edittext)

        val customLimit = requireArguments().getInt(ARG_CUSTOM_LIMIT, -1)
        if (customLimit > 0) {
            customEditText?.setText(customLimit.toString())
        }
        customEditText?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectIndex(CUSTOM_SELECTION)
        }

        bindChoiceList()
        updateCustomEditVisibility()

        val dialog = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_List
        )
            .setTitle(R.string.playlist_track_limit)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                saveSelection()
            }
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_INDEX, selectedIndex)
    }

    override fun onDismiss(dialog: DialogInterface) {
        hideKeyboard()
        super.onDismiss(dialog)
    }

    private fun bindChoiceList() {
        val list = listView ?: return
        list.adapter = createAdapter()
        list.setOnItemClickListener { _, _, position, _ ->
            selectIndex(position)
        }
    }

    private fun createAdapter(): ChoiceListAdapter {
        val checked = BooleanArray(OPTION_COUNT) { it == selectedIndex }
        return object : ChoiceListAdapter(
            requireContext(),
            com.coui.appcompat.R.layout.coui_select_dialog_singlechoice,
            entries,
            null,
            checked,
            false
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.findViewById<RadioButton>(com.coui.appcompat.R.id.radio_button)?.apply {
                    buttonDrawable = ContextCompat.getDrawable(
                        context,
                        com.coui.appcompat.R.drawable.coui_btn_check_mark
                    )
                }
                val divider = view.findViewById<View>(com.coui.appcompat.R.id.item_divider)
                divider?.visibility =
                    if (count == 1 || position == count - 1) View.GONE else View.VISIBLE
                return view
            }
        }
    }

    private fun selectIndex(index: Int) {
        selectedIndex = index.coerceIn(0, CUSTOM_SELECTION)
        listView?.adapter = createAdapter()
        updateCustomEditVisibility()
        if (selectedIndex == CUSTOM_SELECTION) {
            customEditText?.requestFocus()
        } else {
            hideKeyboard()
        }
    }

    private fun updateCustomEditVisibility() {
        val showCustom = selectedIndex == CUSTOM_SELECTION
        customEditText?.isVisible = showCustom
        customEditText?.isEnabled = showCustom
    }

    private fun saveSelection() {
        val customLimit = if (selectedIndex == CUSTOM_SELECTION) {
            customEditText?.text?.toString()?.toIntOrNull()?.takeIf { it > 0 }
                ?: run {
                    ToastUtil.show(
                        requireContext(),
                        Toast.LENGTH_SHORT,
                        getString(R.string.input_error)
                    )
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
        val edit = customEditText ?: return
        val input = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        input?.hideSoftInputFromWindow(edit.windowToken, 0)
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
        private const val OPTION_COUNT = 8

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

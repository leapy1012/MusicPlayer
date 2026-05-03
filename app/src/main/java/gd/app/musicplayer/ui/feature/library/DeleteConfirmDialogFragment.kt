package gd.app.musicplayer.ui.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogCommonBinding
import gd.app.musicplayer.core.ui.dialog.BaseDialogFragment

class DeleteConfirmDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogCommonBinding? = null
    private val binding: DialogCommonBinding
        get() = requireNotNull(_binding)

    private val dialogType: Int
        get() = requireArguments().getInt(ARG_DIALOG_TYPE)
    private val itemName: String
        get() = requireArguments().getString(ARG_ITEM_NAME).orEmpty()

    private val spec: Spec
        get() = when (dialogType) {
            TYPE_TRACK_DELETE -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.delete_file_tip, itemName),
                confirmTextRes = R.string.delete,
                showExtra = true,
                extraCheckedDefault = true
            )

            TYPE_SET_DELETE_PLAYLIST -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.delete_playlist_x, itemName),
                confirmTextRes = R.string.delete,
                showExtra = false,
                extraCheckedDefault = true
            )

            else -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.delete_file_tip, itemName),
                confirmTextRes = R.string.delete,
                showExtra = true,
                extraCheckedDefault = true
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(binding.root)

        binding.dialogTitle.setText(spec.titleRes)
        binding.dialogMessage.text = spec.message
        binding.dialogButtonOk.setText(spec.confirmTextRes)
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)

        binding.dialogCommenExtraLayout.visibility = if (spec.showExtra) View.VISIBLE else View.GONE
        binding.dialogCommenDeleteSelect.isSelected = spec.extraCheckedDefault
        binding.dialogCommenExtraLayout.setOnClickListener(this)
        binding.dialogCommenDeleteSelect.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(0.9f)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.dialog_commen_extra_layout,
            R.id.dialog_commen_delete_select -> {
                binding.dialogCommenDeleteSelect.isSelected = !binding.dialogCommenDeleteSelect.isSelected
            }

            R.id.dialog_button_ok -> {
                setFragmentResult(
                    requireArguments().getString(ARG_RESULT_KEY).orEmpty(),
                    bundleOf(RESULT_CONFIRMED to true, RESULT_EXTRA_CHECKED to binding.dialogCommenDeleteSelect.isSelected)
                )
                dismiss()
            }

            R.id.dialog_button_cancel -> dismiss()
        }
    }

    companion object {
        const val RESULT_CONFIRMED = "confirmed"
        const val RESULT_EXTRA_CHECKED = "extra_checked"

        private const val ARG_ITEM_NAME = "item_name"
        private const val ARG_DIALOG_TYPE = "dialog_type"
        private const val ARG_RESULT_KEY = "result_key"

        private const val TYPE_TRACK_DELETE = 1
        private const val TYPE_SET_DELETE_TRACKS = 2
        private const val TYPE_SET_DELETE_PLAYLIST = 3

        private data class Spec(
            val titleRes: Int,
            val message: String,
            val confirmTextRes: Int,
            val showExtra: Boolean,
            val extraCheckedDefault: Boolean
        )

        private fun create(
            resultKey: String,
            itemName: String,
            type: Int
        ): DeleteConfirmDialogFragment {
            return DeleteConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_RESULT_KEY to resultKey,
                    ARG_ITEM_NAME to itemName,
                    ARG_DIALOG_TYPE to type
                )
            }
        }

        fun forTrackDelete(resultKey: String, trackTitle: String): DeleteConfirmDialogFragment {
            return create(resultKey = resultKey, itemName = trackTitle, type = TYPE_TRACK_DELETE)
        }

        fun forSetDelete(
            resultKey: String,
            setName: String,
            isPlaylist: Boolean
        ): DeleteConfirmDialogFragment {
            return create(
                resultKey = resultKey,
                itemName = setName,
                type = if (isPlaylist) TYPE_SET_DELETE_PLAYLIST else TYPE_SET_DELETE_TRACKS
            )
        }
    }
}

package gd.app.musicplayer.feature.library.tracks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.databinding.DialogCommonBinding

class ClearMusicSetConfirmDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogCommonBinding? = null
    private val binding: DialogCommonBinding
        get() = requireNotNull(_binding)

    private val titleRes: Int
        get() = requireArguments().getInt(ARG_TITLE_RES)

    private val messageText: String
        get() = requireArguments().getString(ARG_MESSAGE_TEXT).orEmpty()

    private val resultKey: String
        get() = requireArguments().getString(ARG_RESULT_KEY).orEmpty()

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

        binding.dialogTitle.setText(titleRes)
        binding.dialogMessage.text = messageText
        binding.dialogButtonOk.setText(R.string.clear)
        binding.dialogCommenExtraLayout.visibility = View.GONE
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(0.9f)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.dialog_button_ok -> {
                setFragmentResult(
                    resultKey,
                    bundleOf(RESULT_CONFIRMED to true)
                )
                dismiss()
            }

            R.id.dialog_button_cancel -> dismiss()
        }
    }

    companion object {
        const val RESULT_CONFIRMED = "result_confirmed"

        private const val ARG_TITLE_RES = "arg_title_res"
        private const val ARG_MESSAGE_TEXT = "arg_message_text"
        private const val ARG_RESULT_KEY = "arg_result_key"

        fun newInstance(
            titleRes: Int,
            messageText: String,
            resultKey: String
        ): ClearMusicSetConfirmDialogFragment {
            return ClearMusicSetConfirmDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_RES, titleRes)
                    putString(ARG_MESSAGE_TEXT, messageText)
                    putString(ARG_RESULT_KEY, resultKey)
                }
            }
        }
    }
}

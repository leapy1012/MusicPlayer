package gd.app.musicplayer.ui.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.databinding.DialogCommonBinding

@AndroidEntryPoint
class QueueClearConfirmDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogCommonBinding? = null
    private val binding: DialogCommonBinding
        get() = requireNotNull(_binding)

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

        binding.dialogTitle.setText(R.string.clear)
        binding.dialogMessage.setText(R.string.clear_message)
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
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf(RESULT_CONFIRMED to true)
                )
                dismiss()
            }

            R.id.dialog_button_cancel -> dismiss()
        }
    }

    companion object {
        const val TAG = "QueueClearConfirmDialogFragment"
        const val RESULT_KEY = "queue_clear_confirm_result"
        const val RESULT_CONFIRMED = "result_confirmed"
    }
}

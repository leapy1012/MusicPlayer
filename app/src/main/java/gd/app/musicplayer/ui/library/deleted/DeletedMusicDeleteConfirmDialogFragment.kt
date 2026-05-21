package gd.app.musicplayer.ui.library.deleted

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.databinding.DialogCommonBinding

class DeletedMusicDeleteConfirmDialogFragment : BaseDialogFragment(), View.OnClickListener {

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

        binding.dialogTitle.setText(R.string.delete)
        binding.dialogMessage.setText(R.string.delete_musics)
        binding.dialogButtonOk.setText(R.string.delete)
        binding.dialogCommenExtraLayout.visibility = View.GONE
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(0.9f)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.dialog_button_ok -> {
                setFragmentResult(RESULT_KEY, bundleOf(RESULT_CONFIRMED to true))
                dismiss()
            }

            R.id.dialog_button_cancel -> dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val RESULT_KEY = "deleted_music_delete_confirm_result"
        const val RESULT_CONFIRMED = "confirmed"
    }
}

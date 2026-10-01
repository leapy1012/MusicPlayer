package gd.app.musicplayer.feature.library.tracks

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.CouiConfirmDialogFragment

@AndroidEntryPoint
class ClearMusicSetConfirmDialogFragment : CouiConfirmDialogFragment() {

    private val titleRes: Int
        get() = requireArguments().getInt(ARG_TITLE_RES)

    private val messageText: String
        get() = requireArguments().getString(ARG_MESSAGE_TEXT).orEmpty()

    private val resultKey: String
        get() = requireArguments().getString(ARG_RESULT_KEY).orEmpty()

    override fun provideTitle(): CharSequence = getString(titleRes)
    override fun provideMessage(): CharSequence = messageText
    override fun providePositiveText(): CharSequence = getString(R.string.clear)

    override fun onPositiveClicked(extraChecked: Boolean) {
        setFragmentResult(resultKey, bundleOf(RESULT_CONFIRMED to true))
        dismiss()
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

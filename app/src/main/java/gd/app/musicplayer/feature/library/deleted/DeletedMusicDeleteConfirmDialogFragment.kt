package gd.app.musicplayer.feature.library.deleted

import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.CouiConfirmDialogFragment

@AndroidEntryPoint
class DeletedMusicDeleteConfirmDialogFragment : CouiConfirmDialogFragment() {

    override fun provideTitle(): CharSequence = getString(R.string.delete)
    override fun provideMessage(): CharSequence = getString(R.string.delete_musics)
    override fun providePositiveText(): CharSequence = getString(R.string.delete)

    override fun onPositiveClicked(extraChecked: Boolean) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_CONFIRMED to true))
        dismiss()
    }

    companion object {
        const val RESULT_KEY = "deleted_music_delete_confirm_result"
        const val RESULT_CONFIRMED = "confirmed"
    }
}

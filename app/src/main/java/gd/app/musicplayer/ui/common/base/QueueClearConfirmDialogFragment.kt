package gd.app.musicplayer.ui.common.base

import android.os.Bundle
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.CouiConfirmDialogFragment
import gd.app.musicplayer.domain.usecase.playback.ClearQueueUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QueueClearConfirmDialogFragment : CouiConfirmDialogFragment() {

    @Inject
    lateinit var clearQueueUseCase: ClearQueueUseCase

    override fun provideTitle(): CharSequence = getString(R.string.clear)
    override fun provideMessage(): CharSequence = getString(R.string.clear_message)
    override fun providePositiveText(): CharSequence = getString(R.string.clear)

    override fun onPositiveClicked(extraChecked: Boolean) {
        clearQueueUseCase()
        dismiss()
    }

    companion object {
        const val TAG = "QueueClearConfirmDialogFragment"
    }
}

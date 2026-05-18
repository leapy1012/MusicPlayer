package gd.app.musicplayer.ui.library.options

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.selection.MusicShareSupport
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import gd.app.musicplayer.ui.library.albums.AlbumMusicActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QueueTrackOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var music: Music
    private val viewModel: QueueTrackOptionsViewModel by viewModels()

    @Inject
    lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    override fun onReadArguments(arguments: Bundle) {
        music = arguments.parcelable(ARG_MUSIC) ?: error("Missing music")
    }

    override fun provideMenuItems(): List<MenuItem> = listOf(
        MenuItem.create(R.string.operation_play, R.drawable.ic_menu_play),
        MenuItem.create(R.string.add_to, R.drawable.ic_menu_add),
        MenuItem.create(R.string.dlg_more_view_album, R.drawable.ic_more_album),
        MenuItem.create(R.string.dlg_more_view_artist, R.drawable.ic_more_artist),
        MenuItem.create(R.string.remove, R.drawable.ic_menu_remove),
        MenuItem.create(R.string.dlg_ringtone_2, R.drawable.ic_menu_ringtone),
        MenuItem.create(R.string.dlg_share_music, R.drawable.ic_menu_share),
        MenuItem.create(R.string.delete, R.drawable.ic_menu_delete)
    )

    override fun onMenuItemClicked(item: MenuItem) {
        when (item.id) {
            R.string.operation_play -> viewModel.onPlay(music)
            R.string.add_to -> viewModel.onAddToPlaylist(music)
            R.string.dlg_more_view_album -> viewModel.onOpenAlbum(music)
            R.string.dlg_more_view_artist -> viewModel.onOpenArtist(music)
            R.string.remove -> viewModel.onRemoveFromQueue(music)
            R.string.dlg_ringtone_2 -> {
                dismissAllowingStateLoss()
                RingtoneActionHandler.handle(
                    requireActivity(),
                    music,
                    materialDialogConfigFactory
                )
            }
            R.string.dlg_share_music -> viewModel.onShare(music)
            R.string.delete -> confirmDeleteTrack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onBindTitleArea(
        container: View,
        titleView: TextView,
        titleIconView: ImageView
    ) {
        titleView.text = music.title
        titleIconView.setImageResource(R.drawable.ic_menu_song_detail)
        titleIconView.visibility = View.VISIBLE
        val detailClick = View.OnClickListener {
            dismiss()
            MusicDetailDialogFragment.newInstance(music)
                .show(parentFragmentManager, MusicDetailDialogFragment::class.java.simpleName)
        }
        titleView.setOnClickListener(detailClick)
        titleIconView.setOnClickListener(detailClick)
    }

    private fun confirmDeleteTrack() {
        requireActivity().showMessageDialog(
            requireContext().createMessageDialogConfig(
                title = getString(R.string.delete),
                message = music.title,
                negativeText = getString(android.R.string.cancel),
                positiveText = getString(R.string.delete),
                positiveClickListener = { _, _ ->
                    viewModel.onDeleteConfirmed(music)
                }
            )
        )
    }

    private fun handleEvent(event: QueueTrackOptionsEvent) {
        when (event) {
            QueueTrackOptionsEvent.Dismiss -> dismissAllowingStateLoss()
            is QueueTrackOptionsEvent.OpenAddTo -> PlaylistSelectActivity.start(requireContext(), event.tracks)
            is QueueTrackOptionsEvent.OpenAlbum -> AlbumMusicActivity.start(requireContext(), event.album)
            is QueueTrackOptionsEvent.OpenArtist -> AlbumMusicActivity.start(requireContext(), event.artist)
            is QueueTrackOptionsEvent.Share -> MusicShareSupport.share(requireContext(), event.tracks)
            is QueueTrackOptionsEvent.ShowToast -> ToastUtil.show(requireContext(), event.messageRes)
        }
    }

    companion object {
        private const val ARG_MUSIC = "music"

        fun newInstance(music: Music): QueueTrackOptionsDialog {
            return QueueTrackOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC, music)
                }
            }
        }
    }
}

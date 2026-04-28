package gd.app.musicplayer.ui.feature.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.selection.MusicShareSupport
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import kotlinx.coroutines.launch

class QueueTrackOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var music: Music
    private val deleteTracks by lazy { requireContext().appDependencies.deleteTracksUseCase }

    override fun onReadArguments(arguments: Bundle) {
        music = arguments.parcelable(ARG_MUSIC) ?: error("Missing music")
    }

    override fun provideMenuItems(): List<MenuItem> = listOf(
        MenuItem.create(R.string.operation_play, R.drawable.ic_menu_play),
        MenuItem.create(R.string.add_to, R.drawable.ic_menu_add),
        MenuItem.create(R.string.dlg_more_view_album, R.drawable.main_album_simple),
        MenuItem.create(R.string.dlg_more_view_artist, R.drawable.main_artist_simple),
        MenuItem.create(R.string.remove, R.drawable.ic_menu_remove),
        MenuItem.create(R.string.dlg_ringtone_2, R.drawable.ic_menu_ringtone),
        MenuItem.create(R.string.dlg_share_music, R.drawable.ic_menu_share),
        MenuItem.create(R.string.delete, R.drawable.ic_menu_delete)
    )

    override fun onMenuItemClicked(item: MenuItem) {
        dismiss()
        when (item.id) {
            R.string.operation_play -> MusicPlaybackController.playQueue(requireContext(), listOf(music), 0)
            R.string.add_to -> ActivityPlaylistSelect.start(requireContext(), listOf(music))
            R.string.dlg_more_view_album -> openAlbum()
            R.string.dlg_more_view_artist -> openArtist()
            R.string.remove -> removeFromQueue()
            R.string.dlg_ringtone_2 -> ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            R.string.dlg_share_music -> MusicShareSupport.share(requireContext(), listOf(music))
            R.string.delete -> confirmDeleteTrack()
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

    private fun openAlbum() {
        val albumName = music.album.takeIf { it.isNotBlank() } ?: return
        AlbumMusicActivity.start(
            requireContext(),
            MusicSet.Album(
                id = music.albumId.toLongOrNull() ?: MusicSet.ALBUMS_ID,
                name = albumName,
                albumArt = music.albumPicture,
                artist = music.artist,
                musicCount = 0,
                date = music.date ?: 0L
            )
        )
    }

    private fun openArtist() {
        val artistName = music.artist.takeIf { it.isNotBlank() } ?: return
        AlbumMusicActivity.start(
            requireContext(),
            MusicSet.Artist(
                id = MusicSet.ARTISTS_ID,
                name = artistName,
                musicCount = 0,
                albumCount = 0,
                albumArt = music.albumPicture
            )
        )
    }

    private fun removeFromQueue() {
        val state = MusicPlaybackController.state.value
        val index = state.queue.indexOfFirst { it.id == music.id }
        if (index < 0) return

        val newQueue = state.queue.toMutableList().apply { removeAt(index) }
        if (newQueue.isEmpty()) {
            MusicPlaybackController.clearQueue(requireContext())
            ToastUtil.show(requireContext(), R.string.succeed)
            return
        }

        val newIndex = when {
            index < state.currentIndex -> state.currentIndex - 1
            state.currentIndex >= newQueue.size -> newQueue.lastIndex
            else -> state.currentIndex
        }
        MusicPlaybackController.replaceQueue(requireContext(), newQueue, newIndex)
        ToastUtil.show(requireContext(), R.string.succeed)
    }

    private fun confirmDeleteTrack() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(music.title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val deletedCount = deleteTracks(listOf(music))
                    ToastUtil.show(
                        requireContext(),
                        if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                    )
                }
            }
            .show()
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

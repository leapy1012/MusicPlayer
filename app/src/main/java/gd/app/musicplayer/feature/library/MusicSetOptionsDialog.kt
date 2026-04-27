package gd.app.musicplayer.feature.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import gd.app.musicplayer.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.feature.selection.MusicShareSupport
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.ui.extension.parcelable
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.ui.feature.shortcut.AppShortcutManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MusicSetOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var musicSet: MusicSet
    private val playTracks by lazy { requireContext().appContainer.playTracksUseCase }
    private val playNextTracks by lazy { requireContext().appContainer.playNextTracksUseCase }
    private val enqueueTracks by lazy { requireContext().appContainer.enqueueTracksUseCase }
    private val deleteTracks by lazy { requireContext().appContainer.deleteTracksUseCase }
    private val hiddenRepo by lazy { requireContext().appContainer.hiddenRepo }
    private val deletePlaylist by lazy { requireContext().appContainer.deletePlaylistUseCase }
    private val libraryRepo by lazy { requireContext().appContainer.libraryRepo }
    private val preferenceUtil by lazy { requireContext().appContainer.preferenceUtil }

    override fun onReadArguments(arguments: Bundle) {
        musicSet = arguments.parcelable(ARG_MUSIC_SET) ?: error("Missing music set")
    }

    override fun provideMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        val setType = musicSet.id.toInt()

        items += MenuItem.create(R.string.operation_play, R.drawable.ic_menu_play)
        items += MenuItem.create(R.string.play_next_2, R.drawable.ic_menu_next_play)
        items += MenuItem.create(R.string.operation_enqueue, R.drawable.ic_menu_enqueue)

        if (musicSet is MusicSet.Playlist) {
            items += MenuItem.create(R.string.list_rename, R.drawable.ic_menu_rename)
        }

        if (musicSet is MusicSet.Album || musicSet is MusicSet.Folder || musicSet is MusicSet.Artist || musicSet is MusicSet.Genre ||
            musicSet is MusicSet.RecentlyPlayed
        ) {
            items += MenuItem.create(R.string.add_to, R.drawable.ic_menu_add)
        }

        if (musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet is MusicSet.Playlist || musicSet is MusicSet.Folder || musicSet is MusicSet.Genre) {
            items += MenuItem.create(R.string.dlg_manage_artwork, R.drawable.ic_menu_artwork)
        }

        if (musicSet is MusicSet.Album || musicSet is MusicSet.RecentlyAdded || musicSet is MusicSet.RecentlyPlayed
            || musicSet is MusicSet.MostPlayed || musicSet is MusicSet.Folder || musicSet is MusicSet.Artist || musicSet is MusicSet.Genre
            || musicSet is MusicSet.Playlist
        ) {
            if (AppShortcutManager.isPinShortcutSupported(requireContext())) {
                items += MenuItem.create(R.string.home_screen, R.drawable.ic_menu_home)
            } else if (musicSet is MusicSet.Folder) {
                items += MenuItem.create(R.string.share, R.drawable.ic_menu_share)
            } else if (musicSet is MusicSet.Album || musicSet is MusicSet.Artist || musicSet is MusicSet.Genre) {
                items += MenuItem.create(R.string.rename, R.drawable.ic_menu_rename)

            } else if (musicSet is MusicSet.Playlist) {
                items += MenuItem.create(R.string.add_to, R.drawable.ic_menu_add)
            }
        }

        if (musicSet is MusicSet.Folder) {
            items += MenuItem.create(R.string.dlg_hide_folder, R.drawable.ic_menu_hide_folder)
        }

        if (musicSet is MusicSet.Album || musicSet is MusicSet.Artist || musicSet is MusicSet.Genre
            || musicSet is MusicSet.Playlist || musicSet is MusicSet.Favorites
        ) {
            items += MenuItem.create(R.string.share, R.drawable.ic_menu_share)
        }

        if (musicSet is MusicSet.Playlist) {
            items += MenuItem.create(R.string.list_delete, R.drawable.ic_menu_delete)
        }

        if (musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet is MusicSet.Folder || musicSet is MusicSet.Genre) {
            items += MenuItem.create(R.string.delete, R.drawable.ic_menu_delete)
        }

        return items
    }

    override fun onMenuItemClicked(item: MenuItem) {
        dismiss()

        when (item.id) {
            R.string.rename,
            R.string.list_rename -> showRenameDialog()

            R.string.dlg_manage_artwork -> {
                ManageArtworkDialogFragment.newInstance(ArtworkRequest.MusicSetTarget(musicSet))
                    .show(parentFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
            }

            R.string.home_screen -> {
//                MusicSetShortcutHelper.pinShortcut(requireContext(), musicSet)
            }

            R.string.dlg_hide_folder -> {
                if (musicSet is MusicSet.Folder) {
                    hideFolder(musicSet as MusicSet.Folder)
                }
            }

            R.string.list_delete,
            R.string.delete -> {
                confirmDeleteSet()
            }

            else -> launchTrackAction(item.id)
        }
    }

    override fun onBindTitleArea(container: View, titleView: TextView, titleIconView: ImageView) {
        titleView.text = musicSet.name
        if (musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet is MusicSet.Genre) {
            titleIconView.setImageResource(R.drawable.ic_menu_edit_tags)
            titleIconView.visibility = View.VISIBLE
        } else if (musicSet is MusicSet.Folder) {
            titleIconView.setImageResource(R.drawable.ic_menu_share_2)
            titleIconView.visibility = View.VISIBLE
        }
    }


    private fun launchTrackAction(action: Int) {
        lifecycleScope.launch {
            val tracks = resolveTracks()
            if (tracks.isEmpty()) {
                ToastUtil.show(requireContext(), R.string.list_is_empty)
                return@launch
            }

            when (action) {
                R.string.operation_play -> playTracks(requireContext(), tracks, 0)

                R.string.play_next_2 -> {
                    playNextTracks(requireContext(), tracks)
                    ToastUtil.show(
                        requireContext(),
                        getString(R.string.enqueue_msg_count, tracks.size)
                    )
                }

                R.string.operation_enqueue -> {
                    enqueueTracks(requireContext(), tracks)
                    ToastUtil.show(
                        requireContext(),
                        getString(R.string.enqueue_msg_count, tracks.size)
                    )
                }

                R.string.add_to -> {
                    ActivityPlaylistSelect.Companion.start(requireContext(), tracks)
                }

                R.string.share -> {
                    MusicShareSupport.share(requireContext(), tracks)
                }
            }
        }
    }

    private fun showRenameDialog() {
        val host = activity ?: return
        if (musicSet is MusicSet.Playlist) {
            PlaylistInputDialog.Companion.forSet(
                musicSet,
                PlaylistInputDialog.Companion.MODE_RENAME_SET
            ).show(host.supportFragmentManager, "rename_playlist_dialog")
        } else {
            ToastUtil.show(requireContext(), R.string.feature_not_implemented)
        }
    }

    private fun hideFolder(folder: MusicSet.Folder) {
        lifecycleScope.launch {
            hiddenRepo.hideSelection(folderPaths = listOf(folder.folderPath), songIds = emptyList())
            ToastUtil.show(requireContext(), R.string.hidden_folders_tips)
        }
    }

    private fun confirmDeleteSet() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(musicSet.name)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSet()
            }
            .show()
    }

    private fun deleteSet() {
        lifecycleScope.launch {
            when (musicSet) {
                is MusicSet.Playlist -> {
                    deletePlaylist(musicSet.id)
                    ToastUtil.show(requireContext(), R.string.succeed)
                }

                else -> {
                    val tracks = resolveTracks()
                    if (tracks.isEmpty()) {
                        ToastUtil.show(requireContext(), R.string.list_is_empty)
                        return@launch
                    }
                    val deletedCount = deleteTracks(tracks)
                    ToastUtil.show(
                        requireContext(),
                        if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                    )
                }
            }
        }
    }

    private suspend fun resolveTracks(): List<Music> =
        libraryRepo.observeTracks(
            musicSet = musicSet,
            sortStyle = preferenceUtil.getSortStyle(musicSet),
            sortDescending = preferenceUtil.isSortReversed(musicSet, false)
        ).first()

    companion object {

        fun newInstance(musicSet: MusicSet): MusicSetOptionsDialog {
            return MusicSetOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, musicSet)
                }
            }
        }
    }
}

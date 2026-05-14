package gd.app.musicplayer.ui.library.musicset

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
import gd.app.musicplayer.domain.model.ArtworkRequest
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import gd.app.musicplayer.ui.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.playlist.PlaylistInputDialog
import gd.app.musicplayer.ui.shortcut.MusicSetShortcutHelper
import gd.app.musicplayer.ui.selection.MusicShareSupport
import gd.app.musicplayer.ui.tags.EditTagsActivity
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.model.isUserPlaylist
import gd.app.musicplayer.ui.shortcut.AppShortcutManager
import gd.app.musicplayer.ui.library.ARG_MUSIC_SET
import gd.app.musicplayer.ui.library.options.DeleteConfirmDialogFragment
import gd.app.musicplayer.ui.library.artwork.ManageArtworkDialogFragment
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicSetOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var musicSet: MusicSet
    private val viewModel: MusicSetOptionsViewModel by viewModels()
    private val deleteConfirmResultKey: String by lazy {
        "music_set_delete_confirm_${hashCode()}"
    }

    override fun onReadArguments(arguments: Bundle) {
        musicSet = arguments.parcelable(ARG_MUSIC_SET) ?: error("Missing music set")
    }

    override fun provideMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()

        items += MenuItem.create(R.string.operation_play, R.drawable.ic_menu_play)
        items += MenuItem.create(R.string.play_next_2, R.drawable.ic_menu_next_play)
        items += MenuItem.create(R.string.operation_enqueue, R.drawable.ic_menu_enqueue)

        if (musicSet.isUserPlaylist) {
            items += MenuItem.create(R.string.list_rename, R.drawable.ic_menu_rename)
        }

        if (musicSet is MusicSet.Album || musicSet is MusicSet.Folder || musicSet is MusicSet.Artist || musicSet is MusicSet.Genre ||
            musicSet is MusicSet.RecentlyPlayed
        ) {
            items += MenuItem.create(R.string.add_to, R.drawable.ic_menu_add)
        }

        if (musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet.isUserPlaylist || musicSet is MusicSet.Folder || musicSet is MusicSet.Genre) {
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

        if (musicSet.isUserPlaylist) {
            items += MenuItem.create(R.string.list_delete, R.drawable.ic_menu_delete)
        }

        if (musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet is MusicSet.Folder || musicSet is MusicSet.Genre) {
            items += MenuItem.create(R.string.delete, R.drawable.ic_menu_delete)
        }

        return items
    }

    override fun onMenuItemClicked(item: MenuItem) {
        when (item.id) {
            R.string.rename,
            R.string.list_rename -> {
                dismiss()
                showRenameDialog()
            }

            R.string.dlg_manage_artwork -> {
                dismiss()
                showManageArtworkDialog()
            }

            R.string.home_screen -> {
                dismiss()
                addToHomeScreen()
            }

            R.string.dlg_hide_folder -> {
                (musicSet as? MusicSet.Folder)?.let(viewModel::hideFolder)
            }

            R.string.list_delete,
            R.string.delete -> {
                confirmDeleteSet()
            }

            else -> launchTrackAction(item.id)
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

    override fun onBindTitleArea(container: View, titleView: TextView, titleIconView: ImageView) {
        titleView.text = musicSet.name
        when (musicSet) {
            is MusicSet.Artist, is MusicSet.Album, is MusicSet.Genre -> {
                titleIconView.setImageResource(R.drawable.ic_menu_edit_tags)
                titleIconView.visibility = View.VISIBLE
                val clickListener = View.OnClickListener {
                    dismissAllowingStateLoss()
                    EditTagsActivity.start(requireContext(), musicSet)
                }
                titleIconView.setOnClickListener(clickListener)
                titleView.setOnClickListener(clickListener)
            }

            is MusicSet.Folder -> {
                titleIconView.setImageResource(R.drawable.ic_menu_share_2)
                titleIconView.visibility = View.VISIBLE
                val clickListener = View.OnClickListener {
                    launchTrackAction(R.string.share)
                }
                titleIconView.setOnClickListener(clickListener)
                titleView.setOnClickListener(clickListener)
            }

            else -> {
                titleIconView.visibility = View.GONE
                titleView.setOnClickListener(null)
                titleIconView.setOnClickListener(null)
            }
        }
    }


    private fun launchTrackAction(action: Int) {
        viewModel.onTrackAction(action, musicSet)
    }

    private fun showManageArtworkDialog() {
        ManageArtworkDialogFragment
            .newInstance(ArtworkRequest.MusicSetTarget(musicSet))
            .show(parentFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
    }

    private fun addToHomeScreen() {
        if (!MusicSetShortcutHelper.isPinShortcutSupported(requireContext())) {
            ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            return
        }
        val success = MusicSetShortcutHelper.requestPinnedShortcut(
            context = requireContext(),
            musicSet = musicSet,
            title = musicSet.name
        )
        ToastUtil.show(requireContext(), if (success) R.string.succeed else R.string.feature_not_implemented)
    }

    private fun showRenameDialog() {
        val host = activity ?: return
        if (musicSet is MusicSet.Playlist) {
            PlaylistInputDialog.forSet(
                musicSet,
                PlaylistInputDialog.MODE_RENAME_SET
            ).show(host.supportFragmentManager, "rename_playlist_dialog")
        } else {
            ToastUtil.show(requireContext(), R.string.feature_not_implemented)
        }
    }

    private fun confirmDeleteSet() {
        parentFragmentManager.setFragmentResultListener(
            deleteConfirmResultKey,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_CONFIRMED, false)) {
                val deleteSourceFile = bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_EXTRA_CHECKED, true)
                viewModel.deleteSet(musicSet, deleteSourceFile)
                dismissAllowingStateLoss()
            }
        }

        val isPlaylist = musicSet is MusicSet.Playlist
        DeleteConfirmDialogFragment.forSetDelete(
            resultKey = deleteConfirmResultKey,
            setName = musicSet.name,
            isPlaylist = isPlaylist
        ).show(parentFragmentManager, DeleteConfirmDialogFragment::class.java.simpleName)
    }

    private fun handleEvent(event: MusicSetOptionsEvent) {
        when (event) {
            MusicSetOptionsEvent.Dismiss -> dismissAllowingStateLoss()
            is MusicSetOptionsEvent.OpenAddTo -> PlaylistSelectActivity.start(requireContext(), event.tracks)
            is MusicSetOptionsEvent.ShareTracks -> MusicShareSupport.share(requireContext(), event.tracks)
            is MusicSetOptionsEvent.ShowToast -> {
                if (event.args.isEmpty()) {
                    ToastUtil.show(requireContext(), event.messageRes)
                } else {
                    ToastUtil.show(requireContext(), getString(event.messageRes, *event.args.toTypedArray()))
                }
            }
        }
    }

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


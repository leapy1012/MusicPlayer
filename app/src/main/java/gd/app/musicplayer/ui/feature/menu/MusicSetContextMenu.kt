package gd.app.musicplayer.ui.feature.menu

import android.os.Build
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.isTrackCollection
import gd.app.musicplayer.data.repo.EditableTrackMetadata
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.feature.library.ManageArtworkDialogFragment
import gd.app.musicplayer.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.feature.playlist.PlaylistBackupManager
import gd.app.musicplayer.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.launch
import kotlin.collections.plusAssign

class MusicSetContextMenu(
    private val context: Context,
    private val musicSet: MusicSet,
    private val onSelect: (() -> Unit)? = null,
    private val onViewModeChanged: ((Int) -> Unit)? = null,
    private val onSortChanged: ((String, Boolean) -> Unit)? = null,
    private val tracksProvider: (() -> List<Music>)? = null
) : BaseContextMenu(context) {
    private val preferenceUtil: PreferenceUtil = context.appContainer.preferenceUtil
    private val shuffleTracks = context.appContainer.shuffleTracksUseCase
    private val playNextTracks = context.appContainer.playNextTracksUseCase
    private val enqueueTracks = context.appContainer.enqueueTracksUseCase

    override fun buildItems(): List<ContextMenuItem> {
        val items = mutableListOf<ContextMenuItem>()
        val resources = context.resources

        items += ContextMenuItem(resources.getString(R.string.select), R.string.select)

        if (musicSet.supportsShuffleAllMenu) {
            items += ContextMenuItem(resources.getString(R.string.shuffle_all), R.string.shuffle_all)
        }

        if (musicSet.supportsViewModeMenu) {
            items += ContextMenuItem(resources.getString(R.string.view_as), R.string.view_as, showArrow = true)
        }

        if (musicSet.supportsPlayNextMenu) {
            items += ContextMenuItem(resources.getString(R.string.play_next), R.string.play_next)
        }

        if (musicSet !is MusicSet.RecentlyPlayed && musicSet !is MusicSet.MostPlayed)
            items += ContextMenuItem(resources.getString(R.string.sort_by), R.string.sort_by, showArrow = true)

        if (musicSet.isTrackCollection && musicSet !is MusicSet.Tracks) {
            if (musicSet is MusicSet.Playlist || musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet is MusicSet.Genre) {
                items += ContextMenuItem(resources.getString(R.string.rename), R.string.rename)
            }

            if (musicSet is MusicSet.Artist || musicSet is MusicSet.Album || musicSet is MusicSet.Genre) {
                items += ContextMenuItem(resources.getString(R.string.dlg_manage_artwork), R.string.dlg_manage_artwork)
            }
            items += ContextMenuItem(resources.getString(R.string.add_to_queue), R.string.add_to_queue)
            items += ContextMenuItem(resources.getString(R.string.add_to_list), R.string.add_to_list)
            items += ContextMenuItem(resources.getString(R.string.add_to_home_screen), R.string.add_to_home_screen)

            if (musicSet is MusicSet.Favorites) {
                items += ContextMenuItem(resources.getString(R.string.clear_favorite), R.string.clear_favorite)
            }
            else if (musicSet is MusicSet.Playlist) {
                items += ContextMenuItem(resources.getString(R.string.list_delete), R.string.list_delete)
            } else if (musicSet is MusicSet.RecentlyPlayed) {
                items += ContextMenuItem(resources.getString(R.string.clear_recent_play), R.string.clear_recent_play)
            } else if (musicSet is MusicSet.MostPlayed) {
                items += ContextMenuItem(resources.getString(R.string.clear_most_play), R.string.clear_most_play)
            }
        }

        if (musicSet is MusicSet.Playlists) {
            items += ContextMenuItem(resources.getString(R.string.list_backup), R.string.list_backup)
            items += ContextMenuItem(resources.getString(R.string.list_recovery), R.string.list_recovery)
            items += ContextMenuItem(resources.getString(R.string.list_delete_empty), R.string.list_delete_empty)
        }


        return items
    }

    override fun onItemClicked(
        item: ContextMenuItem,
        anchor: View
    ) {
        val tabId = musicSet.id.toInt()
        when (item.titleRes) {
            R.string.select -> {
                dismiss()
                onSelect?.invoke()
            }

            R.string.view_as -> {
                val subMenu = ViewAsContextMenu(
                    context = context,
                    selectedMode = preferenceUtil.getListViewMode(tabId)) { mode ->
                    preferenceUtil.setListViewMode(tabId, mode)
                    onViewModeChanged?.invoke(mode)
                }
                dismiss()
                showAtLastPosition(subMenu)
            }

            R.string.sort_by -> {
                dismiss()
                showAtLastPosition(
                    SortByContextMenu(
                        context = context,
                        musicSet = musicSet,
                        onSortChanged = onSortChanged
                    )
                )
            }

            R.string.shuffle_all -> {
                dismiss()
                val tracks = resolveTracks() ?: return
                if (tracks.isEmpty()) {
                    ToastUtil.show(context, R.string.list_is_empty)
                    return
                }
                shuffleTracks(context, tracks)
            }

            R.string.play_next -> {
                dismiss()
                val tracks = resolveTracks() ?: return
                if (tracks.isEmpty()) {
                    ToastUtil.show(context, R.string.list_is_empty)
                    return
                }
                ToastUtil.show(context, context.getString(R.string.enqueue_msg_count, tracks.size))
                playNextTracks(context, tracks)
            }

            R.string.add_to_queue -> {
                dismiss()
                val tracks = resolveTracks() ?: return
                if (tracks.isEmpty()) {
                    ToastUtil.show(context, R.string.list_is_empty)
                    return
                }
                ToastUtil.show(context, context.getString(R.string.enqueue_msg_count, tracks.size))
                enqueueTracks(context, tracks)
            }

            R.string.add_to_list -> {
                dismiss()
                val tracks = resolveTracks() ?: return
                if (tracks.isEmpty()) {
                    ToastUtil.show(context, R.string.list_is_empty)
                    return
                }
                ActivityPlaylistSelect.start(context, tracks)
            }

            R.string.add_to_home_screen -> {
                dismiss()
//                MusicSetShortcutHelper.pinShortcut(context, musicSet)
            }

            R.string.rename -> {
                dismiss()
                val activity = context as? FragmentActivity ?: return
                when (val set = musicSet) {
                    is MusicSet.Playlist -> {
                        PlaylistInputDialog.forSet(
                            set,
                            PlaylistInputDialog.MODE_RENAME_SET
                        ).show(activity.supportFragmentManager, "rename_playlist_dialog")
                    }

                    is MusicSet.Album,
                    is MusicSet.Artist,
                    is MusicSet.Genre -> showCollectionRenameDialog(activity)

                    else -> ToastUtil.show(context, R.string.feature_not_implemented)
                }
            }

            R.string.dlg_manage_artwork -> {
                dismiss()
                val activity = context as? FragmentActivity ?: return
                ManageArtworkDialogFragment.newInstance(ArtworkRequest.MusicSetTarget(musicSet))
                    .show(activity.supportFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
            }

            R.string.list_delete -> {
                dismiss()
                val playlist = musicSet as? MusicSet.Playlist ?: return
                val activity = context as? FragmentActivity ?: return
                activity.lifecycleScope.launch {
                    context.appContainer.deletePlaylistUseCase(playlist.id)
                }
            }

            R.string.clear_favorite -> {
                dismiss()
                val activity = context as? FragmentActivity ?: return
                activity.lifecycleScope.launch {
                    context.appContainer.playlistRepo.clearPlaylistEntries(MusicSet.FAVORITES_ID)
                }
            }

            R.string.list_delete_empty -> {
                dismiss()
                val activity = context as? FragmentActivity ?: return
                activity.lifecycleScope.launch {
                    val deletedCount = context.appContainer.deleteEmptyPlaylistsUseCase()
                    ToastUtil.show(
                        context,
                        if (deletedCount > 0) R.string.succeed else R.string.list_delete_empty_failed
                    )
                }
            }

            R.string.clear_recent_play,
            R.string.list_backup,
            R.string.list_recovery -> {
                dismiss()
                val activity = context as? FragmentActivity ?: return
                activity.lifecycleScope.launch {
                    when (item.titleRes) {
                        R.string.clear_recent_play -> {
                            context.appContainer.musicDao.clearRecentlyPlayedStats()
                            ToastUtil.show(context, R.string.succeed)
                        }

                        R.string.list_backup -> {
                            val backupCount = PlaylistBackupManager.backup(context)
                            ToastUtil.show(
                                context,
                                if (backupCount > 0) R.string.succeed else R.string.list_is_empty
                            )
                        }

                        R.string.list_recovery -> {
                            val restoredCount = PlaylistBackupManager.restore(context)
                            ToastUtil.show(
                                context,
                                if (restoredCount > 0) R.string.succeed else R.string.list_is_empty
                            )
                        }
                    }
                }
            }

            R.string.clear_most_play -> {
                dismiss()
                val activity = context as? FragmentActivity ?: return
                activity.lifecycleScope.launch {
                    context.appContainer.musicDao.clearMostPlayedStats()
                    ToastUtil.show(context, R.string.succeed)
                }
            }
        }
    }

    private fun resolveTracks(): List<Music>? {
        val provider = tracksProvider ?: return null
        return provider.invoke()
    }

    private fun showCollectionRenameDialog(activity: FragmentActivity) {
        val tracks = resolveTracks().orEmpty()
        if (tracks.isEmpty()) {
            ToastUtil.show(context, R.string.list_is_empty)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ToastUtil.show(context, R.string.feature_not_implemented)
            return
        }

        val input = EditText(context).apply {
            setText(musicSet.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.rename) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty() || newName == musicSet.name) return@setPositiveButton
                activity.lifecycleScope.launch {
                    val metadataRepo = context.appContainer.trackMetadataRepo
                    val renamedCount = tracks.count { track ->
                        metadataRepo.updateTrackMetadata(
                            track,
                            when (musicSet) {
                                is MusicSet.Album -> EditableTrackMetadata(
                                    title = track.title,
                                    album = newName,
                                    artist = track.artist,
                                    genre = ""
                                )

                                is MusicSet.Artist -> EditableTrackMetadata(
                                    title = track.title,
                                    album = track.album,
                                    artist = newName,
                                    genre = ""
                                )

                                is MusicSet.Genre -> EditableTrackMetadata(
                                    title = track.title,
                                    album = track.album,
                                    artist = track.artist,
                                    genre = newName
                                )

                                else -> EditableTrackMetadata(
                                    title = track.title,
                                    album = track.album,
                                    artist = track.artist,
                                    genre = ""
                                )
                            }
                        )
                    }
                    ToastUtil.show(
                        context,
                        if (renamedCount > 0) R.string.rename_success else R.string.feature_not_implemented
                    )
                }
            }
            .show()
    }
}

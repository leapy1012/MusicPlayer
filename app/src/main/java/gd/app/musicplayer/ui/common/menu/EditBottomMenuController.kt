package gd.app.musicplayer.ui.common.menu

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.domain.model.MenuItemModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.hidden.HideSelectionUseCase
import gd.app.musicplayer.domain.usecase.library.RemoveTrackFromGeneratedMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.RemoveFromPlayingQueueUseCase
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.RemoveTracksFromPlaylistUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksFromLibraryUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.feature.library.options.DeleteConfirmDialogFragment
import gd.app.musicplayer.ui.selection.MusicEditActivity
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.feature.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.selection.MusicShareSupport
import kotlinx.coroutines.launch

/** c9.a **/
interface OnItemClickListener<T> {
    fun onItemClick(item: T, clickedView: View, position: Int)
}

/** m5.p **/
class EditBottomMenuController(
    private val activity: MusicEditActivity,
    private val musicSet: MusicSet,
    private val menuContainer: ViewGroup,
    private val playTracksUseCase: PlayTracksUseCase,
    private val playNextTracksUseCase: PlayNextTracksUseCase,
    private val enqueueTracksUseCase: EnqueueTracksUseCase,
    private val removeFromPlayingQueueUseCase: RemoveFromPlayingQueueUseCase,
    private val removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase,
    private val removeTrackFromGeneratedMusicSetUseCase: RemoveTrackFromGeneratedMusicSetUseCase,
    private val deleteTracksFromLibraryUseCase: DeleteTracksFromLibraryUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase,
    private val hideSelectionUseCase: HideSelectionUseCase,
    private val addTracksToPlaylistsUseCase: AddTracksToPlaylistsUseCase
) : OnItemClickListener<MenuItemModel> {

    private var menuItems: List<MenuItemModel> = emptyList()

    // original: e
    private fun buildMenuItems(): List<MenuItemModel> {
        val items = ArrayList<MenuItemModel>()

        if (musicSet is MusicSet.Favorites) {
            items.add(
                MenuItemModel.create(R.string.operation_play).setIcon(R.drawable.vector_edit_play)
            )
            items.add(MenuItemModel.create(R.string.add_to).setIcon(R.drawable.vector_editor_add))
            items.add(
                MenuItemModel.create(R.string.operation_enqueue)
                    .setIcon(R.drawable.vector_editor_enqueue)
            )
            items.add(
                MenuItemModel.create(R.string.remove).setIcon(R.drawable.vector_editor_remove)
            )
            items.add(MenuItemModel.create(R.string.share).setIcon(R.drawable.vector_editor_share))
            return items
        }

        if (musicSet is MusicSet.Playlists) {
            items.add(
                MenuItemModel.create(R.string.play_next_2)
                    .setIcon(R.drawable.vector_editor_play_next)
            )
            items.add(MenuItemModel.create(R.string.add_to).setIcon(R.drawable.vector_editor_add))
            items.add(
                MenuItemModel.create(R.string.remove).setIcon(R.drawable.vector_editor_remove)
            )
            items.add(
                MenuItemModel.create(R.string.add_to_favourite_2)
                    .setIcon(R.drawable.vector_editor_favorite)
            )
            items.add(MenuItemModel.create(R.string.more).setIcon(R.drawable.vector_more))
            items.add(MenuItemModel.create(R.string.hide_music))
            items.add(MenuItemModel.create(R.string.delete))
            return items
        }


        items.add(
            MenuItemModel.create(R.string.operation_play).setIcon(R.drawable.vector_edit_play)
        )
        items.add(MenuItemModel.create(R.string.add_to).setIcon(R.drawable.vector_editor_add))
        items.add(
            MenuItemModel.create(R.string.operation_enqueue)
                .setIcon(R.drawable.vector_editor_enqueue)
        )
        items.add(
            MenuItemModel.create(R.string.add_to_favourite_2)
                .setIcon(R.drawable.vector_editor_favorite)
        )
        items.add(MenuItemModel.create(R.string.more).setIcon(R.drawable.vector_more))

        if (musicSet is MusicSet.Queue || musicSet is MusicSet.RecentlyPlayed || musicSet is MusicSet.Playlist || musicSet is MusicSet.MostPlayed) {
            items.add(MenuItemModel.create(R.string.remove_from_list))
        } else {
            items.add(MenuItemModel.create(R.string.delete))
        }

        items.add(MenuItemModel.create(R.string.hide_music))
        items.add(MenuItemModel.create(R.string.share))
        return items
    }

    // original: i
    private fun buildTargetMusicList(
        selectedSongs: List<Music>,
        keepDuplicates: Boolean
    ): List<Music> {
        val result = ArrayList<Music>()

        if (musicSet !is MusicSet.Playlists) {
            result.addAll(selectedSongs)
            return result
        }

        return if (keepDuplicates) {
            selectedSongs.toList()
        } else {
            selectedSongs.distinctBy { it.id }
        }
    }

    // original: f
    override fun onItemClick(item: MenuItemModel, clickedView: View, position: Int) {
        val actionId = item.getTitleResId()

        if (actionId == R.string.more) {
            val extraItems = menuItems.subList(5, menuItems.size)
            EditMorePopupMenu(
                context = activity,
                items = extraItems,
                theme = activity.themeRepo.getCorePalette(),
                itemClickListener = this
            ).show(
                anchor = clickedView,
                yOff = -calculateMorePopupOffsetPx(extraItems.size)
            )
            return
        }

        val selectedSongs = activity.getSelectedItems()
        if (selectedSongs.isEmpty()) {
            ToastUtil.show(activity, R.string.select_musics_empty)
            return
        }

        when (actionId) {
            R.string.remove_from_list,
            R.string.remove -> {
                removeSelectedSongs(buildTargetMusicList(selectedSongs, true))
            }

            R.string.delete -> {
                confirmDeleteSelectedSongs(buildTargetMusicList(selectedSongs, false))
            }

            R.string.share -> {
                MusicShareSupport.share(activity, buildTargetMusicList(selectedSongs, false))
            }

            R.string.hide_music -> {
                hideSelectedSongs(buildTargetMusicList(selectedSongs, false))
            }

            R.string.operation_play -> {
                val songsToPlay = buildTargetMusicList(selectedSongs, true)
                ToastUtil.show(
                    activity,
                    activity.getString(R.string.edit_play_tips, songsToPlay.size)
                )
                playTracksUseCase(songsToPlay, 0)
            }

            R.string.play_next_2 -> {
                val songsToPlayNext = buildTargetMusicList(selectedSongs, true)
                playNextTracksUseCase(songsToPlayNext)
                ToastUtil.show(
                    activity,
                    activity.getString(R.string.enqueue_msg_count, songsToPlayNext.size)
                )
            }

            R.string.add_to -> {
                PlaylistSelectActivity.start(activity, buildTargetMusicList(selectedSongs, false))
            }

            R.string.operation_enqueue -> {
                val songsToEnqueue = buildTargetMusicList(selectedSongs, true)
                ToastUtil.show(
                    activity,
                    activity.getString(R.string.enqueue_msg_count, songsToEnqueue.size)
                )
                enqueueTracksUseCase(songsToEnqueue)
            }

            R.string.add_to_favourite_2 -> {
                addSelectedSongsToFavorites(buildTargetMusicList(selectedSongs, false))
            }
        }
    }

    private fun removeSelectedSongs(songs: List<Music>) {
        if (musicSet is MusicSet.Queue) {
            activity.lifecycleScope.launch {
                removeFromPlayingQueueUseCase(songs)
                ToastUtil.show(activity, R.string.succeed)
            }
            return
        }

        val message = if (songs.size == 1) {
            activity.getString(R.string.remove_song_from_list_msg, songs.first().title)
        } else {
            activity.getString(R.string.remove_songs_from_list_msg, songs.size.toString())
        }

        activity.showMessageDialog(
            activity.createMessageDialogConfig(
                title = activity.getString(R.string.remove),
                message = message,
                negativeText = activity.getString(R.string.cancel),
                positiveText = activity.getString(R.string.remove),
                positiveClickListener = { _, _ ->
                    activity.lifecycleScope.launch {
                        when (musicSet) {
                            is MusicSet.Playlist -> {
                                removeTracksFromPlaylistUseCase(musicSet.id, songs.map(Music::id))
                            }

                            is MusicSet.Favorites -> {
                                removeTracksFromPlaylistUseCase(MusicSet.FAVORITES, songs.map(Music::id))
                            }

                            is MusicSet.RecentlyPlayed,
                            is MusicSet.MostPlayed -> {
                                songs
                                    .asSequence()
                                    .map(Music::id)
                                    .distinct()
                                    .forEach { trackId ->
                                        removeTrackFromGeneratedMusicSetUseCase(musicSet, trackId)
                                    }
                            }

                            else -> return@launch
                        }
                        ToastUtil.show(activity, R.string.succeed)
                    }
                }
            )
        )
    }

    private fun confirmDeleteSelectedSongs(songs: List<Music>) {
        val resultKey = "music_edit_delete_confirm_result"
        activity.supportFragmentManager.setFragmentResultListener(
            resultKey,
            activity
        ) { _, bundle ->
            if (!bundle.getBoolean(
                    DeleteConfirmDialogFragment.RESULT_CONFIRMED,
                    false
                )
            ) return@setFragmentResultListener
            val deleteFromDevice =
                bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_EXTRA_CHECKED, true)
            activity.lifecycleScope.launch {
                val deletedCount = if (deleteFromDevice) {
                    deleteTracksUseCase(songs)
                } else {
                    deleteTracksFromLibraryUseCase(songs.map(Music::id))
                    songs.size
                }
                ToastUtil.show(
                    activity,
                    if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                )
            }
        }

        val dialog = if (songs.size == 1) {
            DeleteConfirmDialogFragment.forTrackDelete(
                resultKey = resultKey,
                trackTitle = songs.first().title
            )
        } else {
            DeleteConfirmDialogFragment.forTracksDelete(
                resultKey = resultKey,
                trackCount = songs.size
            )
        }
        dialog.show(
            activity.supportFragmentManager,
            DeleteConfirmDialogFragment::class.java.simpleName
        )
    }

    private fun hideSelectedSongs(songs: List<Music>) {
        activity.lifecycleScope.launch {
            hideSelectionUseCase(
                folderPaths = emptyList(),
                songIds = songs.map(Music::id)
            )
            ToastUtil.show(activity, R.string.hidden_folders_tips)
        }
    }

    private fun addSelectedSongsToFavorites(songs: List<Music>) {
        activity.lifecycleScope.launch {
            val songsToAdd = songs.filterNot { it.isFavorite() }
            val addedCount = addTracksToPlaylistsUseCase(
                listOf(MusicSet.FAVORITES),
                songsToAdd
            )
            ToastUtil.show(
                activity,
                if (addedCount > 0) R.string.succeed else R.string.list_contains_music
            )
        }
    }

    // original: h
    fun bindMenu() {
        menuItems = buildMenuItems()

        val childCount = menuContainer.childCount
        val bindCount = minOf(childCount, menuItems.size)
        for (index in 0 until bindCount) {
            val itemView = menuContainer.getChildAt(index) as ViewGroup
            val menuItem = menuItems[index]

            (itemView.getChildAt(0) as ImageView).setImageResource(menuItem.getIconResId())
            (itemView.getChildAt(1) as TextView).text = menuItem.getTitle(activity)

            itemView.setOnClickListener { view ->
                onItemClick(menuItem, view, 0)
            }
        }
    }

    private fun calculateMorePopupOffsetPx(itemCount: Int): Int {
        val itemHeightPx = activity.dpToPx(48f)
        return (itemHeightPx * itemCount) + 10
    }
}

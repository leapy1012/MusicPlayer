package gd.app.musicplayer.ui.common.menu

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.data.model.MenuItemModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.queue.currentTrack
import gd.app.musicplayer.ui.feature.selection.MusicEditActivity
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.selection.MusicShareSupport
import kotlinx.coroutines.launch

/** c9.a **/
interface OnItemClickListener<T> {
    fun onItemClick(item: T, clickedView: View, position: Int)
}

/** m5.p **/
class EditBottomMenuController(
    private val activity: MusicEditActivity,
    private val musicSet: MusicSet,
    private val menuContainer: ViewGroup
) : OnItemClickListener<MenuItemModel> {

    private var menuItems: List<MenuItemModel> = emptyList()

    // original: e
    private fun buildMenuItems(): List<MenuItemModel> {
        val items = ArrayList<MenuItemModel>()

        if (musicSet is MusicSet.Favorites) {
            items.add(MenuItemModel.create(R.string.operation_play).setIcon(R.drawable.vector_edit_play))
            items.add(MenuItemModel.create(R.string.add_to).setIcon(R.drawable.vector_editor_add))
            items.add(MenuItemModel.create(R.string.operation_enqueue).setIcon(R.drawable.vector_editor_enqueue))
            items.add(MenuItemModel.create(R.string.remove).setIcon(R.drawable.vector_editor_remove))
            items.add(MenuItemModel.create(R.string.share).setIcon(R.drawable.vector_editor_share))
            return items
        }

        if (musicSet is MusicSet.Playlists) {
            items.add(MenuItemModel.create(R.string.play_next_2).setIcon(R.drawable.vector_editor_play_next))
            items.add(MenuItemModel.create(R.string.add_to).setIcon(R.drawable.vector_editor_add))
            items.add(MenuItemModel.create(R.string.remove).setIcon(R.drawable.vector_editor_remove))
            items.add(MenuItemModel.create(R.string.add_to_favourite_2).setIcon(R.drawable.vector_editor_favorite))
            items.add(MenuItemModel.create(R.string.more).setIcon(R.drawable.vector_more))
            items.add(MenuItemModel.create(R.string.hide_music))
            items.add(MenuItemModel.create(R.string.delete))
            return items
        }

        items.add(MenuItemModel.create(R.string.operation_play).setIcon(R.drawable.vector_edit_play))
        items.add(MenuItemModel.create(R.string.add_to).setIcon(R.drawable.vector_editor_add))
        items.add(MenuItemModel.create(R.string.operation_enqueue).setIcon(R.drawable.vector_editor_enqueue))
        items.add(MenuItemModel.create(R.string.add_to_favourite_2).setIcon(R.drawable.vector_editor_favorite))
        items.add(MenuItemModel.create(R.string.more).setIcon(R.drawable.vector_more))

        if (musicSet is MusicSet.Queue || musicSet is MusicSet.RecentlyPlayed || musicSet.id > 0) {
            items.add(MenuItemModel.create(R.string.remove_from_list))
        } else {
            items.add(MenuItemModel.create(R.string.delete))
        }

        items.add(MenuItemModel.create(R.string.hide_music))
        items.add(MenuItemModel.create(R.string.share))
        return items
    }

    // original: i
    private fun buildTargetMusicList(selectedSongs: List<Music>, keepDuplicates: Boolean): List<Music> {
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
            EditMorePopupMenu(activity, extraItems, this).show(clickedView)
            return
        }

        val selectedSongs = activity.getSelectedItems()
        if (selectedSongs.isEmpty()) {
            ToastUtil.show (activity, R.string.select_musics_empty)
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
                ToastUtil.show(activity, activity.getString(R.string.edit_play_tips, songsToPlay.size))
                activity.applicationContext.appDependencies.playTracksUseCase(activity, songsToPlay, 0)
            }

            R.string.play_next_2 -> {
                val songsToPlayNext = buildTargetMusicList(selectedSongs, true)
                activity.applicationContext.appDependencies.playNextTracksUseCase(activity, songsToPlayNext)
                ToastUtil.show(activity, activity.getString(R.string.enqueue_msg_count, songsToPlayNext.size))
            }

            R.string.add_to -> {
                ActivityPlaylistSelect.start(activity, buildTargetMusicList(selectedSongs, false))
            }

            R.string.operation_enqueue -> {
                val songsToEnqueue = buildTargetMusicList(selectedSongs, true)
                ToastUtil.show(
                    activity,
                    activity.getString(R.string.enqueue_msg_count, songsToEnqueue.size)
                )
                activity.applicationContext.appDependencies.enqueueTracksUseCase(activity, songsToEnqueue)
            }

            R.string.add_to_favourite_2 -> {
                addSelectedSongsToFavorites(buildTargetMusicList(selectedSongs, false))
            }
        }
    }

    private fun removeSelectedSongs(songs: List<Music>) {
        activity.lifecycleScope.launch {
            when (musicSet) {
                is MusicSet.Playlist -> {
                    activity.applicationContext.appDependencies.playlistRepo
                        .removeTracksFromPlaylist(musicSet.id, songs.map(Music::id))
                    ToastUtil.show(activity, R.string.succeed)
                }

                is MusicSet.Favorites -> {
                    activity.applicationContext.appDependencies.playlistRepo
                        .removeTracksFromPlaylist(MusicSet.FAVORITES, songs.map(Music::id))
                    ToastUtil.show(activity, R.string.succeed)
                }

                is MusicSet.Queue -> {
                    val selectedIds = songs.mapTo(hashSetOf(), Music::id)
                    val state = PlaybackGateway.state.value
                    val newQueue = state.queue.filterNot { it.id in selectedIds }
                    val newIndex = when {
                        newQueue.isEmpty() -> -1
                        state.currentTrack?.id in selectedIds -> 0
                        else -> newQueue.indexOfFirst { it.id == state.currentTrack?.id }
                            .takeIf { it >= 0 }
                            ?: state.currentIndex.coerceAtMost(newQueue.lastIndex)
                    }
                    PlaybackGateway.replaceQueue(activity, newQueue, newIndex)
                    ToastUtil.show(activity, R.string.succeed)
                }

                else -> {
                    confirmDeleteSelectedSongs(songs)
                }
            }
        }
    }

    private fun confirmDeleteSelectedSongs(songs: List<Music>) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.delete)
            .setMessage(activity.resources.getQuantityString(R.plurals.plurals_select_music, songs.size, songs.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                activity.lifecycleScope.launch {
                    val deletedCount = activity.applicationContext.appDependencies.deleteTracksUseCase(songs)
                    ToastUtil.show(
                        activity,
                        if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                    )
                }
            }
            .show()
    }

    private fun hideSelectedSongs(songs: List<Music>) {
        activity.lifecycleScope.launch {
            activity.applicationContext.appDependencies.hiddenRepo.hideSelection(
                folderPaths = emptyList(),
                songIds = songs.map(Music::id)
            )
            ToastUtil.show(activity, R.string.hidden_folders_tips)
        }
    }

    private fun addSelectedSongsToFavorites(songs: List<Music>) {
        activity.lifecycleScope.launch {
            val songsToAdd = songs.filterNot { it.isFavorite() }
            val addedCount = activity.applicationContext.appDependencies.playlistRepo
                .addTracksToPlaylists(listOf(MusicSet.FAVORITES), songsToAdd)
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
        for (index in 0 until childCount) {
            val itemView = menuContainer.getChildAt(index) as ViewGroup
            val menuItem = menuItems[index]

            (itemView.getChildAt(0) as ImageView).setImageResource(menuItem.getIconResId())
            (itemView.getChildAt(1) as TextView).text = menuItem.getTitle(activity)

            itemView.setOnClickListener { view ->
                onItemClick(menuItem, view, 0)
            }
        }
    }
}



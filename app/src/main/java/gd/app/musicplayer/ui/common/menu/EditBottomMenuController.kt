package gd.app.musicplayer.ui.common.menu

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MenuItemModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.feature.selection.MusicEditActivity
import gd.app.musicplayer.core.util.ToastUtil

//import o8.r0
//import w7.k0
//import z6.y

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

    companion object {
        private fun showFavouriteResultToast(controller: EditBottomMenuController, success: Boolean) {
//            r0.e(
//                controller.activity,
//                if (success) R.string.succeed else R.string.list_contains_music
//            )
//            v8.d.e()
        }

        private fun hideSongs(controller: EditBottomMenuController, songs: List<Music>) {
//            u5.d.y().n0(songs, true)
//            y.Y().H0(songs)
//            r0.e(controller.activity, R.string.hidden_folders_tips)
        }

        private fun addSongsToFavourite(controller: EditBottomMenuController, songs: List<Music>) {
//            val success = u5.d.y().b(songs, 1)
//
//            for (song in songs) {
//                song.c0(1)
//            }
//
//            y.Y().l1(songs)
//            y.Y().t0()
//
//            controller.activity.runOnUiThread {
//                showFavouriteResultToast(controller, success)
//            }
        }
    }

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

        if (/* musicSet.j() == -11 || */ musicSet is MusicSet.RecentlyPlayed || musicSet.id > 0) {
            items.add(MenuItemModel.create(R.string.remove_from_list))
        } else {
            items.add(MenuItemModel.create(R.string.delete))
        }

        items.add(MenuItemModel.create(R.string.hide_music))
        items.add(MenuItemModel.create(R.string.share))
        return items
    }

    // original: i
    private fun buildTargetMusicList(selectedSongs: Set<Music>, keepDuplicates: Boolean): List<Music> {
        val result = ArrayList<Music>()

        if (musicSet !is MusicSet.Playlists) {
            result.addAll(selectedSongs)
            return result
        }

//        if (keepDuplicates) {
//            for (song in selectedSongs) {
//                val originalSong = song.a()
//                originalSong.W(song.s())
//                result.add(originalSong)
//            }
//        } else {
//            val uniqueSongs = HashSet<Music>()
//            for (song in selectedSongs) {
//                val originalSong = song.a()
//                if (uniqueSongs.add(originalSong)) {
//                    result.add(originalSong)
//                }
//            }
//        }

        return result
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
//                if (musicSet is MusicSet.Playlists) {
//                    y.Y().L0(buildTargetMusicList(selectedSongs, true))
//                } else {
//                    p5.f.w0(
//                        2,
//                        q5.b()
//                            .f(buildTargetMusicList(selectedSongs, false))
//                            .g(musicSet)
//                    ).show(activity.Z(), null)
//                }
            }

            R.string.delete -> {
//                p5.f.w0(
//                    1,
//                    q5.b().f(buildTargetMusicList(selectedSongs, false))
//                ).show(activity.Z(), null)
            }

            R.string.share -> {
//                val songsToShare = buildTargetMusicList(selectedSongs, false)
//                if (songsToShare.size > 1) {
//                    k0.k(activity, songsToShare)
//                } else {
//                    k0.j(activity, songsToShare[0])
//                }
            }

            R.string.hide_music -> {
                val songsToHide = buildTargetMusicList(selectedSongs, false)
//                u5.a.a {
//                    hideSongs(this, songsToHide)
//                }
            }

            R.string.operation_play -> {
                val songsToPlay = buildTargetMusicList(selectedSongs, true)
//                r0.f(
//                    activity,
//                    activity.getString(R.string.edit_play_tips, songsToPlay.size)
//                )
//                y.Y().V0(songsToPlay, 0, 5)
            }

            R.string.play_next_2 -> {
//                y.Y().P(buildTargetMusicList(selectedSongs, true))
            }

            R.string.add_to -> {
//                ActivityPlaylistSelect.l1(
//                    activity,
//                    buildTargetMusicList(selectedSongs, false),
//                    1
//                )
            }

            R.string.operation_enqueue -> {
                val songsToEnqueue = buildTargetMusicList(selectedSongs, true)
                ToastUtil.show(
                    activity,
                    activity.getString(R.string.enqueue_msg_count, songsToEnqueue.size)
                )
//                y.Y().N(songsToEnqueue)
            }

            R.string.add_to_favourite_2 -> {
//                val songsToFavourite = buildTargetMusicList(selectedSongs, false)
//                g9.a.q(activity, activity.getString(R.string.common_waiting))
//                u5.a.a {
//                    addSongsToFavourite(this, songsToFavourite)
//                }
            }
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
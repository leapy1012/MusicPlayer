package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import android.widget.AdapterView
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.supportsArtworkMenu
import gd.app.musicplayer.core.common.extension.supportsPlayNextMenu
import gd.app.musicplayer.core.common.extension.supportsRenameMenu
import gd.app.musicplayer.core.common.extension.supportsShuffleAllMenu
import gd.app.musicplayer.core.common.extension.supportsSortMenu
import gd.app.musicplayer.core.common.extension.supportsViewModeMenu
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.isTrackCollection
import gd.app.musicplayer.feature.library.musicset.MusicSetAdapter
import java.util.ArrayList

/**
 * Library / playlist overflow menu backed by COUI [COUIPopupListWindow].
 */
class ContextMenu(
    private val context: Context,
    private val musicSet: MusicSet,
    @Suppress("UNUSED_PARAMETER") theme: ThemePalette,
    private val onAction: (ContextMenuAction) -> Unit,
    private val selectedViewMode: Int = MusicSetAdapter.VIEW_MODE_LIST,
    private val currentSortStyle: String = "",
    private val currentSortDescending: Boolean = false
) {

    private var popup: COUIPopupListWindow? = null
    private var items: ArrayList<PopupListItem> = ArrayList()
    private var openSubMenuParentId: Int = -1

    fun show(anchor: View) {
        dismiss()

        items = buildItems()
        if (items.isEmpty()) return

        val window = COUIPopupListWindow(context).also { popup = it }
        window.setItemList(items)
        window.setOnItemClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@OnItemClickListener
            if (item.hasSubMenu()) {
                openSubMenuParentId = item.id
                return@OnItemClickListener
            }
            dispatchMainAction(item.id)
            dismiss()
        })
        window.setSubMenuClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
            handleSubMenuClick(position)
            dismiss()
        })
        window.show(anchor)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
        openSubMenuParentId = -1
    }

    private fun buildItems(): ArrayList<PopupListItem> {
        val list = ArrayList<PopupListItem>()

        list += item(ID_SELECT, R.string.select)

        if (musicSet.supportsShuffleAllMenu) {
            list += item(ID_SHUFFLE_ALL, R.string.shuffle_all)
        }

        if (musicSet.supportsViewModeMenu) {
            list += item(ID_VIEW_AS, R.string.view_as, viewAsSubItems())
        }

        if (musicSet.supportsPlayNextMenu) {
            list += item(ID_PLAY_NEXT, R.string.play_next)
        }

        if (musicSet.supportsSortMenu) {
            list += item(ID_SORT_BY, R.string.sort_by, sortSubItems())
        }

        addTrackCollectionItems(list)
        addPlaylistRootItems(list)
        return list
    }

    private fun addTrackCollectionItems(list: ArrayList<PopupListItem>) {
        if (!musicSet.isTrackCollection || musicSet is MusicSet.Tracks) return

        if (musicSet.supportsRenameMenu) {
            list += item(ID_RENAME, R.string.rename)
        }
        if (musicSet.supportsArtworkMenu) {
            list += item(ID_MANAGE_ARTWORK, R.string.dlg_manage_artwork)
        }

        list += item(ID_ADD_TO_QUEUE, R.string.add_to_queue)
        list += item(ID_ADD_TO_PLAYLIST, R.string.add_to_list)
        list += item(ID_ADD_TO_HOME, R.string.add_to_home_screen)

        when (musicSet) {
            is MusicSet.Favorites -> list += item(ID_CLEAR_FAVORITES, R.string.clear_favorite)
            is MusicSet.RecentlyAdded -> list += item(ID_CLEAR_RECENT_ADD, R.string.clear_recent_add)
            is MusicSet.Playlist -> list += item(ID_DELETE_PLAYLIST, R.string.list_delete)
            is MusicSet.RecentlyPlayed ->
                list += item(ID_CLEAR_RECENT_PLAY, R.string.clear_recent_play)
            is MusicSet.MostPlayed -> list += item(ID_CLEAR_MOST_PLAY, R.string.clear_most_play)
            else -> Unit
        }
    }

    private fun addPlaylistRootItems(list: ArrayList<PopupListItem>) {
        if (musicSet !is MusicSet.Playlists) return
        list += item(ID_BACKUP, R.string.list_backup)
        list += item(ID_RESTORE, R.string.list_recovery)
        list += item(ID_DELETE_EMPTY, R.string.list_delete_empty)
    }

    private fun viewAsSubItems(): ArrayList<PopupListItem> = arrayListOf(
        checkedItem(ID_VIEW_LIST, R.string.view_as_list, selectedViewMode == MusicSetAdapter.VIEW_MODE_LIST),
        checkedItem(ID_VIEW_GRID, R.string.view_as_grid, selectedViewMode == MusicSetAdapter.VIEW_MODE_GRID)
    )

    private fun sortSubItems(): ArrayList<PopupListItem> {
        return ArrayList(
            MusicSetSortOptions.build(musicSet, currentSortStyle, currentSortDescending).map {
                checkedItem(it.id, it.titleRes, it.isSelected)
            }
        )
    }

    private fun handleSubMenuClick(position: Int) {
        val parent = items.firstOrNull { it.id == openSubMenuParentId } ?: return
        val subItems = parent.subMenuItemList ?: return
        // On small screens COUI prepends the parent header; skip non-data rows.
        val clicked = subItems.getOrNull(position)
            ?: subItems.getOrNull(position - 1)
            ?: return

        when (openSubMenuParentId) {
            ID_VIEW_AS -> onAction(
                if (clicked.id == ID_VIEW_GRID) {
                    ContextMenuAction.ViewAsGrid
                } else {
                    ContextMenuAction.ViewAsList
                }
            )

            ID_SORT_BY -> {
                val option = MusicSetSortOptions.find(
                    musicSet = musicSet,
                    id = clicked.id,
                    currentSortStyle = currentSortStyle,
                    currentSortDescending = currentSortDescending
                ) ?: return
                onAction(
                    ContextMenuAction.SortChanged(
                        sortKey = option.style,
                        descending = option.reversed
                    )
                )
            }
        }
    }

    private fun dispatchMainAction(id: Int) {
        when (id) {
            ID_SELECT -> onAction(ContextMenuAction.Select)
            ID_SHUFFLE_ALL -> onAction(ContextMenuAction.ShuffleAll)
            ID_PLAY_NEXT -> onAction(ContextMenuAction.PlayNext)
            ID_ADD_TO_QUEUE -> onAction(ContextMenuAction.AddToQueue)
            ID_ADD_TO_PLAYLIST -> onAction(ContextMenuAction.AddToPlaylist)
            ID_ADD_TO_HOME -> onAction(ContextMenuAction.AddToHomeScreen)
            ID_RENAME -> onAction(ContextMenuAction.Rename)
            ID_MANAGE_ARTWORK -> onAction(ContextMenuAction.ManageArtwork)
            ID_DELETE_PLAYLIST -> onAction(ContextMenuAction.DeletePlaylist)
            ID_CLEAR_FAVORITES -> onAction(ContextMenuAction.ClearFavorites)
            ID_CLEAR_RECENT_ADD -> onAction(ContextMenuAction.ClearRecentlyAdded)
            ID_CLEAR_RECENT_PLAY -> onAction(ContextMenuAction.ClearRecentlyPlayed)
            ID_CLEAR_MOST_PLAY -> onAction(ContextMenuAction.ClearMostPlayed)
            ID_BACKUP -> onAction(ContextMenuAction.BackupPlaylists)
            ID_RESTORE -> onAction(ContextMenuAction.RestorePlaylists)
            ID_DELETE_EMPTY -> onAction(ContextMenuAction.DeleteEmptyPlaylists)
        }
    }

    private fun item(
        id: Int,
        titleRes: Int,
        subItems: ArrayList<PopupListItem>? = null
    ): PopupListItem {
        return PopupListItem.Builder()
            .setId(id)
            .setTitle(context.getString(titleRes))
            .setIsEnable(true)
            .apply {
                if (subItems != null) {
                    setSubMenuItemList(subItems)
                }
            }
            .build()
    }

    private fun checkedItem(id: Int, titleRes: Int, checked: Boolean): PopupListItem {
        return PopupListItem.Builder()
            .setId(id)
            .setTitle(context.getString(titleRes))
            .setIsEnable(true)
            .setIsChecked(checked)
            .build()
    }

    private companion object {
        private const val ID_SELECT = 1
        private const val ID_SHUFFLE_ALL = 2
        private const val ID_VIEW_AS = 3
        private const val ID_PLAY_NEXT = 4
        private const val ID_SORT_BY = 5
        private const val ID_RENAME = 6
        private const val ID_MANAGE_ARTWORK = 7
        private const val ID_ADD_TO_QUEUE = 8
        private const val ID_ADD_TO_PLAYLIST = 9
        private const val ID_ADD_TO_HOME = 10
        private const val ID_CLEAR_FAVORITES = 11
        private const val ID_CLEAR_RECENT_ADD = 12
        private const val ID_CLEAR_RECENT_PLAY = 13
        private const val ID_CLEAR_MOST_PLAY = 14
        private const val ID_DELETE_PLAYLIST = 15
        private const val ID_BACKUP = 16
        private const val ID_RESTORE = 17
        private const val ID_DELETE_EMPTY = 18
        private const val ID_VIEW_LIST = 100
        private const val ID_VIEW_GRID = 101
    }
}

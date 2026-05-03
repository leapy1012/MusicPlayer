package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.supportsPlayNextMenu
import gd.app.musicplayer.core.extension.supportsShuffleAllMenu
import gd.app.musicplayer.core.extension.supportsViewModeMenu
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.isTrackCollection
import gd.app.musicplayer.ui.feature.library.MusicSetAdapter

class MusicSetContextMenu(
    context: Context,
    private val musicSet: MusicSet,
    private val onAction: (MusicSetMenuAction) -> Unit,
    private val selectedViewMode: Int = MusicSetAdapter.VIEW_MODE_LIST
) : BaseContextMenu(context) {

    override fun buildItems(): List<ContextMenuItem> {
        return buildList {
            addMenuItem(R.string.select)

            if (musicSet.supportsShuffleAllMenu) {
                addMenuItem(R.string.shuffle_all)
            }

            if (musicSet.supportsViewModeMenu) {
                addMenuItem(
                    titleRes = R.string.view_as,
                    showArrow = true
                )
            }

            if (musicSet.supportsPlayNextMenu) {
                addMenuItem(R.string.play_next)
            }

            if (musicSet.supportsSortMenu) {
                addMenuItem(
                    titleRes = R.string.sort_by,
                    showArrow = true
                )
            }

            addTrackCollectionItems()
            addPlaylistRootItems()
        }
    }

    override fun onItemClicked(
        item: ContextMenuItem,
        anchor: View
    ) {
        when (item.titleRes) {
            R.string.select -> {
                dismiss()
                onAction(MusicSetMenuAction.Select)
            }

            R.string.shuffle_all -> {
                dismiss()
                onAction(MusicSetMenuAction.ShuffleAll)
            }

            R.string.view_as -> {
                showViewModeMenu()
            }

            R.string.sort_by -> {
                showSortMenu()
            }

            R.string.play_next -> {
                dismiss()
                onAction(MusicSetMenuAction.PlayNext)
            }

            R.string.add_to_queue -> {
                dismiss()
                onAction(MusicSetMenuAction.AddToQueue)
            }

            R.string.add_to_list -> {
                dismiss()
                onAction(MusicSetMenuAction.AddToPlaylist)
            }

            R.string.add_to_home_screen -> {
                dismiss()
                onAction(MusicSetMenuAction.AddToHomeScreen)
            }

            R.string.rename -> {
                dismiss()
                onAction(MusicSetMenuAction.Rename)
            }

            R.string.dlg_manage_artwork -> {
                dismiss()
                onAction(MusicSetMenuAction.ManageArtwork)
            }

            R.string.list_delete -> {
                dismiss()
                onAction(MusicSetMenuAction.DeletePlaylist)
            }

            R.string.clear_favorite -> {
                dismiss()
                onAction(MusicSetMenuAction.ClearFavorites)
            }

            R.string.clear_recent_add -> {
                dismiss()
                onAction(MusicSetMenuAction.ClearRecentlyAdded)
            }

            R.string.clear_recent_play -> {
                dismiss()
                onAction(MusicSetMenuAction.ClearRecentlyPlayed)
            }

            R.string.clear_most_play -> {
                dismiss()
                onAction(MusicSetMenuAction.ClearMostPlayed)
            }

            R.string.list_backup -> {
                dismiss()
                onAction(MusicSetMenuAction.BackupPlaylists)
            }

            R.string.list_recovery -> {
                dismiss()
                onAction(MusicSetMenuAction.RestorePlaylists)
            }

            R.string.list_delete_empty -> {
                dismiss()
                onAction(MusicSetMenuAction.DeleteEmptyPlaylists)
            }
        }
    }

    private fun showViewModeMenu() {
        val subMenu = ViewAsContextMenu(
            context = context,
            selectedMode = selectedViewMode
        ) { mode ->
            val action = when (mode) {
                MusicSetAdapter.VIEW_MODE_GRID -> MusicSetMenuAction.ViewAsGrid
                else -> MusicSetMenuAction.ViewAsList
            }

            onAction(action)
        }

        dismiss()
        showAtLastPosition(subMenu)
    }

    private fun showSortMenu() {
        dismiss()

        showAtLastPosition(
            SortByContextMenu(
                context = context,
                musicSet = musicSet,
                onSortChanged = { sortKey, descending ->
                    onAction(
                        MusicSetMenuAction.SortChanged(
                            sortKey = sortKey,
                            descending = descending
                        )
                    )
                }
            )
        )
    }

    private fun MutableList<ContextMenuItem>.addTrackCollectionItems() {
        if (!musicSet.isTrackCollection || musicSet is MusicSet.Tracks) {
            return
        }

        if (musicSet.supportsRenameMenu) {
            addMenuItem(R.string.rename)
        }

        if (musicSet.supportsArtworkMenu) {
            addMenuItem(R.string.dlg_manage_artwork)
        }

        addMenuItem(R.string.add_to_queue)
        addMenuItem(R.string.add_to_list)
        addMenuItem(R.string.add_to_home_screen)

        when (musicSet) {
            is MusicSet.Favorites -> addMenuItem(R.string.clear_favorite)
            is MusicSet.RecentlyAdded -> addMenuItem(R.string.clear_recent_add)
            is MusicSet.Playlist -> addMenuItem(R.string.list_delete)
            is MusicSet.RecentlyPlayed -> addMenuItem(R.string.clear_recent_play)
            is MusicSet.MostPlayed -> addMenuItem(R.string.clear_most_play)
            else -> Unit
        }
    }

    private fun MutableList<ContextMenuItem>.addPlaylistRootItems() {
        if (musicSet !is MusicSet.Playlists) return

        addMenuItem(R.string.list_backup)
        addMenuItem(R.string.list_recovery)
        addMenuItem(R.string.list_delete_empty)
    }

    private fun MutableList<ContextMenuItem>.addMenuItem(
        titleRes: Int,
        showArrow: Boolean = false
    ) {
        add(
            ContextMenuItem(
                context.getString(titleRes),
                titleRes = titleRes,
                showArrow = showArrow
            )
        )
    }

    private val MusicSet.supportsSortMenu: Boolean
        get() {
            return this !is MusicSet.RecentlyPlayed &&
                    this !is MusicSet.MostPlayed
        }

    private val MusicSet.supportsRenameMenu: Boolean
        get() {
            return this is MusicSet.Playlist ||
                    this is MusicSet.Artist ||
                    this is MusicSet.Album ||
                    this is MusicSet.Genre
        }

    private val MusicSet.supportsArtworkMenu: Boolean
        get() {
            return this is MusicSet.Artist ||
                    this is MusicSet.Album ||
                    this is MusicSet.Genre
        }
}

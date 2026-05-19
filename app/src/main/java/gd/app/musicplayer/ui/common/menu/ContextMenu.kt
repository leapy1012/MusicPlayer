package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.supportsArtworkMenu
import gd.app.musicplayer.core.common.extension.supportsPlayNextMenu
import gd.app.musicplayer.core.common.extension.supportsRenameMenu
import gd.app.musicplayer.core.common.extension.supportsShuffleAllMenu
import gd.app.musicplayer.core.common.extension.supportsSortMenu
import gd.app.musicplayer.core.common.extension.supportsViewModeMenu
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.popupTitleColor
import gd.app.musicplayer.domain.model.ContextMenuItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.isTrackCollection
import gd.app.musicplayer.ui.library.musicset.MusicSetAdapter

class ContextMenu(
    context: Context,
    private val musicSet: MusicSet,
    private val theme: ThemePalette,
    private val onAction: (ContextMenuAction) -> Unit,
    private val selectedViewMode: Int = MusicSetAdapter.VIEW_MODE_LIST,
    private val currentSortStyle: String = "",
    private val currentSortDescending: Boolean = false
) : BaseContextMenu(
    context = context,
    accentColor = theme.accentColor,
    popupTextColor = theme.popupTitleColor,
    popupBackgroundProvider = { menuContext ->
        theme.getPopupBackgroundDrawable(menuContext)
    }
) {

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
                onAction(ContextMenuAction.Select)
            }

            R.string.shuffle_all -> {
                dismiss()
                onAction(ContextMenuAction.ShuffleAll)
            }

            R.string.view_as -> {
                showViewModeMenu()
            }

            R.string.sort_by -> {
                showSortMenu()
            }

            R.string.play_next -> {
                dismiss()
                onAction(ContextMenuAction.PlayNext)
            }

            R.string.add_to_queue -> {
                dismiss()
                onAction(ContextMenuAction.AddToQueue)
            }

            R.string.add_to_list -> {
                dismiss()
                onAction(ContextMenuAction.AddToPlaylist)
            }

            R.string.add_to_home_screen -> {
                dismiss()
                onAction(ContextMenuAction.AddToHomeScreen)
            }

            R.string.rename -> {
                dismiss()
                onAction(ContextMenuAction.Rename)
            }

            R.string.dlg_manage_artwork -> {
                dismiss()
                onAction(ContextMenuAction.ManageArtwork)
            }

            R.string.list_delete -> {
                dismiss()
                onAction(ContextMenuAction.DeletePlaylist)
            }

            R.string.clear_favorite -> {
                dismiss()
                onAction(ContextMenuAction.ClearFavorites)
            }

            R.string.clear_recent_add -> {
                dismiss()
                onAction(ContextMenuAction.ClearRecentlyAdded)
            }

            R.string.clear_recent_play -> {
                dismiss()
                onAction(ContextMenuAction.ClearRecentlyPlayed)
            }

            R.string.clear_most_play -> {
                dismiss()
                onAction(ContextMenuAction.ClearMostPlayed)
            }

            R.string.list_backup -> {
                dismiss()
                onAction(ContextMenuAction.BackupPlaylists)
            }

            R.string.list_recovery -> {
                dismiss()
                onAction(ContextMenuAction.RestorePlaylists)
            }

            R.string.list_delete_empty -> {
                dismiss()
                onAction(ContextMenuAction.DeleteEmptyPlaylists)
            }
        }
    }

    private fun showViewModeMenu() {
        val subMenu = ViewAsContextMenu(
            context = context,
            selectedMode = selectedViewMode,
            accentColor = theme.accentColor,
            popupTextColor = theme.popupTitleColor,
            popupBackgroundProvider = { menuContext ->
                theme.getPopupBackgroundDrawable(menuContext)
            }
        ) { mode ->
            val action = when (mode) {
                MusicSetAdapter.VIEW_MODE_GRID -> ContextMenuAction.ViewAsGrid
                else -> ContextMenuAction.ViewAsList
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
                currentSortStyle = currentSortStyle,
                currentSortDescending = currentSortDescending,
                accentColor = theme.accentColor,
                popupTextColor = theme.popupTitleColor,
                popupBackgroundProvider = { menuContext ->
                    theme.getPopupBackgroundDrawable(menuContext)
                },
                onSortChanged = { sortKey, descending ->
                    onAction(
                        ContextMenuAction.SortChanged(
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
}

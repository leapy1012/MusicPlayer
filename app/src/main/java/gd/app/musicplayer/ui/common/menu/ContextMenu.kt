package gd.app.musicplayer.ui.common.menu

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.PopupWindow
import androidx.appcompat.content.res.AppCompatResources
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
 *
 * Submenus ("View as", "Sort by") are shown as a **second** popup over the parent so the
 * parent stays visible (Oppo-style). Phone COUI's built-in submenu replaces the parent
 * in-place, which is why we do not attach [PopupListItem.setSubMenuItemList].
 *
 * The overlay submenu must be anchored to an **activity-rooted** view (the original
 * overflow host), never to a row inside the parent popup — nesting [PopupWindow] tokens
 * throws [WindowManager.BadTokenException].
 */
class ContextMenu(
    private val context: Context,
    private val musicSet: MusicSet,
    private val theme: ThemePalette,
    private val onAction: (ContextMenuAction) -> Unit,
    private val selectedViewMode: Int = MusicSetAdapter.VIEW_MODE_LIST,
    private val currentSortStyle: String = "",
    private val currentSortDescending: Boolean = false
) {

    private var popup: COUIPopupListWindow? = null
    private var subPopup: COUIPopupListWindow? = null
    /** Activity-window anchor from [show]; used for overlay submenus (valid window token). */
    private var hostAnchor: View? = null
    private var items: ArrayList<PopupListItem> = ArrayList()
    private val subMenus = LinkedHashMap<Int, ArrayList<PopupListItem>>()
    private var openSubMenuParentId: Int = -1

    fun show(anchor: View) {
        dismiss()

        items = buildItems()
        if (items.isEmpty()) return

        hostAnchor = anchor
        CouiPopupListSurface.paintItemTitles(context, items, theme)
        subMenus.values.forEach { CouiPopupListSurface.paintItemTitles(context, it, theme) }

        val window = COUIPopupListWindow(
            CouiPopupListSurface.popupContext(context, theme)
        ).also { popup = it }
        window.setItemList(items)
        window.setOnItemClickListener(AdapterView.OnItemClickListener { _, itemView, position, _ ->
            val item = items.getOrNull(position) ?: return@OnItemClickListener
            val subItems = subMenus[item.id]
            if (subItems != null) {
                openSubMenuParentId = item.id
                showOverlaySubMenu(itemView, subItems)
                return@OnItemClickListener
            }
            dispatchMainAction(item.id)
            dismiss()
        })
        window.setOnDismissListener(
            PopupWindow.OnDismissListener {
                // Parent dismissed (outside tap / back) — drop any open overlay submenu.
                if (popup === window) {
                    dismissSubMenuOnly()
                    popup = null
                    openSubMenuParentId = -1
                }
            }
        )
        CouiPopupListSurface.apply(window, context, theme.getDialogSurfaceDrawable(context))
        window.show(anchor)
        anchor.post {
            if (popup === window) {
                CouiPopupListSurface.apply(
                    window,
                    context,
                    theme.getDialogSurfaceDrawable(context)
                )
            }
        }
    }

    fun dismiss() {
        dismissSubMenuOnly()
        popup?.setOnDismissListener(null)
        popup?.dismiss()
        popup = null
        hostAnchor = null
        openSubMenuParentId = -1
    }

    private fun dismissSubMenuOnly() {
        val parent = popup
        subPopup?.setOnDismissListener(null)
        subPopup?.dismiss()
        subPopup = null
        restoreParentAfterSubMenu(parent)
    }

    private fun showOverlaySubMenu(itemView: View, subItems: ArrayList<PopupListItem>) {
        val parent = popup ?: return
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        // Must use an activity-rooted token. itemView lives in the parent PopupWindow;
        // COUI show() → showAtLocation(itemView.rootView) would BadToken.
        val stableAnchor = resolveStableSubMenuAnchor(activity) ?: return

        dismissSubMenuOnly()
        prepareParentForSubMenu(parent)

        val window = COUIPopupListWindow(
            CouiPopupListSurface.popupContext(context, theme)
        ).also { subPopup = it }
        window.setItemList(subItems)
        window.setOnItemClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
            handleSubMenuClick(position)
            dismiss()
        })
        window.setOnDismissListener(
            PopupWindow.OnDismissListener {
                if (subPopup === window) {
                    subPopup = null
                    restoreParentAfterSubMenu(popup)
                }
            }
        )
        CouiPopupListSurface.apply(window, context, theme.getDialogSurfaceDrawable(context))

        val itemLoc = IntArray(2)
        val hostLoc = IntArray(2)
        itemView.getLocationOnScreen(itemLoc)
        stableAnchor.getLocationOnScreen(hostLoc)
        val offsetX = itemLoc[0] - hostLoc[0]
        val offsetY = itemLoc[1] - hostLoc[1]

        try {
            window.show(stableAnchor, offsetX, offsetY)
        } catch (_: WindowManager.BadTokenException) {
            subPopup = null
            restoreParentAfterSubMenu(parent)
            return
        }

        stableAnchor.post {
            if (subPopup === window) {
                CouiPopupListSurface.apply(
                    window,
                    context,
                    theme.getDialogSurfaceDrawable(context)
                )
            }
        }
    }

    private fun resolveStableSubMenuAnchor(activity: Activity): View? {
        val host = hostAnchor
        if (host != null && host.isAttachedToWindow && host.windowToken != null) {
            return host
        }
        val decor = activity.window?.decorView ?: return null
        return decor.takeIf { it.windowToken != null }
    }

    private fun prepareParentForSubMenu(parent: COUIPopupListWindow) {
        parent.isFocusable = false
        parent.setDismissTouchOutside(false)
        parent.update()
    }

    private fun restoreParentAfterSubMenu(parent: COUIPopupListWindow?) {
        if (parent == null || !parent.isShowing) return
        parent.isFocusable = true
        parent.setDismissTouchOutside(true)
        parent.update()
    }

    private fun buildItems(): ArrayList<PopupListItem> {
        val list = ArrayList<PopupListItem>()
        subMenus.clear()

        list += item(ID_SELECT, R.string.select)

        if (musicSet.supportsShuffleAllMenu) {
            list += item(ID_SHUFFLE_ALL, R.string.shuffle_all)
        }

        if (musicSet.supportsViewModeMenu) {
            list += branchItem(ID_VIEW_AS, R.string.view_as, viewAsSubItems())
        }

        if (musicSet.supportsPlayNextMenu) {
            list += item(ID_PLAY_NEXT, R.string.play_next)
        }

        if (musicSet.supportsSortMenu) {
            list += branchItem(ID_SORT_BY, R.string.sort_by, sortSubItems())
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
        val subItems = subMenus[openSubMenuParentId] ?: return
        val clicked = subItems.getOrNull(position) ?: return

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

    private fun item(id: Int, titleRes: Int): PopupListItem {
        return PopupListItem.Builder()
            .setId(id)
            .setTitle(context.getString(titleRes))
            .setIsEnable(true)
            .build()
    }

    /** Parent row that opens an overlay submenu — chevron only, no COUI built-in sub list. */
    private fun branchItem(
        id: Int,
        titleRes: Int,
        subItems: ArrayList<PopupListItem>
    ): PopupListItem {
        subMenus[id] = subItems
        val arrow = AppCompatResources.getDrawable(
            context,
            com.coui.appcompat.R.drawable.coui_btn_next
        )
        return PopupListItem.Builder()
            .setId(id)
            .setTitle(context.getString(titleRes))
            .setIsEnable(true)
            .apply {
                if (arrow != null) {
                    setOperateIcon(arrow)
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

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
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

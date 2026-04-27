package gd.app.musicplayer.feature.selection

import android.content.Context
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.ui.feature.menu.BaseContextMenu
import gd.app.musicplayer.core.ui.extension.dpToPx

class SelectionMoreContextMenu(
    private val context: Context,
    private val onItemClick: (ContextMenuItem) -> Unit
) : BaseContextMenu(context) {

    private val items: List<ContextMenuItem> = listOf(
        ContextMenuItem(
            id = ACTION_DELETE,
            titleRes = R.string.delete
        ),
        ContextMenuItem(
            id = ACTION_HIDE,
            titleRes = R.string.hide_music
        ),
        ContextMenuItem(
            id = ACTION_SHARE,
            titleRes = R.string.share
        )
    )

    override fun buildItems(): List<ContextMenuItem> = items

    override fun onItemClicked(item: ContextMenuItem, anchor: View) {
        dismiss()
        onItemClick(item)
    }

    fun showAbove(anchor: View) {
        val popupHeightEstimate = context.dpToPx(48f) * items.size
        show(anchor, 0, -(anchor.height + popupHeightEstimate))
    }

    private companion object {
        const val ACTION_DELETE = "delete"
        const val ACTION_HIDE = "hide"
        const val ACTION_SHARE = "share"
    }
}

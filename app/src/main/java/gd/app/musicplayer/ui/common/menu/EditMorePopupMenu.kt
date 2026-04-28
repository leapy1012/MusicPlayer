package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.data.model.MenuItemModel
import gd.app.musicplayer.ui.common.menu.BaseContextMenu

class EditMorePopupMenu(
    context: Context,
    private val items: List<MenuItemModel>,
    private val itemClickListener: OnItemClickListener<MenuItemModel>
) : BaseContextMenu(context) {

    override fun buildItems(): List<ContextMenuItem> {
        return items.mapIndexed { index, item ->
            ContextMenuItem(
                id = index.toString(),
                titleRes = item.getTitleResId(),
                leftIconRes = item.getIconResId().takeIf { it != 0 }
            )
        }
    }

    override fun onItemClicked(item: ContextMenuItem, anchor: View) {
        val index = item.id.toIntOrNull() ?: return
        val selectedItem = items.getOrNull(index) ?: return
        dismiss()
        itemClickListener.onItemClick(selectedItem, anchor, index)
    }
}

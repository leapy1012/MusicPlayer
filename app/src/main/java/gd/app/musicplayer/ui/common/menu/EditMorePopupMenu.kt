package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import gd.app.musicplayer.domain.model.MenuItemModel

class EditMorePopupMenu(
    private val context: Context,
    private val items: List<MenuItemModel>,
    private val itemClickListener: OnItemClickListener<MenuItemModel>
) {

    private var popup: COUIPopupListWindow? = null

    fun show(anchor: View) {
        dismiss()
        if (items.isEmpty()) return

        val popupItems = ArrayList(
            items.mapIndexed { index, item ->
                PopupListItem.Builder()
                    .setId(index)
                    .setTitle(item.getTitle(context))
                    .setIsEnable(true)
                    .build()
            }
        )
        val window = COUIPopupListWindow(context).also { popup = it }
        window.setItemList(popupItems)
        window.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = items.getOrNull(position) ?: return@setOnItemClickListener
            dismiss()
            itemClickListener.onItemClick(selectedItem, anchor, position)
        }
        window.show(anchor)
        CouiPopupListSurface.apply(window, context)
        anchor.post {
            if (popup === window) CouiPopupListSurface.apply(window, context)
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}

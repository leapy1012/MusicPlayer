package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ContextMenuItem

class ViewAsContextMenu(
    context: Context,
    private val selectedMode: Int,
    private val accentColor: Int,
    private val popupTextColor: Int,
    private val popupBackgroundProvider: (Context) -> Drawable,
    private val onModeSelected: (Int) -> Unit
) : BaseContextMenu(context, accentColor, popupTextColor, popupBackgroundProvider) {

    companion object {
        private const val ID_LIST_ITEM = "list_item"
        private const val ID_GRID_ITEM = "grid_item"
    }

    override fun buildItems(): List<ContextMenuItem> {
        return listOf(
            ContextMenuItem(
                id = ID_LIST_ITEM,
                titleRes = R.string.view_as_list,
                rightIconRes = if (selectedMode == 0) R.drawable.b_vector_menu_single_selected else null,
                selected = selectedMode == 0
            ),
            ContextMenuItem(
                id = ID_GRID_ITEM,
                titleRes = R.string.view_as_grid,
                rightIconRes = if (selectedMode == 1) R.drawable.b_vector_menu_single_selected else null,
                selected = selectedMode == 1
            )
        )
    }

    override fun onItemClicked(item: ContextMenuItem, anchor: View) {
        dismiss()
        onModeSelected(if (item.id == ID_GRID_ITEM) 1 else 0)
    }
}

package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.ListItem

abstract class BaseViewHolder(itemViewBinding: View) :
    RecyclerView.ViewHolder(itemViewBinding) {

    fun bind(
        item: ListItem,
        selected: Boolean = false,
        viewInfo: String = ""
    ) {
        onBind(
            item = item,
            selected = selected,
            viewInfo = viewInfo
        )
    }
    protected abstract fun onBind(item: ListItem, selected: Boolean, viewInfo: String)
}

package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ContextMenuItem

class ContextMenuAdapter(
    context: Context,
    private val items: List<ContextMenuItem>,
    private val accentColor: Int,
    private val popupTextColor: Int
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): ContextMenuItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(
            R.layout.b_popupwindow_list_item,
            parent,
            false
        )

        val leftIcon = view.findViewById<ImageView>(R.id.b_popup_left_icon)
        val text = view.findViewById<TextView>(R.id.b_popup_text)
        val rightIcon = view.findViewById<ImageView>(R.id.b_popup_right_icon)
        val arrow = view.findViewById<ImageView>(R.id.b_popup_arrow)

        val item = getItem(position)

        text.setText(item.titleRes)
        text.setTextColor(popupTextColor)
        text.alpha = if (item.enabled) 1f else 0.5f

        if (item.leftIconRes != null) {
            leftIcon.isVisible = true
            leftIcon.setImageResource(item.leftIconRes)
        } else {
            leftIcon.isVisible = false
        }

        if (item.rightIconRes != null) {
            rightIcon.isVisible = true
            rightIcon.setImageResource(item.rightIconRes)
            rightIcon.setColorFilter(accentColor)
            rightIcon.isSelected = item.selected
        } else {
            rightIcon.isVisible = false
            rightIcon.clearColorFilter()
        }

        arrow.isVisible = item.showArrow
        if (item.showArrow) {
            arrow.setColorFilter(accentColor)
        } else {
            arrow.clearColorFilter()
        }
        view.isEnabled = item.enabled
        return view
    }
}

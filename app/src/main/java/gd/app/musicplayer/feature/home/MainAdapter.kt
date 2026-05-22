package gd.app.musicplayer.feature.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.databinding.FragmentMainItemBinding

class MainAdapter(
    private val onItemClick: (MainItem) -> Unit
) : BaseAdapter() {

    private var items: List<MainItem> = emptyList()

    override fun hasStableIds(): Boolean = true

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): MainItem = items[position]

    override fun getItemId(position: Int): Long = getItem(position).category.ordinal.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = if (convertView == null) {
            ViewHolder(
                FragmentMainItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            convertView.tag as ViewHolder
        }

        holder.bind(getItem(position), onItemClick)
        return holder.itemView
    }

    fun submitList(newItems: List<MainItem>) {
        if (items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    private class ViewHolder(
        private val binding: FragmentMainItemBinding
    ) {
        val itemView: View = binding.root

        init {
            binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
            binding.root.tag = this
        }

        fun bind(
            item: MainItem,
            onItemClick: (MainItem) -> Unit
        ) = with(binding) {
            mainItemImage.setImageResource(item.iconRes)
            mainItemName.setText(item.titleRes)
            mainItemCount.text = item.count.toString()
            root.background = item.bgColor.asRippleBackground()
            root.setOnClickListener { onItemClick(item) }
        }
    }

    private companion object {
        val RIPPLE_COLOR: ColorStateList = ColorStateList.valueOf(0x33FFFFFF)

        fun Int.asRippleBackground(): RippleDrawable {
            return RippleDrawable(
                RIPPLE_COLOR,
                this.toDrawable(),
                ShapeDrawable(RectShape()).apply {
                    paint.color = Color.WHITE
                }
            )
        }
    }
}

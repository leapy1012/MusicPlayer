package gd.app.musicplayer.feature.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.widget.ImageViewCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.databinding.FragmentMainItemBinding

class MainAdapter(
    private val onItemClick: (MainItem) -> Unit,
    private val applyTheme: (View) -> Unit = {}
) : BaseAdapter() {

    private var items: List<MainItem> = emptyList()

    private companion object {
        /** Keyed tag so XML [android:tag] (theme binder) is not overwritten. */
        private val HOLDER_KEY = R.id.main_item_image
    }

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
            convertView.getTag(HOLDER_KEY) as ViewHolder
        }

        holder.bind(getItem(position), onItemClick, applyTheme)
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
            binding.root.applyRoundedOutline(com.coui.appcompat.R.dimen.coui_round_corner_m)
            binding.root.setTag(HOLDER_KEY, this)
        }

        fun bind(
            item: MainItem,
            onItemClick: (MainItem) -> Unit,
            applyTheme: (View) -> Unit
        ) = with(binding) {
            mainItemImage.setImageResource(item.iconRes)
            ImageViewCompat.setImageTintList(
                mainItemImage,
                ColorStateList.valueOf(item.bgColor)
            )
            mainItemName.setText(item.titleRes)
            mainItemCount.text = item.count.toString()
            root.setOnClickListener { onItemClick(item) }
            applyTheme(root)
        }
    }
}

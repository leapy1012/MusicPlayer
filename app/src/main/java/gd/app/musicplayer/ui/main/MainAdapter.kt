package gd.app.musicplayer.ui.main

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.applyRoundedOutline
import gd.app.musicplayer.databinding.FragmentMainItemBinding

class MainAdapter(
    private var items: List<MainItem>,
    private val onItemClick: (MainItem) -> Unit
) : BaseAdapter() {
    private val rippleColor = ColorStateList.valueOf(872415231)
    private val mask = ShapeDrawable(RectShape())
    private val backgroundCache = hashMapOf<Int, RippleDrawable>()

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {

        val binding: FragmentMainItemBinding

        if (convertView == null) {
            binding =
                FragmentMainItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

            binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
            binding.root.tag = binding


        } else {
            binding = convertView.tag as FragmentMainItemBinding
        }

        val item = getItem(position)

        binding.apply {

            mainItemImage.setImageResource(item.iconRes)
            mainItemName.setText(item.titleRes)
            mainItemCount.text = item.count.toString()

            root.background = backgroundFor(item.bgColor)
        }

        binding.root.setOnClickListener {
            onItemClick(item)
        }

        return binding.root

    }

    fun submitList(newItems: List<MainItem>) {
        if (items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    private fun backgroundFor(color: Int): RippleDrawable {
        val prototype = backgroundCache[color] ?: RippleDrawable(
            rippleColor,
            color.toDrawable(),
            mask
        ).also { drawable ->
            backgroundCache[color] = drawable
        }
        return (prototype.constantState?.newDrawable()?.mutate() as? RippleDrawable)
            ?: RippleDrawable(rippleColor, color.toDrawable(), mask)
    }
}

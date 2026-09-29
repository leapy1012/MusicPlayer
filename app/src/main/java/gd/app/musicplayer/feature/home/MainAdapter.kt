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
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.ImageViewCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.databinding.FragmentMainItemBinding

class MainAdapter(
    private val onItemClick: (MainItem) -> Unit,
    private val isPictureTheme: () -> Boolean = { false },
    private val applyTheme: (View) -> Unit = {}
) : BaseAdapter() {

    private var items: List<MainItem> = emptyList()

    private companion object {
        /** Keyed tag so XML [android:tag] (theme binder) is not overwritten. */
        private val HOLDER_KEY = R.id.main_item_image
        private val RIPPLE_COLOR: ColorStateList = ColorStateList.valueOf(0x33FFFFFF)

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

        holder.bind(getItem(position), onItemClick, isPictureTheme(), applyTheme)
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
            binding.root.setTag(HOLDER_KEY, this)
        }

        fun bind(
            item: MainItem,
            onItemClick: (MainItem) -> Unit,
            pictureTheme: Boolean,
            applyTheme: (View) -> Unit
        ) = with(binding) {
            mainItemImage.setImageResource(item.iconRes)
            mainItemName.setText(item.titleRes)
            mainItemCount.text = item.count.toString()

            if (pictureTheme) {
                root.applyRoundedOutline(R.dimen.item_image_corner_radius)
                ImageViewCompat.setImageTintList(mainItemImage, null)
                mainItemName.setTextColor(Color.WHITE)
                mainItemCount.setTextColor(Color.WHITE)
                root.background = item.bgColor.asRippleBackground()
            } else {
                root.applyRoundedOutline(com.coui.appcompat.R.dimen.coui_round_corner_m)
                ImageViewCompat.setImageTintList(
                    mainItemImage,
                    ColorStateList.valueOf(item.bgColor)
                )
                val typed = root.context.obtainStyledAttributes(
                    intArrayOf(
                        com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
                        com.coui.appcompat.R.attr.couiColorSecondNeutral,
                    )
                )
                mainItemName.setTextColor(typed.getColor(0, Color.BLACK))
                mainItemCount.setTextColor(typed.getColor(1, Color.GRAY))
                typed.recycle()
                root.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.bg_main_home_card_ripple,
                )
            }

            root.setOnClickListener { onItemClick(item) }
            applyTheme(root)
        }
    }
}

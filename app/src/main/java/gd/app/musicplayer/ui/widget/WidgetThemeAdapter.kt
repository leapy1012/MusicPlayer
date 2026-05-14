package gd.app.musicplayer.ui.widget

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.databinding.ActivityWidgetConfigThemeItemBinding

internal class WidgetThemeAdapter(
    private val items: List<WidgetThemeOption>,
    private val applyTheme: (View) -> Unit,
    private val onSelected: (WidgetThemeOption) -> Unit
) : RecyclerView.Adapter<WidgetThemeAdapter.ViewHolder>() {

    private var selected: WidgetThemeOption? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = items[position]
        return "${item.themeType}:${item.index}".hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ActivityWidgetConfigThemeItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyTheme(binding.root)
        return ViewHolder(binding, onSelected)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val item = items[position]
        holder.bind(item, item == selected)
    }

    override fun getItemCount(): Int = items.size

    fun submitSelection(value: WidgetThemeOption?) {
        if (selected == value) return

        val oldIndex = items.indexOf(selected)
        val newIndex = items.indexOf(value)
        selected = value

        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    class ViewHolder(
        private val binding: ActivityWidgetConfigThemeItemBinding,
        private val onSelected: (WidgetThemeOption) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: WidgetThemeOption,
            isSelected: Boolean
        ) {

            val context = binding.itemImage.context
            if ((context as? Activity)?.isDestroyed == true) return

            val cornerRadiusPx = context.dpToPx(6f)
            if (item.themeType == 0) {
                val drawable = ContextCompat.getDrawable(
                    binding.root.context,
                    item.drawableRes
                )?.mutate()

                val roundedDrawable =
                    if (drawable is GradientDrawable) {
                        drawable.apply {
                            cornerRadius = cornerRadiusPx.toFloat()
                        }
                    } else {
                        drawable
                    }

                binding.itemImage.setImageDrawable(roundedDrawable)
            } else {
                Glide.with(context).load(item.drawableRes).transform(RoundedCorners(cornerRadiusPx))
                    .into(binding.itemImage)
            }
            binding.itemImage.imageAlpha = (item.alpha * 255f).toInt().coerceIn(0, 255)

            binding.itemSelect.imageTintList = ColorStateList.valueOf(
                if (item.shouldUseDarkForeground()) {
                    -16777216
                } else {
                    Color.WHITE
                }
            )
            binding.itemSelect.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onSelected(item) }
        }
    }
}

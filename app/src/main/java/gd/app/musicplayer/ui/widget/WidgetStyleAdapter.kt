package gd.app.musicplayer.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivityWidgetConfigStyleItemBinding

internal class WidgetStyleAdapter(
    private val applyTheme: (View) -> Unit,
    private val onSelected: (WidgetStyleOption) -> Unit
) : RecyclerView.Adapter<WidgetStyleAdapter.ViewHolder>() {

    private var items: List<WidgetStyleOption> = emptyList()
    private var classify: String = "4*1"
    private var selected: WidgetStyleOption? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].styleKey.hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ActivityWidgetConfigStyleItemBinding.inflate(
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
        holder.bind(item, item == selected, classify)
    }

    override fun getItemCount(): Int = items.size

    fun setItems(
        items: List<WidgetStyleOption>,
        classify: String
    ) {
        this.items = items
        this.classify = classify
        notifyDataSetChanged()
    }

    fun submitSelection(value: WidgetStyleOption?) {
        if (selected == value) return

        val oldIndex = items.indexOf(selected)
        val newIndex = items.indexOf(value)
        selected = value

        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    class ViewHolder(
        private val binding: ActivityWidgetConfigStyleItemBinding,
        private val onSelected: (WidgetStyleOption) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: WidgetStyleOption,
            isSelected: Boolean,
            classify: String
        ) {
            binding.itemImage.setImageResource(item.previewRes)
            binding.itemSelect.visibility = if (isSelected) View.VISIBLE else View.GONE
            applyPreviewSize(classify)
            binding.root.setOnClickListener { onSelected(item) }
        }

        private fun applyPreviewSize(classify: String) {
            val resources = binding.root.resources
            val params = binding.itemContent.layoutParams as ViewGroup.MarginLayoutParams

            val height = when (classify) {
                "2*1" -> resources.getDimensionPixelSize(R.dimen.widget_config_style_h_2x1)
                "3*2" -> resources.getDimensionPixelSize(R.dimen.widget_config_style_h_3x2)
                "4*1" -> resources.getDimensionPixelSize(R.dimen.widget_config_style_h_4x1)
                "4*2" -> resources.getDimensionPixelSize(R.dimen.widget_config_style_h_4x2)
                "4*3" -> resources.getDimensionPixelSize(R.dimen.widget_config_style_h_4x3)
                else -> resources.getDimensionPixelSize(R.dimen.widget_config_style_h_4x4)
            }

            val width = when (classify) {
                "2*1" -> height * 2
                "3*2" -> (height * 1.38f).toInt()
                "4*1" -> (height * 4.286f).toInt()
                "4*2" -> (height * 2.26f).toInt()
                "4*3" -> (height * 1.38f).toInt()
                else -> height
            }

            val margin = if (classify == "4*1") {
                resources.getDimensionPixelSize(R.dimen.widget_config_content_margin_start)
            } else {
                0
            }

            params.width = width
            params.height = height
            params.leftMargin = margin
            params.topMargin = margin
            params.rightMargin = margin
            params.bottomMargin = margin
            binding.itemContent.layoutParams = params
        }
    }
}

package gd.app.musicplayer.feature.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivityWidgetConfigStyleItemBinding
import kotlin.math.roundToInt

internal class WidgetStyleAdapter(
    private val applyTheme: (View) -> Unit,
    private val onSelected: (WidgetStyleOption) -> Unit
) : ListAdapter<WidgetStyleOption, WidgetStyleAdapter.ViewHolder>(DiffCallback) {

    private var classify: String = DEFAULT_CLASSIFY
    private var selectedStyleKey: String? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).styleKey.hashCode().toLong()
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

        return ViewHolder(
            binding = binding,
            onSelected = onSelected
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val item = getItem(position)

        holder.bind(
            item = item,
            isSelected = item.styleKey == selectedStyleKey,
            classify = classify
        )
    }

    fun submitItems(
        items: List<WidgetStyleOption>,
        classify: String
    ) {
        this.classify = classify
        submitList(items)
    }

    fun submitSelection(value: WidgetStyleOption?) {
        val oldStyleKey = selectedStyleKey
        val newStyleKey = value?.styleKey

        if (oldStyleKey == newStyleKey) return

        selectedStyleKey = newStyleKey

        notifyStyleChanged(oldStyleKey)
        notifyStyleChanged(newStyleKey)
    }

    private fun notifyStyleChanged(styleKey: String?) {
        if (styleKey == null) return

        val index = currentList.indexOfFirst { item ->
            item.styleKey == styleKey
        }

        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    internal class ViewHolder(
        private val binding: ActivityWidgetConfigStyleItemBinding,
        private val onSelected: (WidgetStyleOption) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: WidgetStyleOption,
            isSelected: Boolean,
            classify: String
        ) = with(binding) {
            itemImage.setImageResource(item.previewRes)
            itemSelect.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemContent.applyPreviewSize(classify)

            root.setOnClickListener {
                onSelected(item)
            }
        }

        private fun View.applyPreviewSize(classify: String) {
            val sizeSpec = WidgetStylePreviewSize.from(classify)
            val height = resources.getDimensionPixelSize(sizeSpec.heightRes)
            val width = (height * sizeSpec.widthRatio).roundToInt()
            val margin = if (sizeSpec.hasOuterMargin) {
                resources.getDimensionPixelSize(R.dimen.widget_config_content_margin_start)
            } else {
                0
            }

            updateMarginLayoutParams {
                this.width = width
                this.height = height
                setMargins(margin, margin, margin, margin)
            }
        }

        private inline fun View.updateMarginLayoutParams(
            block: ViewGroup.MarginLayoutParams.() -> Unit
        ) {
            val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
            params.block()
            layoutParams = params
        }
    }

    private data class WidgetStylePreviewSize(
        val heightRes: Int,
        val widthRatio: Float,
        val hasOuterMargin: Boolean = false
    ) {
        companion object {
            fun from(classify: String): WidgetStylePreviewSize {
                return when (classify) {
                    "2*1" -> WidgetStylePreviewSize(
                        heightRes = R.dimen.widget_config_style_h_2x1,
                        widthRatio = 2f
                    )

                    "3*2" -> WidgetStylePreviewSize(
                        heightRes = R.dimen.widget_config_style_h_3x2,
                        widthRatio = 1.38f
                    )

                    "4*1" -> WidgetStylePreviewSize(
                        heightRes = R.dimen.widget_config_style_h_4x1,
                        widthRatio = 4.286f,
                        hasOuterMargin = true
                    )

                    "4*2" -> WidgetStylePreviewSize(
                        heightRes = R.dimen.widget_config_style_h_4x2,
                        widthRatio = 2.26f
                    )

                    "4*3" -> WidgetStylePreviewSize(
                        heightRes = R.dimen.widget_config_style_h_4x3,
                        widthRatio = 1.38f
                    )

                    else -> WidgetStylePreviewSize(
                        heightRes = R.dimen.widget_config_style_h_4x4,
                        widthRatio = 1f
                    )
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<WidgetStyleOption>() {
        override fun areItemsTheSame(
            oldItem: WidgetStyleOption,
            newItem: WidgetStyleOption
        ): Boolean {
            return oldItem.styleKey == newItem.styleKey
        }

        override fun areContentsTheSame(
            oldItem: WidgetStyleOption,
            newItem: WidgetStyleOption
        ): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        const val DEFAULT_CLASSIFY = "4*1"
    }
}
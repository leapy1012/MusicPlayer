package gd.app.musicplayer.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.databinding.ActivityWidgetItemBinding

internal class WidgetSizeAdapter(
    private val items: List<WidgetProviderSpec>,
    private val applyTheme: (View) -> Unit,
    private val onAddClicked: (WidgetProviderSpec) -> Unit
) : RecyclerView.Adapter<WidgetSizeAdapter.ViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ActivityWidgetItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyTheme(binding.root)
        return ViewHolder(binding, onAddClicked)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ActivityWidgetItemBinding,
        private val onAddClicked: (WidgetProviderSpec) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WidgetProviderSpec) = with(binding) {
            itemTitle.setText(item.titleRes)
            itemSize.text = root.context.getString(R.string.size) + ": " + item.classify
            itemImage.setImageResource(item.previewRes)
            applyPreviewScale(itemImage, item.classify)

            root.setOnClickListener { onAddClicked(item) }
            itemAdd.setOnClickListener { onAddClicked(item) }
        }

        private fun applyPreviewScale(
            imageView: ImageView,
            classify: String
        ) {
            imageView.adjustViewBounds = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.maxHeight = when {
                classify.endsWith("1") -> imageView.context.dpToPx(64f)
                classify.endsWith("2") -> imageView.context.dpToPx(80f)
                else -> Int.MAX_VALUE
            }
        }
    }
}

package gd.app.musicplayer.feature.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import gd.app.musicplayer.data.model.ThemeItem
import gd.app.musicplayer.databinding.FragmentThemeItemAddBinding
import gd.app.musicplayer.databinding.FragmentThemeItemBinding
import java.io.File

class ThemeItemAdapter(
    private val tabIndex: Int,
    private val onThemeSelected: (String) -> Unit,
    private val onAddClicked: () -> Unit
) : RecyclerView.Adapter<ThemeItemAdapter.BaseViewHolder>() {

    private val hasAddCell = tabIndex == 0
    private val headerOffset: Int
        get() = if (hasAddCell) 1 else 0

    private val differ = AsyncListDiffer(
        HeaderAwareListUpdateCallback(),
        AsyncDifferConfig.Builder(ThemeDiffCallback).build()
    )

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = differ.currentList.size + headerOffset

    override fun getItemViewType(position: Int): Int {
        return if (hasAddCell && position == 0) VIEW_TYPE_ADD else VIEW_TYPE_THEME
    }

    override fun getItemId(position: Int): Long {
        return if (hasAddCell && position == 0) {
            ADD_CELL_ID
        } else {
            getThemeItem(position).id
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ADD -> AddViewHolder(
                FragmentThemeItemAddBinding.inflate(inflater, parent, false),
                onAddClicked
            )

            else -> ThemeViewHolder(
                FragmentThemeItemBinding.inflate(inflater, parent, false),
                ::getThemeItem,
                onThemeSelected,
                headerOffset
            )
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind()
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            holder.bind()
        } else {
            holder.bind(payloads)
        }
    }

    fun submitList(items: List<ThemeItem>) {
        differ.submitList(items)
    }

    private fun getThemeItem(adapterPosition: Int): ThemeItem {
        return differ.currentList[adapterPosition - headerOffset]
    }

    abstract class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind()
        open fun bind(payloads: List<Any>) = bind()
    }

    private class AddViewHolder(
        binding: FragmentThemeItemAddBinding,
        onAddClicked: () -> Unit
    ) : BaseViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { onAddClicked() }
        }

        override fun bind() = Unit
    }

    private class ThemeViewHolder(
        private val binding: FragmentThemeItemBinding,
        private val getThemeItem: (Int) -> ThemeItem,
        private val onThemeSelected: (String) -> Unit,
        private val headerOffset: Int
    ) : BaseViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position < headerOffset) return@setOnClickListener
                onThemeSelected(getThemeItem(position).fileName)
            }
        }

        override fun bind() {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || position < headerOffset) return

            val item = getThemeItem(position)
            bindImage(item)
            bindSelection(item.isSelected)
        }

        override fun bind(payloads: List<Any>) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || position < headerOffset) return

            val item = getThemeItem(position)

            if (payloads.any { it == PAYLOAD_SELECTION }) {
                bindSelection(item.isSelected)
            } else {
                bind()
            }
        }

        private fun bindImage(item: ThemeItem) {
            val source = if (File(item.fileName).isAbsolute) {
                File(item.fileName)
            } else {
                "file:///android_asset/${item.fileName}"
            }

            Glide.with(binding.themeImage)
                .load(source)
                .into(binding.themeImage)
        }

        private fun bindSelection(isSelected: Boolean) {
            binding.themeCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    private object ThemeDiffCallback : DiffUtil.ItemCallback<ThemeItem>() {
        override fun areItemsTheSame(oldItem: ThemeItem, newItem: ThemeItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ThemeItem, newItem: ThemeItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: ThemeItem, newItem: ThemeItem): Any? {
            return if (
                oldItem.isSelected != newItem.isSelected &&
                oldItem.fileName == newItem.fileName &&
                oldItem.id == newItem.id
            ) {
                PAYLOAD_SELECTION
            } else {
                null
            }
        }
    }

    private inner class HeaderAwareListUpdateCallback : ListUpdateCallback {
        private fun offset(position: Int): Int = position + headerOffset

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(offset(position), count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(offset(position), count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(offset(fromPosition), offset(toPosition))
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(offset(position), count, payload)
        }
    }

    private companion object {
        private const val VIEW_TYPE_ADD = 0
        private const val VIEW_TYPE_THEME = 1
        private const val ADD_CELL_ID = Long.MIN_VALUE
        private const val PAYLOAD_SELECTION = "payload_selection"
    }
}
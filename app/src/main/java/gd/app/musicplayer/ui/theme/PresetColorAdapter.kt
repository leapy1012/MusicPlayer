package gd.app.musicplayer.ui.theme

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.databinding.DialogAccentColorPickerItemBinding

class PresetColorAdapter(
    private val layoutInflater: LayoutInflater,
    private val presetColors: IntArray,
    private val callback: Callback
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callback {
        fun onPresetColorSelected(color: Int)
        fun onCustomColorRequested()
    }

    companion object {
        private const val TYPE_PRESET = 0
        private const val TYPE_CUSTOM = 1
        private const val PAYLOAD_SELECTION = "selection"
        private const val DEFAULT_ACCENT = -12467

        fun createPresetDrawable(color: Int): Drawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }

        fun createCustomDrawable(): Drawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    -324369,
                    -76262,
                    -14090732,
                    -16319503,
                    -10972674,
                    -257560,
                    -324369
                )
                gradientType = GradientDrawable.SWEEP_GRADIENT
            }
        }
    }

    private val colorIndexMap: Map<Int, Int> = presetColors.withIndex().associate { it.value to it.index }

    private var selectedColor: Int = DEFAULT_ACCENT
    private var selectedPresetIndex: Int = colorIndexMap[selectedColor] ?: -1

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = presetColors.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == customItemPosition()) TYPE_CUSTOM else TYPE_PRESET
    }

    override fun getItemId(position: Int): Long {
        return if (position == customItemPosition()) Long.MIN_VALUE else presetColors[position].toLong()
    }

    fun setSelectedColor(color: Int) {
        if (selectedColor == color) return

        val oldPresetIndex = selectedPresetIndex
        val oldCustomSelected = oldPresetIndex < 0

        selectedColor = color
        selectedPresetIndex = colorIndexMap[color] ?: -1

        val newPresetIndex = selectedPresetIndex
        val newCustomSelected = newPresetIndex < 0

        if (oldPresetIndex >= 0) notifyItemChanged(oldPresetIndex, PAYLOAD_SELECTION)
        if (newPresetIndex >= 0 && newPresetIndex != oldPresetIndex) {
            notifyItemChanged(newPresetIndex, PAYLOAD_SELECTION)
        }
        if (oldCustomSelected != newCustomSelected) {
            notifyItemChanged(customItemPosition(), PAYLOAD_SELECTION)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = DialogAccentColorPickerItemBinding.inflate(layoutInflater, parent, false)
        return if (viewType == TYPE_CUSTOM) {
            CustomColorViewHolder(binding)
        } else {
            PresetColorViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindInternal(holder, position, isSelectionPayloadOnly = false)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val isSelectionPayloadOnly = payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }
        bindInternal(holder, position, isSelectionPayloadOnly)
    }

    private fun bindInternal(
        holder: RecyclerView.ViewHolder,
        position: Int,
        isSelectionPayloadOnly: Boolean
    ) {
        when (holder) {
            is PresetColorViewHolder -> {
                val color = presetColors[position]
                val isSelected = position == selectedPresetIndex
                if (isSelectionPayloadOnly) {
                    holder.bindSelection(isSelected)
                } else {
                    holder.bind(
                        color = color,
                        isSelected = isSelected,
                        onClick = { callback.onPresetColorSelected(color) }
                    )
                }
            }

            is CustomColorViewHolder -> {
                val isSelected = selectedPresetIndex < 0
                if (isSelectionPayloadOnly) {
                    holder.bindSelection(isSelected)
                } else {
                    holder.bind(
                        isSelected = isSelected,
                        onClick = callback::onCustomColorRequested
                    )
                }
            }
        }
    }

    private fun customItemPosition(): Int = presetColors.size

    private class PresetColorViewHolder(
        private val binding: DialogAccentColorPickerItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundColor: Int? = null

        fun bind(color: Int, isSelected: Boolean, onClick: () -> Unit) {
            if (boundColor != color) {
                boundColor = color
                binding.accentColorImage.setImageDrawable(createPresetDrawable(color))
            }
            bindSelection(isSelected)
            binding.accentColorImage.setOnClickListener { onClick() }
        }

        fun bindSelection(isSelected: Boolean) {
            binding.accentColorSelect.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    private class CustomColorViewHolder(
        private val binding: DialogAccentColorPickerItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var initialized = false

        fun bind(isSelected: Boolean, onClick: () -> Unit) {
            if (!initialized) {
                initialized = true
                binding.accentColorImage.setImageDrawable(createCustomDrawable())
            }
            bindSelection(isSelected)
            binding.accentColorImage.setOnClickListener { onClick() }
        }

        fun bindSelection(isSelected: Boolean) {
            binding.accentColorSelect.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

}


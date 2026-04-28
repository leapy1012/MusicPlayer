package gd.app.musicplayer.ui.feature.equalizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.playback.EffectGroupPreset
import gd.app.musicplayer.playback.EffectGroupPresets

internal class EffectGroupAdapter(
    private val layoutInflater: LayoutInflater,
    private val headerView: View,
    private val useGridItem: Boolean,
    private val onSelectPreset: (EffectGroupPreset) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPresetId: Int = 0
    private var enabled = false

    override fun getItemCount(): Int = EffectGroupPresets.all.size + 1

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> VIEW_TYPE_HEADER
            useGridItem -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                (headerView.parent as? ViewGroup)?.removeView(headerView)
                headerView.context.appDependencies.themeEngine.apply(headerView)
                HeaderViewHolder(headerView)
            }

            VIEW_TYPE_GRID -> PresetViewHolder(
                layoutInflater.inflate(R.layout.activity_effect_group_grid_item, parent, false).also {
                    parent.context.appDependencies.themeEngine.apply(it)
                }
            )

            else -> PresetViewHolder(
                layoutInflater.inflate(R.layout.activity_effect_group_item, parent, false).also {
                    parent.context.appDependencies.themeEngine.apply(it)
                }
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is PresetViewHolder) return
        holder.bind(EffectGroupPresets.all[position - 1])
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty() || holder !is PresetViewHolder) {
            onBindViewHolder(holder, position)
            return
        }
        holder.updateState()
    }

    fun setSelection(enabled: Boolean, presetId: Int) {
        val changed = this.enabled != enabled || this.selectedPresetId != presetId
        this.enabled = enabled
        this.selectedPresetId = presetId
        if (changed) {
            notifyItemRangeChanged(1, itemCount - 1, PAYLOAD_STATE)
        }
    }

    private inner class PresetViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val icon = itemView.findViewById<AppCompatImageView>(R.id.group_effect_item_icon)
        private val name = itemView.findViewById<TextView>(R.id.group_effect_item_name)
        private val use = itemView.findViewById<TextView>(R.id.group_effect_item_use)
        private lateinit var preset: EffectGroupPreset

        init {
            use.setOnClickListener(this)
        }

        fun bind(preset: EffectGroupPreset) {
            this.preset = preset
            icon.setImageResource(preset.iconRes)
            name.setText(preset.nameRes)
            updateState()
        }

        fun updateState() {
            val inUse = enabled && selectedPresetId == preset.id
            use.isSelected = inUse
            use.text = itemView.context.getString(if (inUse) R.string.in_use else R.string.use)
        }

        override fun onClick(v: View) {
            onSelectPreset(preset)
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_LIST = 1
        const val VIEW_TYPE_GRID = 2
        const val PAYLOAD_STATE = "state"
    }
}

package gd.app.musicplayer.playback.desktop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.lib.model.lrc.view.LyricView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.view.SeekBar

class DesktopLyricPresetColorAdapter(
    recyclerView: RecyclerView,
    private val lyricView: LyricView,
    private val currentColorSeekBar: SeekBar,
    private val normalColorSeekBar: SeekBar,
    private val onPresetSelected: (PresetColor, Int) -> Unit
) {

    private val layoutManager = LinearLayoutManager(
        recyclerView.context,
        RecyclerView.HORIZONTAL,
        false
    )
    private val adapter = PresetAdapter(LayoutInflater.from(recyclerView.context))
    private var selectedIndex = -1

    init {
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)
        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter
    }

    fun render(selectedIndex: Int, applySelected: Boolean) {
        this.selectedIndex = selectedIndex
        adapter.notifyDataSetChanged()

        if (selectedIndex in presetColors.indices) {
            if (applySelected) {
                applyPreset(presetColors[selectedIndex])
            }
            layoutManager.scrollToPositionWithOffset(selectedIndex, 0)
        }
    }

    fun clearSelection() {
        selectedIndex = -1
        adapter.notifyDataSetChanged()
    }

    private fun select(index: Int) {
        if (index !in presetColors.indices) return

        selectedIndex = index
        val preset = presetColors[index]
        applyPreset(preset)
        onPresetSelected(preset, index)
        adapter.notifyDataSetChanged()
    }

    private fun applyPreset(preset: PresetColor) {
        lyricView.setNormalTextColor(preset.normalColor)
        lyricView.setCurrentTextColor(preset.currentColor)
        currentColorSeekBar.setProgress(preset.currentProgress)
        normalColorSeekBar.setProgress(preset.normalProgress)
        currentColorSeekBar.setThumbOverlayColor(preset.currentColor)
        normalColorSeekBar.setThumbOverlayColor(preset.normalColor)
    }

    private inner class PresetAdapter(
        private val inflater: LayoutInflater
    ) : RecyclerView.Adapter<PresetViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
            return PresetViewHolder(
                inflater.inflate(R.layout.layout_desk_lrc_preset_color_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
            holder.bind(presetColors[position], position)
        }

        override fun getItemCount(): Int = presetColors.size

        override fun getItemId(position: Int): Long = position.toLong()
    }

    private inner class PresetViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private val imageView: ImageView = itemView.findViewById(R.id.itemImage)
        private val checkbox: ImageView = itemView.findViewById(R.id.checkbox)
        private var index: Int = -1

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(preset: PresetColor, index: Int) {
            this.index = index
            imageView.setImageDrawable(
                DesktopLyricPresetDrawable(
                    currentColor = preset.currentColor,
                    normalColor = preset.normalColor
                )
            )
            checkbox.visibility = if (selectedIndex == index) View.VISIBLE else View.GONE
        }

        override fun onClick(view: View) {
            select(index)
        }
    }

    data class PresetColor(
        val normalProgress: Int,
        val currentProgress: Int
    ) {
        val normalColor: Int =
            DesktopLyricColorUtils.interpolate(
                DesktopLyricColorUtils.normalGradientColors,
                normalProgress / 100f
            )

        val currentColor: Int =
            DesktopLyricColorUtils.interpolate(
                DesktopLyricColorUtils.currentGradientColors,
                currentProgress / 100f
            )
    }

    companion object {
        val presetColors: List<PresetColor> = listOf(
            PresetColor(19, 28),
            PresetColor(46, 100),
            PresetColor(52, 10),
            PresetColor(87, 32),
            PresetColor(17, 13),
            PresetColor(33, 4),
            PresetColor(16, 38),
            PresetColor(42, 100),
            PresetColor(70, 100),
            PresetColor(53, 28),
            PresetColor(96, 51),
            PresetColor(32, 37),
            PresetColor(16, 100),
            PresetColor(33, 92),
            PresetColor(84, 12)
        )
    }
}

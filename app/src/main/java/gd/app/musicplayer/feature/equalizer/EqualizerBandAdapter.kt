package gd.app.musicplayer.feature.equalizer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.databinding.ItemEqualizerSeekbarBinding
import gd.app.musicplayer.ui.common.view.SeekBar

internal class EqualizerBandAdapter(
    private val layoutInflater: LayoutInflater,
    private val onBandChanged: (index: Int, levelMb: Int, fromUser: Boolean) -> Unit,
    private val onTrackingChanged: (tracking: Boolean) -> Unit
) : RecyclerView.Adapter<EqualizerBandAdapter.BandViewHolder>() {
    private companion object {
        const val PAYLOAD_LEVEL = "payload_level"
        const val PAYLOAD_ENABLED = "payload_enabled"
        const val PAYLOAD_ANIMATION = "payload_animation"
    }

    private var labels: List<String> = emptyList()
    private var levels: IntArray = intArrayOf()
    private var enabled: Boolean = true
    private var animateOnNextBind = BooleanArray(10) { true }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BandViewHolder {
        val binding = ItemEqualizerSeekbarBinding.inflate(layoutInflater, parent, false)
        parent.context.appContainer.themeEngine.apply(binding.root)
        return BandViewHolder(binding)
    }

    override fun getItemCount(): Int = labels.size

    override fun onBindViewHolder(holder: BandViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: BandViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            holder.bind(position)
            return
        }
        var levelOnly = false
        var enabledOnly = false
        var animationOnly = false
        payloads.forEach {
            if (it == PAYLOAD_LEVEL) levelOnly = true
            if (it == PAYLOAD_ENABLED) enabledOnly = true
            if (it == PAYLOAD_ANIMATION) animationOnly = true
        }
        when {
            levelOnly && !enabledOnly -> holder.bindLevel(position)
            enabledOnly && !levelOnly -> holder.bindEnabled()
            animationOnly && !levelOnly && !enabledOnly -> holder.bindLevel(position)
            else -> holder.bind(position)
        }
    }

    fun submit(labels: List<String>, levels: IntArray, enabled: Boolean) {
        val labelsChanged = this.labels != labels
        val sizeChanged = this.levels.size != levels.size
        if (labelsChanged || sizeChanged) {
            this.labels = labels
            this.levels = levels.copyOf()
            this.enabled = enabled
            animateOnNextBind = BooleanArray(levels.size.coerceAtLeast(10)) { true }
            notifyDataSetChanged()
            return
        }

        val previousEnabled = this.enabled
        this.labels = labels
        this.enabled = enabled
        for (index in levels.indices) {
            if (this.levels[index] != levels[index]) {
                this.levels[index] = levels[index]
                notifyItemChanged(index, PAYLOAD_LEVEL)
            }
        }

        if (previousEnabled != enabled) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_ENABLED)
        }
    }

    fun refreshAnimations() {
        animateOnNextBind.fill(true)
        notifyItemRangeChanged(0, itemCount, PAYLOAD_ANIMATION)
    }

    inner class BandViewHolder(
        private val binding: ItemEqualizerSeekbarBinding
    ) : RecyclerView.ViewHolder(binding.root), SeekBar.OnSeekBarChangeListener {

        init {
            binding.equalizerItemSeek.setOnSeekBarChangeListener(this)
        }

        fun bind(position: Int) {
            binding.equalizerItemText.text = labels[position]
            bindLevel(position)
            bindEnabled()
        }

        fun bindLevel(position: Int) {
            val level = levels[position]
            binding.equalizerItemSeekText.text = EqualizerPresets.formatBandValue(level)
            val progress = EqualizerPresets.levelMbToProgress(level)
            if (animateOnNextBind.getOrNull(position) == true) {
                binding.equalizerItemSeek.j(progress, true)
                animateOnNextBind[position] = false
            } else {
                binding.equalizerItemSeek.setProgress(progress)
            }
        }

        fun bindEnabled() {
            binding.equalizerItemSeek.isEnabled = enabled
            binding.equalizerItemText.isEnabled = enabled
            binding.equalizerItemSeekText.isEnabled = enabled
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val level = EqualizerPresets.progressToLevelMb(progress)
            binding.equalizerItemSeekText.text = EqualizerPresets.formatBandValue(level)
            onBandChanged(position, level, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            onTrackingChanged(true)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            onTrackingChanged(false)
        }
    }
}

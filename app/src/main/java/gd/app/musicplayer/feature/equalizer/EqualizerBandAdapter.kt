package gd.app.musicplayer.feature.equalizer

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.seekbar.COUIVerticalSeekBar
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.designsystem.view.EqualizerItemLayout
import gd.app.musicplayer.databinding.ItemEqualizerSeekbarBinding

internal class EqualizerBandAdapter(
    private val layoutInflater: LayoutInflater,
    private val onBandChanged: (index: Int, levelMb: Int, fromUser: Boolean) -> Unit,
    private val onTrackingChanged: (tracking: Boolean) -> Unit,
    private val applyTheme: (View) -> Unit
) : RecyclerView.Adapter<EqualizerBandAdapter.BandViewHolder>() {
    private companion object {
        const val PAYLOAD_LEVEL = "payload_level"
        const val PAYLOAD_ENABLED = "payload_enabled"
        const val LEVEL_ANIM_DURATION_MS = 300L
    }

    private var labels: List<String> = emptyList()
    private var levels: IntArray = intArrayOf()
    private var enabled: Boolean = true
    private var animateOnNextBind = BooleanArray(10) { true }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BandViewHolder {
        val binding = ItemEqualizerSeekbarBinding.inflate(layoutInflater, parent, false)
        applyTheme(binding.root)
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
        payloads.forEach {
            if (it == PAYLOAD_LEVEL) levelOnly = true
            if (it == PAYLOAD_ENABLED) enabledOnly = true
        }
        when {
            levelOnly && !enabledOnly -> holder.bindLevel(position)
            enabledOnly && !levelOnly -> holder.bindEnabled()
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

    fun markAnimationsPending() {
        animateOnNextBind.fill(true)
    }

    inner class BandViewHolder(
        private val binding: ItemEqualizerSeekbarBinding
    ) : RecyclerView.ViewHolder(binding.root), COUIVerticalSeekBar.OnSeekBarChangeListener {

        private var tracking = false
        private var levelAnimator: ValueAnimator? = null

        init {
            binding.equalizerItemSeek.claimSliderDrags(vertical = true)
            binding.equalizerItemSeek.setOnSeekBarChangeListener(this)
        }

        fun bind(position: Int) {
            (binding.root as? EqualizerItemLayout)?.bandCount = itemCount
            binding.equalizerItemText.text = labels[position]
            bindLevel(position)
            bindEnabled()
        }

        fun bindLevel(position: Int) {
            if (tracking) return
            val level = levels[position]
            binding.equalizerItemSeekText.text = formatBandValue(level)
            val progress = EqualizerPresets.levelMbToProgress(level)
            val animate = animateOnNextBind.getOrNull(position) == true
            if (animate) animateOnNextBind[position] = false
            if (animate && binding.equalizerItemSeek.isLaidOut) {
                animateProgressTo(progress)
            } else {
                levelAnimator?.cancel()
                binding.equalizerItemSeek.setProgress(progress)
            }
        }

        // COUIVerticalSeekBar's own setProgress(p, true) never refreshes its pixels-per-step
        // after layout and jumps to 0, so step it with plain setProgress calls instead.
        private fun animateProgressTo(target: Int) {
            val seekBar = binding.equalizerItemSeek
            levelAnimator?.cancel()
            if (seekBar.progress == target) return
            levelAnimator = ValueAnimator.ofInt(seekBar.progress, target).apply {
                duration = LEVEL_ANIM_DURATION_MS
                interpolator = PathInterpolatorCompat.create(0.3f, 0f, 0.1f, 1f)
                addUpdateListener { seekBar.setProgress(it.animatedValue as Int) }
                start()
            }
        }

        fun bindEnabled() {
            binding.equalizerItemSeek.isEnabled = enabled
            binding.equalizerItemText.isEnabled = enabled
            binding.equalizerItemSeekText.isEnabled = enabled
        }

        override fun onProgressChanged(
            seekBar: COUIVerticalSeekBar,
            progress: Int,
            fromUser: Boolean
        ) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val level = EqualizerPresets.progressToLevelMb(progress)
            binding.equalizerItemSeekText.text = formatBandValue(level)
            if (fromUser && position in levels.indices) levels[position] = level
            onBandChanged(position, level, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: COUIVerticalSeekBar) {
            levelAnimator?.cancel()
            tracking = true
            onTrackingChanged(true)
        }

        override fun onStopTrackingTouch(seekBar: COUIVerticalSeekBar) {
            tracking = false
            onTrackingChanged(false)
        }

        private fun formatBandValue(levelMb: Int): String {
            val db = levelMb / 100
            if (db == 0) return "0"

            return if (binding.root.context.isRtl()) {
                val magnitude = kotlin.math.abs(db)
                if (db > 0) {
                    "$magnitude+"
                } else {
                    "$magnitude-"
                }
            } else {
                if (db > 0) {
                    "+$db"
                } else {
                    db.toString()
                }
            }
        }
    }
}

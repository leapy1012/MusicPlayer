package gd.app.musicplayer.feature.equalizer

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.coui.appcompat.seekbar.COUIVerticalSeekBar
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.designsystem.view.EqualizerItemLayout
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.databinding.ItemEqualizerSeekbarBinding
import gd.app.musicplayer.databinding.ItemEqualizerSeekbarPicturedBinding

internal class EqualizerBandAdapter(
    private val layoutInflater: LayoutInflater,
    private val pictured: Boolean,
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
        val binding: ViewBinding = if (pictured) {
            ItemEqualizerSeekbarPicturedBinding.inflate(layoutInflater, parent, false)
        } else {
            ItemEqualizerSeekbarBinding.inflate(layoutInflater, parent, false)
        }
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
        private val binding: ViewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var tracking = false
        private var levelAnimator: ValueAnimator? = null

        private val picturedBinding = binding as? ItemEqualizerSeekbarPicturedBinding
        private val couiBinding = binding as? ItemEqualizerSeekbarBinding
        private val seekText =
            picturedBinding?.equalizerItemSeekText ?: couiBinding!!.equalizerItemSeekText
        private val freqText =
            picturedBinding?.equalizerItemText ?: couiBinding!!.equalizerItemText
        private val picturedSeek = picturedBinding?.equalizerItemSeek
        private val couiSeek = couiBinding?.equalizerItemSeek

        init {
            picturedSeek?.let { seek ->
                seek.claimSliderDrags(vertical = true)
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) = dispatchProgress(progress, fromUser)

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        levelAnimator?.cancel()
                        tracking = true
                        onTrackingChanged(true)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        tracking = false
                        onTrackingChanged(false)
                    }
                })
            }
            couiSeek?.let { seek ->
                seek.claimSliderDrags(vertical = true)
                seek.setOnSeekBarChangeListener(object : COUIVerticalSeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: COUIVerticalSeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) = dispatchProgress(progress, fromUser)

                    override fun onStartTrackingTouch(seekBar: COUIVerticalSeekBar) {
                        levelAnimator?.cancel()
                        tracking = true
                        onTrackingChanged(true)
                    }

                    override fun onStopTrackingTouch(seekBar: COUIVerticalSeekBar) {
                        tracking = false
                        onTrackingChanged(false)
                    }
                })
            }
        }

        fun bind(position: Int) {
            (binding.root as? EqualizerItemLayout)?.bandCount = itemCount
            freqText.text = labels[position]
            bindLevel(position)
            bindEnabled()
        }

        fun bindLevel(position: Int) {
            if (tracking) return
            val level = levels[position]
            seekText.text = formatBandValue(level)
            val progress = EqualizerPresets.levelMbToProgress(level)
            val animate = animateOnNextBind.getOrNull(position) == true
            if (animate) animateOnNextBind[position] = false
            picturedSeek?.let { seek ->
                seek.setProgress(progress, animate && seek.isLaidOut)
            }
            couiSeek?.let { seek ->
                if (animate && seek.isLaidOut) {
                    animateCouiProgressTo(seek, progress)
                } else {
                    levelAnimator?.cancel()
                    seek.setProgress(progress)
                }
            }
        }

        private fun animateCouiProgressTo(seekBar: COUIVerticalSeekBar, target: Int) {
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
            picturedSeek?.isEnabled = enabled
            couiSeek?.isEnabled = enabled
            freqText.isEnabled = enabled
            seekText.isEnabled = enabled
        }

        private fun dispatchProgress(progress: Int, fromUser: Boolean) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val level = EqualizerPresets.progressToLevelMb(progress)
            seekText.text = formatBandValue(level)
            if (fromUser && position in levels.indices) levels[position] = level
            onBandChanged(position, level, fromUser)
        }

        private fun formatBandValue(levelMb: Int): String {
            val db = levelMb / 100
            if (db == 0) return "0"
            return if (binding.root.context.isRtl()) {
                val magnitude = kotlin.math.abs(db)
                if (db > 0) "$magnitude+" else "$magnitude-"
            } else {
                if (db > 0) "+$db" else db.toString()
            }
        }
    }
}

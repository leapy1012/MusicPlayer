package gd.app.musicplayer.feature.setting.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.preference.COUIPreference
import com.coui.appcompat.seekbar.COUISeekBar
import gd.app.musicplayer.R

/**
 * Cross-fade duration row: COUI card + [COUISeekBar].
 * Commits duration only on finger-up so drag stays smooth (no ViewModel / rebind loop).
 */
class FadeSeekPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : COUIPreference(context, attrs) {

    fun interface OnFadeDurationChangedListener {
        fun onFadeDurationChanged(seconds: Int)
    }

    var onFadeDurationChanged: OnFadeDurationChangedListener? = null

    private var seekBar: COUISeekBar? = null
    private var labelView: TextView? = null
    private var suppressCallback = false
    private var dragging = false
    private var pendingSeconds: Int = 6
    private var pendingEnabled: Boolean = false

    init {
        layoutResource = R.layout.preference_fade_seek
        isSelectable = false
        isPersistent = false
    }

    fun setFadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 12)
        if (clamped == pendingSeconds && seekBar != null && !dragging) return
        pendingSeconds = clamped
        if (!dragging) {
            bindSeekUi()
        }
    }

    fun setFadeControlsEnabled(enabled: Boolean) {
        pendingEnabled = enabled
        isVisible = enabled
        seekBar?.isEnabled = enabled
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        seekBar = holder.findViewById(R.id.preference_fade_seek_bar) as? COUISeekBar
        labelView = holder.findViewById(R.id.preference_fade_seek_text) as? TextView
        seekBar?.apply {
            max = 11
            // Direct finger tracking — spring follow-hand feels laggy on a 12-step bar.
            setMoveType(0)
            setPhysicalEnabled(false)
            setSupportDeformation(false)
        }
        seekBar?.setOnSeekBarChangeListener(object : COUISeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: COUISeekBar, progress: Int, fromUser: Boolean) {
                val seconds = progress + 1
                labelView?.text = formatSeconds(seconds)
                if (fromUser && !suppressCallback) {
                    pendingSeconds = seconds
                }
            }

            override fun onStartTrackingTouch(seekBar: COUISeekBar) {
                dragging = true
            }

            override fun onStopTrackingTouch(seekBar: COUISeekBar) {
                dragging = false
                if (!suppressCallback) {
                    onFadeDurationChanged?.onFadeDurationChanged(pendingSeconds)
                }
            }
        })
        bindSeekUi()
        seekBar?.isEnabled = pendingEnabled
        COUICardListHelper.setItemCardBackground(
            holder.itemView,
            COUICardListHelper.getPositionInGroup(this)
        )
    }

    private fun bindSeekUi() {
        val bar = seekBar ?: return
        suppressCallback = true
        bar.progress = pendingSeconds - 1
        suppressCallback = false
        labelView?.text = formatSeconds(pendingSeconds)
    }

    private fun formatSeconds(seconds: Int): String {
        return "$seconds${context.getString(R.string.seconds)}"
    }
}

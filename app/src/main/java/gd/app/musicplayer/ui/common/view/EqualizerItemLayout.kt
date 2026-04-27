package gd.app.musicplayer.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import gd.app.musicplayer.playback.SoundEffectPreferences

class EqualizerItemLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalAvailableWidth = View.MeasureSpec.getSize(widthMeasureSpec)

        val itemWidth = if (
            SoundEffectPreferences.getEqualizerBandMode(context) == SoundEffectPreferences.TEN_BAND_MODE
        ) {
            totalAvailableWidth / 10
        } else {
            totalAvailableWidth / 5
        }

        val exactItemWidthSpec = View.MeasureSpec.makeMeasureSpec(
            itemWidth,
            View.MeasureSpec.EXACTLY
        )

        super.onMeasure(exactItemWidthSpec, heightMeasureSpec)
    }
}

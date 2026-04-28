package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import gd.app.musicplayer.playback.SoundEffectPreferences

class EqualizerItemLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalAvailableWidth = MeasureSpec.getSize(widthMeasureSpec)

        val itemWidth = if (
            SoundEffectPreferences.getEqualizerBandMode(context) == SoundEffectPreferences.TEN_BAND_MODE
        ) {
            totalAvailableWidth / 10
        } else {
            totalAvailableWidth / 5
        }

        val exactItemWidthSpec = MeasureSpec.makeMeasureSpec(
            itemWidth,
            MeasureSpec.EXACTLY
        )

        super.onMeasure(exactItemWidthSpec, heightMeasureSpec)
    }
}

package gd.app.musicplayer.ui.common

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback

/** Row press feedback: the COUI mask in the light theme, a plain ripple on dark/image themes. */
fun View.applyListRowFeedback(lightTheme: Boolean) {
    if (lightTheme) {
        installCouiPressFeedback()
        return
    }
    val value = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
    setBackgroundResource(value.resourceId)
}

/** Light-theme lists sit on the COUI card colour; other themes keep the window background. */
fun RecyclerView.applyCouiListBackground(lightTheme: Boolean) {
    if (!lightTheme) return
    val typed = context.obtainStyledAttributes(
        intArrayOf(com.coui.appcompat.R.attr.couiColorCardBackground)
    )
    setBackgroundColor(typed.getColor(0, Color.WHITE))
    typed.recycle()
}
